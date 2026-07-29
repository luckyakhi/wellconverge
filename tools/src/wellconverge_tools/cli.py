"""`wc-tools` — the entry point for every operation in this module.

    wc-tools doctor                 # who am I, what will I write, where
    wc-tools generate               # synthetic data -> tools/out/
    wc-tools upload                 # tools/out/ -> s3://<bucket>/raw/
    wc-tools catalog                # register Glue database + tables + partitions
    wc-tools verify                 # run an Athena query against the catalog
    wc-tools describe               # what the catalog currently holds
    wc-tools train                  # baseline readmission model
    wc-tools deploy                 # generate + upload + catalog, in one shot
    wc-tools teardown --yes         # remove everything this tool created
"""

from __future__ import annotations

import argparse
import sys
from datetime import date
from pathlib import Path

from .config import (
    APPLY_HINT,
    OUTPUTS_FILE,
    InfrastructureNotProvisioned,
    Settings,
    load_settings,
    terraform_outputs,
)
from .datagen import GenerationConfig, TABLES, generate, write_dataset
from .datagen.writer import FORMATS, partitions_on_disk


def _unset(value: str | None) -> str:
    return value if value else "(not provisioned)"


def _print_settings(settings: Settings) -> None:
    print(f"  account       {_unset(settings.account_id)}")
    print(f"  region        {settings.region}")
    print(f"  bucket        {_unset(settings.bucket)}")
    print(f"  glue database {_unset(settings.glue_database)}")
    print(f"  data prefix   {settings.data_prefix}")
    print(f"  local dir     {settings.local_dir}")


def _aws(settings: Settings):
    """Resolve provisioned settings and a guarded session, or fail with an apply hint."""
    from .awsio import guarded_session

    provisioned = settings.require_provisioned()
    return provisioned, guarded_session(provisioned)


def cmd_doctor(args: argparse.Namespace, settings: Settings) -> int:
    from .awsio import caller_identity, glue, s3
    from .awsio.session import AccountMismatch

    print("Configuration:")
    _print_settings(settings)

    print(f"\nTerraform ({OUTPUTS_FILE}):")
    if terraform_outputs():
        print("  ✓ outputs found — resource names read from Terraform")
    elif settings.provisioned:
        print("  · no outputs file; names came from environment variables")
    else:
        print(f"  ✗ not applied yet\n\n{APPLY_HINT}")
        return 1

    print("\nAWS:")
    try:
        provisioned, session = _aws(settings)
    except AccountMismatch as exc:
        print(f"  ✗ {exc}")
        return 1

    identity = caller_identity(session)
    print(f"  ✓ credentials resolve to {identity['Arn']}")

    has_bucket = s3.bucket_exists(session, provisioned.bucket)
    print(f"  {'✓' if has_bucket else '✗'} bucket {provisioned.bucket}")

    tables = glue.describe(session, provisioned)
    if tables:
        for table in tables:
            print(
                f"  ✓ glue table {table['table']:<14} "
                f"{table['columns']} cols, {table['partitions']} partitions"
            )
    else:
        print(f"  · glue database {provisioned.glue_database} holds no tables yet")

    return 0 if has_bucket else 1


def cmd_generate(args: argparse.Namespace, settings: Settings) -> int:
    cfg = GenerationConfig(
        patients=args.patients,
        seed=args.seed,
        start_date=date.fromisoformat(args.start_date),
        end_date=date.fromisoformat(args.end_date),
    )
    print(f"Generating synthetic medical data (seed={cfg.seed})…")
    dataset = generate(cfg)
    print(f"  {dataset.summary()}")

    root = Path(args.out or settings.local_dir)
    written = write_dataset(dataset, root, fmt=args.format)
    for table, paths in written.items():
        total_mb = sum(p.stat().st_size for p in paths) / 1e6
        print(f"  {table:<14} {len(paths):>3} file(s)  {total_mb:6.2f} MB  -> {root / table}")
    return 0


