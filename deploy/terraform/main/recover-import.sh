#!/usr/bin/env bash
#
# One-time recovery: adopt the orphaned wellconverge infrastructure back into Terraform state.
#
# Context: the local state file was deleted before `terraform init -migrate-state` had actually
# migrated anything (the backend block was still commented, so init was a no-op). A subsequent
# apply then began building a second copy of the stack from an empty state and failed on the first
# name collision. This script re-imports the ORIGINAL resources so Terraform manages them again.
#
# Run this only AFTER phases 1 and 2 of the runbook (destroy the duplicates, point main/ at S3).
# It imports against the pre-migration config shape, then restores the managed-password changes.
#
# Safe to re-run: `terraform import` on an already-imported address fails loudly without changing
# anything, so a partial run can be resumed.

set -euo pipefail

export AWS_PROFILE="${AWS_PROFILE:-wellconverge-deployer}"
REGION=ap-south-1
REPO_ROOT="$(git rev-parse --show-toplevel)"
PATCH=/tmp/wellconverge-managed-password.patch

CHANGED_FILES=(
  deploy/terraform/main/rds.tf
  deploy/terraform/main/secrets.tf
  deploy/terraform/main/ecs.tf
  deploy/terraform/main/outputs.tf
  deploy/terraform/main/versions.tf
)

echo "==> Pre-flight checks"
grep -q 'backend "s3"' backend.tf && ! grep -qE '^\s*#\s*backend "s3"' backend.tf \
  || { echo "FAIL: main/backend.tf is still commented. Do phase 2 first."; exit 1; }

# `terraform state list` exits 1 on an EMPTY state, which is exactly the state we require here --
# so it must not trip `set -e` / pipefail.
# The hazard this guards against is importing on top of state that still tracks the DUPLICATE
# stack -- not a non-empty state as such, since a resumed run legitimately finds its own earlier
# imports. So check the identity of what's tracked, and let idempotent imports handle the rest.
EXPECTED_VPC=vpc-040c515ca85674bb9
CUR_VPC=$( { terraform state show aws_vpc.main 2>/dev/null || true; } \
  | awk '/^ *id *=/ { gsub(/"/, "", $3); print $3; exit }')
if [ -n "$CUR_VPC" ] && [ "$CUR_VPC" != "$EXPECTED_VPC" ]; then
  echo "FAIL: state tracks $CUR_VPC, not the original $EXPECTED_VPC. Destroy the duplicates first."
  exit 1
fi
COUNT=$( { terraform state list 2>/dev/null || true; } | wc -l | tr -d ' ')
echo "  state currently tracks $COUNT address(es); vpc=${CUR_VPC:-<none>}"

echo "==> Saving the managed-password changes, then reverting config to the deployed shape"
# Imports must run against a config that matches what is actually deployed. The managed-password
# change cannot be in place yet: it references master_user_secret[0], which does not exist until
# that change is applied, and an unresolvable reference fails the whole plan.
cd "$REPO_ROOT"
git diff -- "${CHANGED_FILES[@]}" > "$PATCH"
git checkout -- "${CHANGED_FILES[@]}"
cd "$REPO_ROOT/deploy/terraform/main"
trap 'echo "!! Restoring config"; (cd "$REPO_ROOT" && git apply "$PATCH"); terraform init -upgrade -input=false >/dev/null 2>&1 || true' EXIT

# The reverted config needs the `random` provider again (random_password), which the
# managed-password config dropped from the lock file. Re-resolve providers before importing.
echo "==> Re-resolving providers for the reverted config"
terraform init -upgrade -input=false >/dev/null

echo "==> Importing 26 resources"
imp() {
  if { terraform state list 2>/dev/null || true; } | grep -qxF "$1"; then
    echo "  -- $1 (already in state, skipping)"
    return 0
  fi
  echo "  -> $1"
  terraform import -input=false "$1" "$2" >/dev/null
}

