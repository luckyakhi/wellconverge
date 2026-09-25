#!/usr/bin/env bash
# Parks the environment: ECS -> 0 tasks, ALB destroyed, RDS stopped (data kept).
# AWS auto-starts a stopped RDS instance after 7 days -- re-run this weekly for a long pause.
# Runs as the deployer role. Shows the plan and asks before applying. See ADR-0007.
set -euo pipefail
cd "$(dirname "$0")"
export AWS_PROFILE="${AWS_PROFILE:-wellconverge-deployer}"

plan="$(mktemp -t wellconverge-pause.XXXXXX)"
trap 'rm -f "$plan"' EXIT

terraform plan -var paused=true -out="$plan"
read -r -p "Apply this plan? [y/N] " answer
[[ "$answer" == [yY] ]] || { echo "Aborted."; exit 1; }
terraform apply "$plan"
