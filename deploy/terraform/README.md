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

## Three stacks, and why

| Stack        | Creates                                     | Applied by       | How often |
|--------------|---------------------------------------------|------------------|-----------|
| `bootstrap/` | GitHub OIDC provider + 2 IAM roles          | bootstrap user   | **Once**, ever |
| `state/`     | S3 state bucket + DynamoDB lock table       | deployer role    | **Once**, ever |
| `main/`      | All the actual infrastructure above         | deployer role    | Every infra change |

`bootstrap/` solves a chicken-and-egg problem: `main/` should be applied by a narrowly-scoped role,
but something has to *create* that role first. It's applied by the `wellconverge-bootstrap` IAM
user, whose permissions extend to almost nothing else.

`state/` is separate from `main/` because a stack that managed the bucket holding its own state
would try to delete that bucket during `terraform destroy`. It isn't in `bootstrap/` either, since
the bootstrap user has no S3 or DynamoDB permissions — only the deployer role does.

**`main/` keeps its state in S3**, encrypted and versioned, with DynamoDB locking so two applies
can't corrupt it. `bootstrap/` and `state/` keep local, gitignored state: each holds a handful of
trivially re-importable resources and no secrets. `main/` is the one that tracks 28 resources, so
it's the one that needed a real backend.

> **State is never committed to git.** `.gitignore` excludes `*.tfstate`, and it should stay that
> way — git has no locking, so two people applying concurrently produces a state conflict that is
> effectively unresolvable by hand. S3 + DynamoDB is the mechanism that actually solves this.

## Where the database password lives

Nowhere in Terraform. `manage_master_user_password = true` on the RDS instance hands password
generation to AWS: RDS creates the master password, writes it into a Secrets Manager secret it owns,
and Terraform only ever sees that secret's **ARN**.

This matters more than it looks. Terraform writes the *resolved value* of every attribute into
state, so the previous design — a `random_password` fed into both the DB instance and a
`aws_secretsmanager_secret_version` — put the live password into `terraform.tfstate` in plaintext,
in three separate attributes. No amount of `sensitive = true` or `TF_VAR_` indirection changes that;
`sensitive` only redacts CLI *output*. Letting RDS own the password is what actually keeps it out of
state, and therefore what makes a shared remote backend safe.

The ECS task reads that secret at container start via its execution role
(`master_user_secret[0].secret_arn`), so nothing needs the plaintext value at deploy time.

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

## Step 2 — State backend (one time only)

Skip if the bucket already exists. Runs as the **deployer role**, which `bootstrap/` grants scoped
S3 and DynamoDB access for exactly these two resources.

```bash
cd deploy/terraform/state
export AWS_PROFILE=wellconverge-deployer

terraform init
terraform apply
```

Creates the versioned, encrypted, public-access-blocked S3 bucket that holds `main/`'s state, plus
the DynamoDB lock table. The bucket carries `prevent_destroy = true` — losing it means losing track
of every resource in `main/`.

Then migrate `main/` onto that backend. **Do these in order and check each one** — the local state
file is the only copy of your infrastructure's identity until step 4 confirms it's in S3.

**1. Uncomment the `terraform` block** in [`main/backend.tf`](main/backend.tf). Verify it actually
took effect, rather than assuming:

```bash
cd ../main
grep -c 'backend "s3"' backend.tf      # must print 1, not 0
```

If that prints `0`, stop — the block is still commented and the next step will silently do nothing.

**2. Back up the local state before touching it:**

```bash
cp terraform.tfstate ~/wellconverge-tfstate-backup.json
```

**3. Migrate:**

```bash
terraform init -migrate-state          # answer "yes" to copy local state into S3
```

**4. Confirm the state actually landed in S3 — this is the gate:**

```bash
aws s3 ls s3://wellconverge-tfstate-273505519511/main/ --region ap-south-1
terraform state list | wc -l           # must print 29 (28 resources + 1 data source)
```

**5. Only once step 4 shows a non-empty bucket and the right resource count**, remove the now-stale
local files:

```bash
rm -f terraform.tfstate terraform.tfstate.backup
```

> ⚠️ **Never run step 5 before step 4 passes.** If the backend block was still commented,
> `init -migrate-state` is a no-op, the bucket stays empty, and deleting the local state orphans
> every resource in the stack. The next `apply` then tries to build a second copy of everything and
> fails partway through on the first name collision — leaving you with duplicated VPCs, subnets and
> security groups to clean up by hand. Step 4 exists specifically to make that impossible.

## Step 3 — Main infrastructure stack

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
to pull, and retry. **This is expected between Step 3 and Step 4.** The ALB will return `503` and
the service's events will show `CannotPullContainerError`. Step 4 fixes it.

## Step 4 — Build and push the backend image

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

## Step 5 — Verify

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

Things `destroy` will not remove on its own:
- **ECR images** — a repository with images in it blocks deletion. Either
  `aws ecr batch-delete-image` first, or add `force_delete = true` to the repo resource.
- **The RDS-managed secret** — AWS schedules it for deletion with a recovery window (7–30 days)
  rather than deleting immediately.