# Networking
imp 'aws_vpc.main'                            'vpc-040c515ca85674bb9'
imp 'aws_subnet.public[0]'                    'subnet-07fe3a6bf9677abcd'   # 10.20.1.0/24 ap-south-1a
imp 'aws_subnet.public[1]'                    'subnet-0a07103e538769d3b'   # 10.20.2.0/24 ap-south-1b
imp 'aws_internet_gateway.main'               'igw-05d0853b033a4c5e1'
imp 'aws_route_table.public'                  'rtb-0a9a2c15992290d38'
imp 'aws_route_table_association.public[0]'   'subnet-07fe3a6bf9677abcd/rtb-0a9a2c15992290d38'
imp 'aws_route_table_association.public[1]'   'subnet-0a07103e538769d3b/rtb-0a9a2c15992290d38'

# Security groups
imp 'aws_security_group.alb'                  'sg-036fd9fe997205c1b'
imp 'aws_security_group.ecs_service'          'sg-0e764257ef185d85f'
imp 'aws_security_group.rds'                  'sg-04ede78ba00916f02'

# Load balancing
imp 'aws_lb.main'              'arn:aws:elasticloadbalancing:ap-south-1:273505519511:loadbalancer/app/wellconverge-alb/e494af60e6fef776'
imp 'aws_lb_target_group.backend' 'arn:aws:elasticloadbalancing:ap-south-1:273505519511:targetgroup/wellconverge-backend-tg/8129547dabd2553f'
imp 'aws_lb_listener.http'     'arn:aws:elasticloadbalancing:ap-south-1:273505519511:listener/app/wellconverge-alb/e494af60e6fef776/e59e4579665d90e8'

# Container registry
imp 'aws_ecr_repository.backend'        'wellconverge-backend'
imp 'aws_ecr_lifecycle_policy.backend'  'wellconverge-backend'

# ECS
imp 'aws_ecs_cluster.main'          'wellconverge-cluster'
imp 'aws_ecs_service.backend'       'wellconverge-cluster/wellconverge-backend'
imp 'aws_ecs_task_definition.backend' 'arn:aws:ecs:ap-south-1:273505519511:task-definition/wellconverge-backend:2'
imp 'aws_cloudwatch_log_group.backend' '/ecs/wellconverge-backend'

# Database
imp 'aws_db_subnet_group.main' 'wellconverge-db-subnet-group'
imp 'aws_db_instance.main'     'wellconverge-db'

# Secrets (self-managed; destroyed again in phase 4 once RDS owns the password).
#
# Only the secret itself is imported, not aws_secretsmanager_secret_version. Importing a version
# needs its VersionId via secretsmanager:ListSecretVersionIds, which the deployer role does not
# have -- and it would be pointless: the final config declares neither resource, and destroying
# the secret takes its versions with it.
SECRET_ARN='arn:aws:secretsmanager:ap-south-1:273505519511:secret:wellconverge/db-credentials-MtAMwT'
imp 'aws_secretsmanager_secret.db_credentials' "$SECRET_ARN"

# IAM
imp 'aws_iam_role.ecs_task'           'wellconverge-ecs-task'
imp 'aws_iam_role.ecs_task_execution' 'wellconverge-ecs-task-execution'
imp 'aws_iam_role_policy.ecs_task_execution_secrets' \
    'wellconverge-ecs-task-execution:wellconverge-ecs-task-execution-secrets'
imp 'aws_iam_role_policy_attachment.ecs_task_execution_managed' \
    'wellconverge-ecs-task-execution/arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy'

echo
echo "==> Imported $(terraform state list | wc -l | tr -d ' ') addresses"
echo "==> Restoring the managed-password config"
trap - EXIT
cd "$REPO_ROOT" && git apply "$PATCH"
cd "$REPO_ROOT/deploy/terraform/main"
echo "==> Re-resolving providers for the restored config"
terraform init -upgrade -input=false >/dev/null

cat <<'NEXT'

Done importing. random_password.db is intentionally NOT imported -- generated values cannot be
imported, and the managed-password migration removes that resource anyway.

Next (phase 4), from deploy/terraform/main:

  terraform plan            # expect ONLY the managed-password change; investigate anything else
  terraform apply -target=aws_db_instance.main
  terraform apply

NEXT
