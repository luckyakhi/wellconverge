# Deploying WellConverge to AWS

This directory provisions the AWS environment for WellConverge: a Spring Boot backend on **ECS
Fargate**, behind an **Application Load Balancer**, talking to **RDS Postgres**, with the container
image in **ECR**.

The *why* behind this shape — Fargate over EKS, GitHub Actions over CodeBuild, assumed roles over
static keys — is recorded in [`docs/adr/0005-aws-deployment-terraform-oidc.md`](../../docs/adr/0005-aws-deployment-terraform-oidc.md).
Read that first if you want the reasoning; this file is the runbook.

## What gets created

```
Internet
   │  :80
   ▼
┌──────────────────────── VPC 10.20.0.0/16 ────────────────────────┐
│                                                                   │
│   ALB (public subnets, 2 AZs)  ──forward──▶  target group :8080   │
│      ▲ sg: 0.0.0.0/0 → :80                        │               │
│                                                   ▼               │
│   ECS Fargate service "wellconverge-backend"  (public IP,         │
│      sg: inbound ONLY from the ALB's security group)              │
│                                                   │               │
│                                                   ▼ :5432         │
│   RDS Postgres 16  (publicly_accessible = false,                  │
│      sg: inbound ONLY from the ECS service's security group)      │
└───────────────────────────────────────────────────────────────────┘

Alongside: ECR repo, CloudWatch log group /ecs/wellconverge-backend,
Secrets Manager secret wellconverge/db-credentials, 2 IAM roles.
```

**Public-subnet-only by design.** Fargate tasks get public IPs instead of sitting behind a NAT
Gateway, which avoids its ~$32–35/month fixed cost. They're still not reachable from the internet:
the task security group only accepts traffic from the ALB's security group. Revisit if this ever
needs a private-subnet posture.

## Two stacks, and why

| Stack        | Creates                                          | How often it runs |
|--------------|--------------------------------------------------|-------------------|
| `bootstrap/` | GitHub OIDC provider + 2 IAM roles                | **Once**, ever    |
| `main/`      | All the actual infrastructure above               | Every infra change |

The split solves a chicken-and-egg problem: `main/` should be applied by a narrowly-scoped role, but
something has to *create* that role first. `bootstrap/` is applied by the `wellconverge-bootstrap`
IAM user, whose permissions extend to almost nothing else.

Both stacks use **local state** (`terraform.tfstate` committed beside the config). There's no S3
backend yet — that's the follow-up noted in ADR-0005. Consequence: whoever runs `terraform apply`
needs the current state file, and two people must not apply concurrently.

## Prerequisites

- **Terraform ≥ 1.9** — `brew install terraform`
- **AWS CLI v2** — `brew install awscli`
- Credentials for the `wellconverge-bootstrap` IAM user in `~/.aws/credentials` under `[default]`
- **No Docker needed.** Image builds happen on GitHub Actions runners (ADR-0005).

### One-time AWS profile setup

`~/.aws/credentials` — the bootstrap user's key:

```ini
[default]
aws_access_key_id = AKIA...
aws_secret_access_key = ...
```

`~/.aws/config` — the deployer role that actually does the work:

```ini
[default]
region = ap-south-1
output = json

[profile wellconverge-deployer]
role_arn = arn:aws:iam::273505519511:role/wellconverge-terraform-deployer
source_profile = default
region = ap-south-1
output = json
```

Verify both identities resolve before going further:

```bash
aws sts get-caller-identity                              # → user/wellconverge-bootstrap
aws sts get-caller-identity --profile wellconverge-deployer  # → assumed-role/wellconverge-terraform-deployer
```

If the second command fails, the bootstrap stack hasn't been applied yet — do Step 1.

## Step 1 — Bootstrap stack (one time only)

Skip this if `aws sts get-caller-identity --profile wellconverge-deployer` already works.

Runs as the **bootstrap user** (`[default]`), because the deployer role it creates doesn't exist yet.

```bash
cd deploy/terraform/bootstrap
terraform init
terraform plan
terraform apply
```

Creates:
- `wellconverge-terraform-deployer` — assumed from your laptop to run the `main/` stack
- `wellconverge-github-actions-deploy` — assumed by CI via OIDC to push images (no AWS keys in
  GitHub Secrets)
- the GitHub OIDC provider itself, with the thumbprint fetched live at apply time

Note the outputs; `github_actions_deploy_role_arn` is what `.github/workflows/deploy.yml` uses.

Once this has applied, the bootstrap user's access key can be deactivated until the next time this
stack changes — nothing else depends on it.

## Step 2 — Main infrastructure stack

Runs as the **deployer role**, not the bootstrap user:

```bash
cd deploy/terraform/main
export AWS_PROFILE=wellconverge-deployer

terraform init
terraform plan -out=main.tfplan     # review: should be ~28 to add on a fresh account
terraform apply main.tfplan
```

Takes roughly **8–12 minutes**; RDS is the slow part. The ALB and ECS service come up much faster
and then wait on the database address.

Capture the outputs:

```bash
terraform output
```

| Output | What it's for |
|---|---|
| `alb_dns_name` | The public entry point — `http://<dns>/actuator/health` |
| `ecr_backend_repository_url` | Where Step 3 pushes the image |
| `ecs_cluster_name` / `ecs_service_name` | For `aws ecs` CLI calls and the CI workflow |
| `db_endpoint` | RDS address (not publicly reachable) |
| `db_secret_arn` | Secrets Manager entry the task execution role reads at container start |

### What Terraform does *not* do

It does **not** put an image in ECR. The task definition points at
`<ecr-repo-url>:latest`, which doesn't exist on a fresh repo — so ECS will create the service, fail
to pull, and retry. **This is expected between Step 2 and Step 3.** The ALB will return `503` and
the service's events will show `CannotPullContainerError`. Step 3 fixes it.