- **The state bucket and lock table** — `state/` is a separate stack, and the bucket carries
  `prevent_destroy = true`. That's deliberate: tearing down `main/` should never be able to take its
  own state with it.

Order matters if you're decommissioning entirely: destroy `main/` **first**, while its state still
exists, then `state/` (you'll have to remove the `prevent_destroy` lifecycle block and empty the
bucket, since versioned buckets refuse deletion while objects remain).

Leave `bootstrap/` alone unless you're done with the project — destroying it removes the role your
CI authenticates with, and the role you'd need to clean anything else up.

## Migrating an existing deployment

Only relevant if you deployed before the remote-state / managed-password change. Both migrations are
one-time.

**Password → RDS-managed.** Enabling `manage_master_user_password` on a *live* instance is
inherently two-phase: the Secrets Manager secret doesn't exist until the change is applied, so the
same plan can't also wire references to it. Terraform fails with `Invalid index ... empty list of
object` if you try it in one shot.

```bash
cd deploy/terraform/main
export AWS_PROFILE=wellconverge-deployer

terraform apply -target=aws_db_instance.main   # phase 1: RDS creates + stores the password
terraform apply                                # phase 2: wire ECS/IAM to it, drop the old secret
```

Phase 1 **changes the master password** — AWS generates a new one and the old Secrets Manager value
goes stale immediately. Between the two phases the running task has invalid credentials, so do this
when the service is idle (during initial setup, before an image is ever pushed, is ideal). A fresh
`main/` deployment onto an empty account needs no targeting; this only affects in-place upgrades.

Phase 2 destroys the old self-managed secret. Secrets Manager schedules deletion with a recovery
window rather than deleting immediately, so the name `wellconverge/db-credentials` stays reserved
for 7–30 days.

**State → S3.** See Step 2.

## Troubleshooting

**`Error: creating ... AccessDenied`** — you're running as the bootstrap user instead of the
deployer role. `export AWS_PROFILE=wellconverge-deployer`. If it persists, the action genuinely
isn't in the deployer policy; add it to
[`bootstrap/terraform_deployer_role.tf`](bootstrap/terraform_deployer_role.tf) and re-apply the
bootstrap stack.

**`was unable to place a task. Reason: CannotPullContainerError ... :latest: not found`** — no image
in ECR yet. This is the exact event a fresh `main/` apply produces. Run Step 4.

**Target group health checks fail, tasks cycle** — the check hits `/actuator/health`. Confirm the
container actually reached that point by reading `/ecs/wellconverge-backend` in CloudWatch (console,
or a role with `logs:FilterLogEvents` — not the deployer profile, see Step 5). A Flyway migration
failing against RDS is the usual cause, and it surfaces there.

**`InvalidParameterException: subnets can only be specified in 2 AZs`** — the region has fewer than
two usable AZs for the chosen instance type. Set `availability_zones` explicitly in
`main/variables.tf`.

**`Invalid count argument ... count value depends on resource attributes`** during import or a
plan from empty state — a `count` derived from another resource can't be resolved before that
resource is in state. Derive the count from a variable instead (this is why
`aws_route_table_association.public` counts `var.public_subnet_cidrs`, not `aws_subnet.public`).

**`Inconsistent dependency lock file ... no version is selected`** — the config was swapped to a
shape needing a provider the lock file no longer pins (e.g. reverting to the `random_password`
config after the managed-password change dropped `hashicorp/random`). Run
`terraform init -upgrade`. `recover-import.sh` does this automatically around each config swap.

**`secretsmanager:ListSecretVersionIds` AccessDenied** — the deployer role can describe a secret
but not enumerate its versions. That's why `aws_secretsmanager_secret_version` is never imported;
it isn't needed, since destroying the secret removes its versions.

**`DBSubnetGroupAlreadyExists` / `...AlreadyExists` partway through an apply** — Terraform is
working from empty state and trying to build a second copy of the stack. It gets as far as the first
resource whose name must be globally unique, having already created the ones that don't care
(VPC, subnets, security groups — so you now have duplicates of those).

Do **not** re-run `apply`; it will fail the same way. Check what you actually have:

```bash
aws ec2 describe-vpcs --filters Name=tag:Project,Values=wellconverge \
  --region ap-south-1 --query 'Vpcs[].{id:VpcId,cidr:CidrBlock}' --output table
terraform state list          # what Terraform thinks exists
```

Two VPCs with the same CIDR confirms it. Recovery: `terraform destroy` (which, with the truncated
state, removes only the duplicates — verify with `terraform plan -destroy` first), then re-adopt the
originals with `main/recover-import.sh`. The duplicated resources are all free, so there's no cost
pressure to rush.

**`terraform plan` wants to create everything that already exists** — `main/` isn't pointed at the
S3 backend. Check that the block in `main/backend.tf` is uncommented and that `terraform init` ran.
Don't apply from this position: it would collide on names that are already taken.

**`Error: Invalid index ... master_user_secret is empty list of object`** — you're enabling
`manage_master_user_password` on an RDS instance that doesn't have it yet. The secret doesn't exist
until that change is applied, so references to it can't resolve in the same plan. Do the targeted
two-phase apply in "Migrating an existing deployment" below.
