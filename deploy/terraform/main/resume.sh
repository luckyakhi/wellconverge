#!/usr/bin/env bash
# Un-parks the environment: RDS started, ALB recreated (NEW DNS name), ECS back to desired_count.
# Runs as the deployer role. Shows the plan and asks before applying. See ADR-0007.
set -euo pipefail
cd "$(dirname "$0")"
export AWS_PROFILE="${AWS_PROFILE:-wellconverge-deployer}"

plan="$(mktemp -t wellconverge-resume.XXXXXX)"
trap 'rm -f "$plan"' EXIT

terraform plan -var paused=false -out="$plan"
read -r -p "Apply this plan? [y/N] " answer
[[ "$answer" == [yY] ]] || { echo "Aborted."; exit 1; }
terraform apply "$plan"
