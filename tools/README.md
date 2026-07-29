# `tools/` — adhoc data analysis & ML

A Python side-car to the WellConverge monolith, for data work that doesn't belong in the Java
domain: synthetic dataset generation, exploratory analysis, ML baselines, and AWS data-catalog
experiments.

**This module is not part of the modular monolith.** It imports nothing from `backend/`, owns no
product behaviour, and the architecture rules in [`../CLAUDE.md`](../CLAUDE.md) (hexagonal layering,
BDD-first) do not apply here. It is a lab bench, not a bounded context.

> All medical data here is **fabricated** by seeded RNG. There is no real patient data in this repo
> and none should ever be committed.

> **This module creates no AWS resources** — [ADR-0005](../docs/adr/0005-terraform-for-all-aws-resources.md).
> The bucket and the Glue database are Terraform's ([`../infra/`](../infra)). `wc-tools` reads their
> names from `infra/terraform-outputs.json` and writes *data* into them; if they don't exist it
> fails with an apply instruction rather than conjuring them.

## Setup

Uses [uv](https://docs.astral.sh/uv/) — no system Python packages, no sudo:

```bash
curl -LsSf https://astral.sh/uv/install.sh | sh   # once
cd tools
uv sync --extra dev                               # creates .venv and installs
uv run pytest                                     # 33 tests, all offline
```

Prefix commands with `uv run`, or activate with `source .venv/bin/activate`.

**Provision the infrastructure first** — everything except `generate` and `train` needs it:

```bash
cd ../infra && terraform init && terraform apply
terraform output -json > terraform-outputs.json
```

## What it does

```bash
uv run wc-tools doctor      # who am I, what will I write, and where
uv run wc-tools generate    # synthetic medical data -> tools/out/   (no AWS needed)
uv run wc-tools upload      # tools/out/ -> s3://<bucket>/raw/
uv run wc-tools catalog     # register tables + partitions in the Glue database
uv run wc-tools verify      # run Athena queries to prove the catalog resolves
uv run wc-tools describe    # what the catalog currently holds
uv run wc-tools train       # baseline 30-day readmission model  (no AWS needed)
uv run wc-tools deploy      # generate + upload + catalog in one shot
uv run wc-tools teardown --yes   # delete the objects and tables this tool created
```

`teardown` removes only what this tool wrote. The bucket and database are Terraform's:
`cd ../infra && terraform destroy`.

## The dataset

Three referentially-consistent tables, Hive-partitioned by event year:

| Table | Grain | Notes |
|---|---|---|
| `patients` | one row per patient | demographics, insurance, BMI, chronic-condition count |
| `encounters` | one row per visit | ICD-10 diagnosis, LOS, charges, **`readmitted_30d` label** |
| `observations` | one row per measurement | LOINC vitals/labs with reference ranges and L/N/H flags |

The generator is **seeded and deterministic** — same seed, byte-identical output — and deliberately
bakes a *learnable* relationship into `readmitted_30d` (age, length of stay, abnormal labs, encounter
class, chronic burden, payer) so the ML baseline has real signal to find rather than noise.

Schema lives in one place, [`datagen/schema.py`](src/wellconverge_tools/datagen/schema.py), and is
reused by both the writer and the Glue table definitions — so a table can't drift from the files
behind it, which is the usual reason catalogs stop being trustworthy.

## About the "SageMaker data catalog"

SageMaker doesn't have a separate catalog of its own. **SageMaker Studio, Data Wrangler, Feature
Store, Athena, EMR and Redshift Spectrum all read technical metadata from the AWS Glue Data
Catalog**, so that's what this module writes to. SageMaker Unified Studio's "SageMaker Catalog"
(built on DataZone) layers business metadata, projects and subscriptions *on top of* these same
Glue tables — so registering here is the prerequisite step either way.

Tables are declared explicitly rather than discovered by a crawler: a crawler costs money per run,
needs its own IAM role, and infers a schema that can drift from the declared one.

To take it further into SageMaker proper: point a Studio domain or a SageMaker Unified Studio project
at the `wellconverge_health` database, or run `wc-tools verify --sql "..."` for quick Athena checks.

## Configuration

Resource names are **read, never invented**, in this order:

1. `WC_*` environment variables — for one-off overrides
2. `infra/terraform-outputs.json` — the normal path
3. nothing — every AWS command then fails with an apply instruction

| Variable | Terraform output | Fallback |
|---|---|---|
| `WC_AWS_ACCOUNT_ID` | `aws_account_id` | — (required) |
| `WC_AWS_REGION` | `aws_region` | `ap-south-1` |
| `WC_S3_BUCKET` | `bucket` | — (required) |
| `WC_S3_DATA_PREFIX` | `data_prefix` | `raw` |
| `WC_S3_ATHENA_PREFIX` | `athena_prefix` | `athena-results` |
| `WC_GLUE_DATABASE` | `glue_database` | — (required) |
| `WC_ATHENA_WORKGROUP` | — | `primary` |
| `WC_LOCAL_DIR` | — | `tools/out` |

There is deliberately no fallback that composes a bucket name from account and region: guessing is
how code and infrastructure drift apart.

Every AWS write goes through `guarded_session`, which calls STS first and **refuses to run if the
resolved account isn't the configured one** — a stale `AWS_PROFILE` can't quietly land data in
someone else's account. Terraform applies the same guard via `allowed_account_ids`.

## Notebooks

[`notebooks/01-explore-medical-data.ipynb`](notebooks/01-explore-medical-data.ipynb) — cohort
profile, readmission drivers, the baseline model, and the same data read back through Athena.

```bash
uv run --extra notebook jupyter lab
```

## IAM

The credentials need: `sts:GetCallerIdentity`; `s3:CreateBucket`, `PutObject`, `GetObject`,
`ListBucket`, `DeleteObject`, `PutBucketPublicAccessBlock`, `PutEncryptionConfiguration`;
`glue:*Database`, `glue:*Table`, `glue:*Partition*`; and for `verify`, `athena:StartQueryExecution`,
`GetQueryExecution`, `GetQueryResults`.

## Cost

Small. S3 storage for ~2 MB is fractions of a cent; the Glue Data Catalog is free below 1M objects;
Athena bills $5/TB scanned with a 10 MB minimum per query, so the `verify` queries cost well under a
cent. Nothing here starts a SageMaker domain, notebook instance, or Glue crawler — those are the
line items that actually cost money. To remove it all: `wc-tools teardown --yes` for the data and
tables, then `cd ../infra && terraform destroy` for the bucket and database.
