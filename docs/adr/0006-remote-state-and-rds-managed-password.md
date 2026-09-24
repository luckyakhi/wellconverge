# ADR-0006: Remote Terraform state in S3, and an RDS-managed database password

- **Status:** Accepted
- **Date:** 2026-09-24

## Context

ADR-0005 left `deploy/terraform/main/` on local state, with a note that it could move to S3 "once
that stack provisions a bucket". After the first real deployment, two problems with that became
concrete.

**The state file was a single point of failure.** `.gitignore` excludes `*.tfstate`, so the only
copy of `main/`'s state lived on one laptop. Losing it would orphan 28 live AWS resources: they keep
running and billing, and the next `apply` tries to recreate names that are already taken.

**The obvious fix — committing state to git — is not available.** Terraform writes the *resolved
value* of every attribute into state. The generated database password was therefore sitting in
`terraform.tfstate` in plaintext in three places:

```
random_password.db.result
aws_db_instance.main.password
aws_secretsmanager_secret_version.db_credentials.secret_string
```

Moving the password to a `TF_VAR_` environment variable does **not** change this — the resolved
value still lands in `aws_db_instance.main.password`. Marking a variable `sensitive = true` only
redacts CLI output. And because state is one JSON document, there's no way to commit "the rest of"
it while holding the secret back. Committing state would put a live credential into git history,
where deletion doesn't remove it.

## Decision

**State for `main/` moves to S3**, in a versioned, encrypted, public-access-blocked bucket, with a
DynamoDB table for locking. Not git: git has no locking, and a merge conflict inside a state file is
effectively unresolvable by hand.

**The bucket and lock table live in their own `state/` stack**, not in `main/` and not in
`bootstrap/`:
- Not `main/` — a stack managing the bucket that holds its own state would try to delete that bucket
  during `terraform destroy`.
- Not `bootstrap/` — that stack is applied by the deliberately-tiny bootstrap IAM user, which has no
  S3 or DynamoDB permissions. Only the deployer role does.

`state/` and `bootstrap/` keep local state. Each holds a few trivially re-importable resources and
no secrets, so the cost of losing them is a re-import, not an orphaned estate.

**The database password becomes RDS-managed** (`manage_master_user_password = true`). RDS generates
the password, stores it in a Secrets Manager secret that AWS owns and can rotate, and Terraform only
ever sees the ARN. This is what actually removes the secret from state — and therefore what makes a
shared backend safe. `random_password` and the hand-rolled `aws_secretsmanager_secret_version` are
deleted; the ECS task execution role now reads `master_user_secret[0].secret_arn`.

The deployer role gains `s3:*` and `dynamodb:*` **scoped by ARN** to exactly the state bucket and
lock table. Action-level enumeration was rejected because `aws_s3_bucket` refreshes a long tail of
sub-resource reads (versioning, encryption, policy, ownership, lifecycle, CORS…) and missing any one
of them fails the plan — the same reasoning already applied to `elasticloadbalancing:*` in ADR-0005.

## Consequences

- State is durable, versioned, and locked. Two machines can deploy without corrupting each other.
- No credential has ever to appear in state, git, or a developer's shell history.
- **Enabling a managed password on a live instance is a two-phase apply.** The secret doesn't exist
  until the change lands, so the same plan can't also reference it — Terraform fails with
  `Invalid index ... empty list of object`. The migration runs
  `terraform apply -target=aws_db_instance.main` first, then a full apply. A fresh deployment onto an
  empty account needs no targeting. This is documented in `deploy/terraform/README.md`.
- Phase 1 rotates the master password, so the old secret goes stale immediately and a running task
  briefly holds invalid credentials. Harmless during initial setup; it would be a short outage on a
  live service.
- Three stacks instead of two. The extra one is small and applied once.
- Outputs referencing `master_user_secret` use `one(...)` rather than `[0]`, because outputs are
  evaluated on every plan — including the phase-1 apply, where the list is still empty.
