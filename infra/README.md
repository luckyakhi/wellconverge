# `infra/` — Terraform

**Every AWS resource in this project is declared here.** No console clicking, no `aws ... create-*`,
no boto3 provisioning — see [ADR-0005](../docs/adr/0005-terraform-for-all-aws-resources.md).

## What this module creates

| Resource | Name | Why |
|---|---|---|
| S3 bucket | `wellconverge-datalake-<account>-<region>` | The data lake: dataset under `raw/`, Athena results under `athena-results/` |
| Public access block, ownership controls, SSE-S3, versioning off | on that bucket | Private and encrypted by default; ACLs disabled |
| Lifecycle rules | on that bucket | Expire Athena results after 14 days, abort stale multipart uploads |
| Glue Catalog database | `wellconverge_health` | The namespace SageMaker Studio, Athena, EMR and Redshift Spectrum resolve tables from |

## Usage

```bash
cd infra
terraform init
terraform plan
terraform apply
terraform output -json > terraform-outputs.json   # hand the names to wc-tools
```

If you have `make`, the `Makefile` wraps these (`make init`, `make apply` — which also writes the
outputs file). It isn't installed on this machine, so the commands above are the primary path.

Then load the data (that part is Python, deliberately — objects are data, not infrastructure):

```bash
cd ../tools
uv run wc-tools generate   # synthetic data -> tools/out/
uv run wc-tools upload     # -> s3://<bucket>/raw/
uv run wc-tools catalog    # register tables + partitions in the Glue database
uv run wc-tools verify     # prove it resolves, via Athena
```

## The line between Terraform and `wc-tools`

| | Owner | Why |
|---|---|---|
| Bucket, Glue **database** | **Terraform** | Long-lived resources; need a reviewable diff and a reliable destroy |
| Objects in the bucket | `wc-tools upload` | Data. Regenerable from a seed |
| Glue **tables** | `wc-tools catalog` | Their columns come from `tools/src/wellconverge_tools/datagen/schema.py`, the same source the Parquet writer uses. Restating ~40 columns in HCL would reintroduce exactly the schema drift that design prevents |
| Glue **partitions** | `wc-tools catalog` | Discovered from the files on disk, so the catalog can only describe partitions that really exist |

`wc-tools` **never creates** a bucket or database. It reads their names from `terraform-outputs.json`
(or `WC_*` environment variables) and fails with an apply instruction when they're absent — it will
not guess a name into existence.

## State

State is **local** (`terraform.tfstate`, gitignored) — fine for one machine. Move to a remote backend
before a second person or CI applies; the commented `backend "s3"` block in `versions.tf` has the
shape. The state bucket and lock table are the one bootstrap chicken-and-egg exception to ADR-0005:
create them once, by hand or by a separate minimal module, and record how.

`.terraform.lock.hcl` **is committed** so provider versions are reproducible.

## Teardown

```bash
cd ../tools && uv run wc-tools teardown --yes   # objects + Glue tables
cd ../infra && terraform destroy                # bucket + Glue database
```

`terraform destroy` alone is enough — `force_destroy = true` empties the bucket first. Running the
`wc-tools teardown` step first just makes the destroy plan smaller and easier to read.

## Cost

Everything here is effectively free: S3 storage for ~2 MB, and the Glue Data Catalog is free below
one million objects. Athena bills $5/TB scanned with a 10 MB minimum, so the `verify` queries cost
fractions of a cent. Nothing in this module starts a SageMaker domain, notebook space, or Glue
crawler — those are the line items that actually cost money.
