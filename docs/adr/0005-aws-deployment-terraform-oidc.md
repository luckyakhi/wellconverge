# ADR-0005: AWS deployment via Terraform, ECS Fargate, and role-based (OIDC) access

- **Status:** Accepted
- **Date:** 2026-08-17

## Context

`docs/architecture.md` §5 always planned a move from `docker compose` to a real cloud target ("AWS
Fargate/EKS via Terraform"). We picked this as the next iteration instead of a local Kubernetes cluster
(`kind`) because the development machine has no usable container runtime — Docker Desktop requires
macOS Sonoma+ (this machine runs macOS 12), and Colima's build failed compiling its `lima` dependency
from source. `kind` needs a container runtime to run cluster nodes; without one, a "local Kubernetes"
iteration couldn't be exercised end to end. Testing deployment artifacts directly on AWS sidesteps that
gap entirely, and is where this project was headed eventually regardless.

Two further problems needed solving:
1. **No local Docker to build the container image.** The backend `Dockerfile`/frontend `Dockerfile`
   need something to run `docker build`.
2. **How does anything (a human's laptop, a CI job) authenticate to AWS?** Long-lived IAM access keys
   are easy to leak (e.g. pasted into a chat transcript, committed by accident) and don't expire on
   their own.

## Decision

**Compute target: ECS Fargate**, not EKS. Fargate needs no cluster nodes to manage and is materially
cheaper for a learning project (no ~$73/mo EKS control-plane charge). Kubernetes concepts can still be
revisited later as a separate, explicit iteration if desired — this decision doesn't foreclose that.

**Image build: GitHub Actions**, not AWS CodeBuild. The repo already has a GitHub remote
(`github.com/luckyakhi/wellconverge`); GitHub-hosted runners have Docker preinstalled, which solves the
"no local Docker" problem without adding AWS-side build infrastructure to maintain.

**Authentication: roles, not static keys, everywhere it's possible.**

- **GitHub Actions → AWS:** OIDC federation. AWS trusts GitHub's own OIDC token issuer
  (`token.actions.githubusercontent.com`) directly; the trust policy on `wellconverge-github-actions-deploy`
  is scoped to this repo. **No AWS credentials are stored in GitHub Secrets.** Tokens are minted
  per-workflow-run and expire automatically.
- **Local machine (Terraform runs) → AWS:** a human still has to authenticate the *first* API call —
  there's no OIDC-equivalent for a laptop. We minimize the blast radius instead: a narrow **bootstrap
  IAM user** (`wellconverge-bootstrap`) can do nothing but create the OIDC provider and two IAM roles.
  All real infrastructure work happens through **`wellconverge-terraform-deployer`**, assumed via STS
  from the bootstrap identity. Session credentials from an assumed role expire in ≤1 hour; the bootstrap
  user's own long-lived key can be deactivated once the bootstrap stack is applied, since nothing depends
  on it afterward.

**State:** Terraform state for `deploy/terraform/bootstrap/` is local (chicken-and-egg — there's no S3
bucket yet to hold it). Once `deploy/terraform/main/` provisions an S3 bucket + DynamoDB lock table, that
stack's own state can migrate to it; the bootstrap stack's state is small and rarely touched, so it can
stay local intentionally.

**Database: RDS Postgres**, not a containerized Postgres on Fargate. Matches the existing Flyway/Postgres
stack, and a container running Postgres inside a Fargate task has no persistent volume story worth using
in production.

## Consequences

- Nobody needs Docker, `kind`, or `kubectl` locally to develop or deploy this project going forward —
  only to build and push, which GitHub Actions now owns.
- Two Terraform stacks (`bootstrap/`, `main/`) instead of one. The split exists solely to solve the
  bootstrapping problem cleanly; `main/` is the one that changes as the app evolves.
- The bootstrap IAM user is a one-time-use credential by design. If it's ever regenerated, that's a sign
  the bootstrap stack needs re-applying, not that this pattern failed.
- Fargate costs more per vCPU/hour than a self-managed EC2 fleet, but there is no fleet to patch or size.
  Acceptable trade for a learning project prioritizing "close to production shape" over minimum cost.