def cmd_upload(args: argparse.Namespace, settings: Settings) -> int:
    from .awsio import s3

    root = Path(args.out or settings.local_dir)
    if not root.exists():
        print(f"Nothing to upload: {root} does not exist. Run `wc-tools generate` first.")
        return 1

    provisioned, session = _aws(settings)
    s3.require_bucket(session, provisioned)  # Terraform's, not ours to create

    keys = s3.upload_directory(session, provisioned, root, replace=not args.no_replace)
    print(f"Uploaded {len(keys)} object(s) to {provisioned.data_uri}/")
    for key in keys[:6]:
        print(f"  s3://{provisioned.bucket}/{key}")
    if len(keys) > 6:
        print(f"  … and {len(keys) - 6} more")
    return 0


def cmd_catalog(args: argparse.Namespace, settings: Settings) -> int:
    from .awsio import glue

    provisioned, session = _aws(settings)
    glue.require_database(session, provisioned)  # Terraform's; we only add tables
    print(f"Registering tables in Glue database {provisioned.glue_database}")

    root = Path(args.out or settings.local_dir)
    for spec in TABLES:
        action = glue.register_table(session, provisioned, spec, args.format)
        values = partitions_on_disk(root / spec.name, spec)
        if spec.partition_keys and not values:
            print(f"  ! {spec.name}: no local data found under {root / spec.name}")
        count = glue.register_partitions(session, provisioned, spec, values, args.format)
        print(f"  {action:<8} {spec.name:<14} {len(spec.columns)} cols, {count} partitions")

    print(
        f"\nCatalog ready. Query it in Athena/SageMaker against database "
        f"`{provisioned.glue_database}`."
    )
    return 0


def cmd_describe(args: argparse.Namespace, settings: Settings) -> int:
    from .awsio import glue

    provisioned, session = _aws(settings)
    tables = glue.describe(session, provisioned)
    if not tables:
        print(f"Glue database {provisioned.glue_database} has no tables (or does not exist).")
        return 1

    print(f"Glue database: {provisioned.glue_database}\n")
    for table in tables:
        keys = ", ".join(table["partition_keys"]) or "—"
        print(f"  {table['table']}")
        print(f"    columns      {table['columns']}  ({table['classification']})")
        print(f"    partitioned  {keys}  ({table['partitions']} registered)")
        print(f"    location     {table['location']}")
    return 0


def cmd_verify(args: argparse.Namespace, settings: Settings) -> int:
    from .awsio import athena

    provisioned, session = _aws(settings)
    queries = {
        "row counts": (
            "SELECT 'patients' AS t, count(*) AS rows FROM patients "
            "UNION ALL SELECT 'encounters', count(*) FROM encounters "
            "UNION ALL SELECT 'observations', count(*) FROM observations"
        ),
        "readmission rate by encounter class": (
            "SELECT encounter_class, count(*) AS encounters, "
            "round(avg(CASE WHEN readmitted_30d THEN 1.0 ELSE 0.0 END), 4) AS readmit_rate "
            "FROM encounters GROUP BY encounter_class ORDER BY readmit_rate DESC"
        ),
        "partition pruning (2025 only)": (
            "SELECT year, count(*) AS observations FROM observations "
            "WHERE year = 2025 GROUP BY year"
        ),
    }
    if args.sql:
        queries = {"custom": args.sql}

    for title, sql in queries.items():
        print(f"\n── {title}")
        print(f"   {sql}")
        rows = athena.run_query(session, provisioned, sql)
        for row in rows:
            print("   " + "  ".join(f"{k}={v}" for k, v in row.items()))
    return 0


def cmd_train(args: argparse.Namespace, settings: Settings) -> int:
    from .analysis import build_feature_table, top_coefficients, train_baseline
    from .analysis.loaders import load_all

    frames = load_all(source=args.source, root=Path(args.out) if args.out else None)
    features = build_feature_table(frames["patients"], frames["encounters"], frames["observations"])
    print(f"Feature table: {len(features):,} encounters × {features.shape[1]} columns")

    result = train_baseline(features)
    print(f"Baseline logistic regression: {result}")
    print("\nTop drivers:")
    for _, row in top_coefficients(result).iterrows():
        print(f"  {row['coefficient']:+.3f}  {row['feature']}")
    return 0