## Step 3 — Build and push the backend image

Image builds run on **GitHub Actions**, not locally — this machine (macOS 12) can't run Docker
Desktop, which is precisely why ADR-0005 put the build in CI.

[`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml) authenticates via OIDC, builds
`backend/Dockerfile`, pushes `:latest` and `:<git-sha>` to ECR, then forces a new ECS deployment.

Trigger it either way:

```bash
# Manually, right now
gh workflow run deploy.yml

# Or just push a backend change to main — the workflow watches backend/**
git push origin main
```

Watch it: `gh run watch`.

<details>
<summary>If you <em>do</em> have a working Docker locally</summary>

```bash
cd deploy/terraform/main
export AWS_PROFILE=wellconverge-deployer
REGISTRY=$(terraform output -raw ecr_backend_repository_url)

aws ecr get-login-password --region ap-south-1 \
  | docker login --username AWS --password-stdin "${REGISTRY%%/*}"

docker build -t "$REGISTRY:latest" ../../../backend
docker push "$REGISTRY:latest"

aws ecs update-service --cluster wellconverge-cluster \
  --service wellconverge-backend --force-new-deployment --region ap-south-1
```
</details>

## Step 4 — Verify

```bash
cd deploy/terraform/main
export AWS_PROFILE=wellconverge-deployer
ALB=$(terraform output -raw alb_dns_name)

# Health — expect {"status":"UP"} once an image is deployed
curl "http://$ALB/actuator/health"

# The actual slice: register a member
curl -X POST "http://$ALB/api/members" \
  -H 'Content-Type: application/json' \
  -d '{"email":"ada@example.com","fullName":"Ada Lovelace"}'
```

Service health — this is the one diagnostic the deployer role can read:

```bash
aws ecs describe-services --cluster wellconverge-cluster \
  --services wellconverge-backend --region ap-south-1 \
  --query 'services[0].{running:runningCount,desired:desiredCount,events:events[:5].message}'
```

Service *events* are where deployment failures surface (image pull errors, health-check flapping),
so this is usually enough to diagnose a bad rollout.

> **The deployer role cannot read logs or tasks.** `wellconverge-terraform-deployer` is scoped to
> *provisioning*, not operations — it has no `logs:DescribeLogStreams`, `logs:FilterLogEvents`,
> `ecs:ListTasks`, `ecs:DescribeTasks`, or `ecr:ListImages`. Those commands return
> `AccessDeniedException` under this profile. Use the AWS console, or assume a broader role, for
> `aws logs tail /ecs/wellconverge-backend` and per-task inspection. This is a deliberate scoping
> choice, not an oversight — widen
> [`bootstrap/terraform_deployer_role.tf`](bootstrap/terraform_deployer_role.tf) and re-apply the
> bootstrap stack if you'd rather debug from the CLI with this profile.

## Cost

Roughly **$35–40/month** if left running continuously:

| Resource | Approx/month |
|---|---|
| ALB | ~$16 (hourly charge dominates at low traffic) |
| RDS `db.t4g.micro` + 20 GB gp3 | ~$12–15 |
| Fargate 0.25 vCPU / 0.5 GB, 1 task | ~$9 |
| ECR, Secrets Manager, CloudWatch | ~$1 |

No NAT Gateway, deliberately — that alone would add ~$32–35.

To pause spend without destroying anything, scale the service to zero (the ALB and RDS still bill):

```bash
aws ecs update-service --cluster wellconverge-cluster \
  --service wellconverge-backend --desired-count 0 --region ap-south-1
```

## Teardown

```bash
cd deploy/terraform/main
export AWS_PROFILE=wellconverge-deployer
terraform destroy
```

`skip_final_snapshot = true` and `deletion_protection = false` are set on the RDS instance, so
**destroy discards the database with no snapshot**. That's the right default for a learning project
and the wrong one for anything real — change both before this holds data you care about.

Two things `destroy` will not remove on its own:
- **ECR images** — a repository with images in it blocks deletion. Either
  `aws ecr batch-delete-image` first, or add `force_delete = true` to the repo resource.
- **The Secrets Manager secret** — AWS schedules it for deletion with a recovery window (7–30 days)
  rather than deleting immediately. Re-applying within that window fails on a name collision; use
  `aws secretsmanager delete-secret --force-delete-without-recovery` if you need the name back now.

Leave `bootstrap/` alone unless you're decommissioning the project entirely — destroying it removes
the role your CI authenticates with.

## Troubleshooting

**`Error: creating ... AccessDenied`** — you're running as the bootstrap user instead of the
deployer role. `export AWS_PROFILE=wellconverge-deployer`. If it persists, the action genuinely
isn't in the deployer policy; add it to
[`bootstrap/terraform_deployer_role.tf`](bootstrap/terraform_deployer_role.tf) and re-apply the
bootstrap stack.

**`was unable to place a task. Reason: CannotPullContainerError ... :latest: not found`** — no image
in ECR yet. This is the exact event a fresh `main/` apply produces. Run Step 3.

**Target group health checks fail, tasks cycle** — the check hits `/actuator/health`. Confirm the
container actually reached that point by reading `/ecs/wellconverge-backend` in CloudWatch (console,
or a role with `logs:FilterLogEvents` — not the deployer profile, see Step 4). A Flyway migration
failing against RDS is the usual cause, and it surfaces there.

**`InvalidParameterException: subnets can only be specified in 2 AZs`** — the region has fewer than
two usable AZs for the chosen instance type. Set `availability_zones` explicitly in
`main/variables.tf`.

**Terraform state conflicts** — state is local and committed. Pull before applying, commit the
updated state after, and don't apply from two machines at once.
