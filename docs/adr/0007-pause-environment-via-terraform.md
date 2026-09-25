# ADR-0007: Pause the AWS environment with a Terraform toggle, not by hand

- **Status:** Accepted
- **Date:** 2026-09-25

## Context

The AWS estate (ADR-0005) costs ~$35–40/month running, and for a learning project it sits idle most
of the time. Three things carry the hourly cost: the ALB, the RDS instance, and the Fargate task.
Only two of them can actually be *stopped*: ECS scales to zero and RDS has a stop/start API. An ALB
has no stopped state — it bills every hour it exists.

The previous advice was to scale ECS to zero with the CLI. That saved the cheapest of the three and
left Terraform's view of the service out of date, so the next apply quietly scaled it back up.

## Decision

A single `paused` variable (default `false`) in `main/`, set by `pause.sh` and `resume.sh`:

- **ECS:** `desired_count = 0`, and the `load_balancer` block becomes a `dynamic` over the target
  group so the service can exist without one.
- **ALB, listener, target group:** `count = var.paused ? 0 : 1` — destroyed while paused, recreated
  on resume. They hold no data, so recreating is cheap; the cost is a new DNS name.
- **RDS:** an `aws_rds_instance_state` resource drives the instance to `stopped` or `available`.
  Storage and data are kept. The deployer role gains `rds:StopDBInstance` / `rds:StartDBInstance`.

Rejected alternatives:
- **`terraform destroy`** — also drops the database (`skip_final_snapshot = true`). Too blunt for
  "I'll be back next week".
- **Destroying RDS with a final snapshot and restoring on resume** — a restore creates a new
  instance, with a new managed secret and endpoint, and adds a snapshot identifier to manage. A lot
  of moving parts for saving ~$1–2/month of storage cost on top of what stopping saves.
- **CLI commands outside Terraform** — drift again, which is the problem we're fixing.

## Consequences

- Idle cost drops from ~$35–40/month to ~$2–3/month (RDS storage, the managed secret, ECR/logs).
- Pausing is declared in code and visible in `terraform output paused`, not tribal knowledge.
- **The default is "running".** A plain `terraform apply` in a paused environment resumes it. Pass
  `-var paused=true` while parked. Making the pause sticky would need it stored somewhere shared,
  such as a tfvars file in the state bucket, which isn't worth it yet.
- **AWS restarts a stopped RDS instance after 7 days.** A long pause needs `pause.sh` re-run weekly.
  After such a restart, Terraform sees drift on `aws_rds_instance_state` and the next pause fixes it.
- Resume takes ~10 minutes (RDS start dominates) and yields a new ALB DNS name.