def cmd_deploy(args: argparse.Namespace, settings: Settings) -> int:
    for step in (cmd_generate, cmd_upload, cmd_catalog):
        print()
        code = step(args, settings)
        if code != 0:
            return code
    return 0


def cmd_teardown(args: argparse.Namespace, settings: Settings) -> int:
    from .awsio import glue, s3

    provisioned = settings.require_provisioned()

    if not args.yes:
        print("Refusing to delete without --yes. This would remove:")
        print(f"  every object under {provisioned.data_uri}/")
        print(f"  every Athena result under {provisioned.athena_output_uri}")
        print(f"  the tables registered in {provisioned.glue_database}")
        print("\nThe bucket and the Glue database itself are Terraform's:")
        print("  cd infra && terraform destroy")
        return 1

    _, session = _aws(settings)
    for prefix in (provisioned.data_prefix, provisioned.athena_prefix):
        deleted = s3.delete_prefix(session, provisioned.bucket, f"{prefix}/")
        print(f"Deleted {deleted} S3 object(s) under s3://{provisioned.bucket}/{prefix}/")

    dropped = glue.drop_tables(session, provisioned)
    print(f"Dropped {len(dropped)} Glue table(s): {', '.join(dropped) or '—'}")

    print("\nKept (Terraform-managed — destroy them with `cd infra && terraform destroy`):")
    print(f"  s3://{provisioned.bucket}")
    print(f"  glue database {provisioned.glue_database}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="wc-tools",
        description="Synthetic medical data + AWS Glue/SageMaker catalog tooling for WellConverge.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    sub = parser.add_subparsers(dest="command", required=True)

    def add_data_args(p: argparse.ArgumentParser) -> None:
        p.add_argument("--out", help="Local dataset directory (default: tools/out)")
        p.add_argument("--format", choices=FORMATS, default="parquet")

    sub.add_parser("doctor", help="Check credentials, config and current AWS state")

    gen = sub.add_parser("generate", help="Generate synthetic medical data locally")
    add_data_args(gen)
    gen.add_argument("--patients", type=int, default=2_000)
    gen.add_argument("--seed", type=int, default=GenerationConfig.seed)
    gen.add_argument("--start-date", default=str(GenerationConfig.start_date))
    gen.add_argument("--end-date", default=str(GenerationConfig.end_date))

    up = sub.add_parser("upload", help="Upload the local dataset to S3")
    add_data_args(up)
    up.add_argument("--no-replace", action="store_true", help="Keep existing objects under the prefix")

    cat = sub.add_parser("catalog", help="Register the dataset in the Glue Data Catalog")
    add_data_args(cat)

    sub.add_parser("describe", help="Show what the Glue catalog currently holds")

    ver = sub.add_parser("verify", help="Run Athena queries against the catalog")
    ver.add_argument("--sql", help="Run this SQL instead of the built-in checks")

    tr = sub.add_parser("train", help="Train the baseline readmission model")
    tr.add_argument("--out", help="Local dataset directory (default: tools/out)")
    tr.add_argument("--source", choices=("local", "s3"), default="local")

    dep = sub.add_parser("deploy", help="generate + upload + catalog")
    add_data_args(dep)
    dep.add_argument("--patients", type=int, default=2_000)
    dep.add_argument("--seed", type=int, default=GenerationConfig.seed)
    dep.add_argument("--start-date", default=str(GenerationConfig.start_date))
    dep.add_argument("--end-date", default=str(GenerationConfig.end_date))
    dep.add_argument("--no-replace", action="store_true")

    td = sub.add_parser("teardown", help="Delete the uploaded data and the Glue database")
    td.add_argument("--yes", action="store_true", help="Confirm deletion")

    return parser


COMMANDS = {
    "doctor": cmd_doctor,
    "generate": cmd_generate,
    "upload": cmd_upload,
    "catalog": cmd_catalog,
    "describe": cmd_describe,
    "verify": cmd_verify,
    "train": cmd_train,
    "deploy": cmd_deploy,
    "teardown": cmd_teardown,
}


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    settings = load_settings()

    from .awsio.session import AccountMismatch, MissingCredentials

    try:
        return COMMANDS[args.command](args, settings)
    except (AccountMismatch, MissingCredentials, InfrastructureNotProvisioned) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
