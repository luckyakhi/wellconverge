# ADR-0005: Terraform is the only way AWS resources are created

- **Status:** Accepted
- **Date:** 2026-07-29

## Context

Exploratory data work on AWS (an S3 data lake, a Glue Data Catalog database, Athena workgroups,
SageMaker/Unified Studio plumbing) is easy to stand up imperatively — a console click, an
`aws s3 mb`, a `boto3.create_bucket`. The first such resources in this account were in fact created
that way by `tools/`, which called `create_bucket` and `create_database` directly.

That is fast once and expensive thereafter. Imperatively created infrastructure has no reviewable
diff, no reproducible recreation path, and no reliable teardown — you find out what exists by
listing the account and guessing why. It also splits ownership: the same bucket might be created by
a script, modified in the console, and referenced by code that assumes a third shape.

The repo already made the equivalent decision for the database, where Flyway owns the schema and the
app runs `ddl-auto: validate`. Cloud resources deserve the same treatment for the same reasons.

## Decision

**All AWS resources are declared in Terraform under `infra/` and applied from there.** No console
provisioning, no `aws` CLI `create-*`/`put-*` for resource creation, no SDK provisioning calls in
application or tooling code.

- **Terraform provisions; code consumes.** Buckets, Glue databases, IAM roles, workgroups, clusters
  are Terraform's. Application and tooling code may read from and write *data into* them — upload
  objects, register table partitions, run queries — but must not create the resource itself.
- **Identifiers arrive as configuration.** Code receives bucket and database names via environment
  variables or Terraform outputs; it does not compose names from account id and region at runtime,
  because that quietly re-implements a naming decision that belongs in one place.
- **Reads are unrestricted.** `aws ... describe-*`, `list-*`, `get-*` are fine at any time, as is
  break-glass debugging. Anything that mutates goes through `terraform plan` → `terraform apply`.
- **State** starts local, and moves to an S3 backend (with DynamoDB locking) once more than one
  machine or person applies. That bootstrap bucket is the one permitted chicken-and-egg exception
  and is documented in `infra/README.md`.
- **Narrow exception:** genuinely throwaway experiments, explicitly scoped as such, torn down in the
  same session, never referenced by committed code.

## Consequences

- A resource cannot be created without a reviewable diff, and `terraform destroy` reliably removes
  what was made — which matters most in a learning account where cost surprises come from forgotten
  resources.
- Slower for one-off exploration: standing up a bucket now means editing HCL and applying. Accepted
  — the cases where that friction hurts are exactly the cases the narrow exception covers.
- **`tools/` was reworked to comply** (done, 2026-07-29). `wc-tools` no longer creates anything: the
  bucket and Glue database were destroyed and recreated by `infra/`, and `upload`/`catalog` now read
  their names from `terraform output -json` and fail with an apply instruction when they're absent.
  There is deliberately no fallback that composes a bucket name at runtime.
- **Glue *tables* stay with the tool**, and only the *database* moved to Terraform. Table columns are
  derived from `tools/src/wellconverge_tools/datagen/schema.py`, the same declaration the Parquet
  writer uses; restating ~40 columns in HCL would reintroduce the schema drift that design exists to
  prevent. Partitions likewise stay: they are data, discovered from the files themselves.
- The dataset regenerates byte-for-byte from its seed, which is what made destroy-and-recreate the
  cheap option here instead of `terraform import`. That will not hold for anything stateful — import
  will be the right tool then.
- Requires Terraform locally (or via Docker, consistent with how the JDK is handled here).
