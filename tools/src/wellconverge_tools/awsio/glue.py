"""AWS Glue Data Catalog registration.

The Glue Data Catalog *is* the catalog that SageMaker Studio, SageMaker Data
Wrangler, Athena, EMR and Redshift Spectrum all read technical metadata from
(SageMaker Unified Studio / SageMaker Catalog layers business metadata on top of
these same Glue tables). So registering tables here is what makes the dataset
discoverable from SageMaker.

Tables are declared explicitly rather than crawled: a crawler costs money, needs
its own IAM role, and infers a schema that can drift from the one in
`datagen/schema.py`. Explicit registration keeps the catalog and the files
provably in sync.
"""

from __future__ import annotations

import boto3
from botocore.exceptions import ClientError

from ..config import APPLY_HINT, InfrastructureNotProvisioned, ProvisionedSettings
from ..datagen.schema import TABLES, TableSpec

PARQUET_SERDE = {
    "input_format": "org.apache.hadoop.hive.ql.io.parquet.MapredParquetInputFormat",
    "output_format": "org.apache.hadoop.hive.ql.io.parquet.MapredParquetOutputFormat",
    "serde": "org.apache.hadoop.hive.ql.io.parquet.serde.ParquetHiveSerDe",
    "parameters": {"serialization.format": "1"},
    "classification": "parquet",
}
CSV_SERDE = {
    "input_format": "org.apache.hadoop.mapred.TextInputFormat",
    "output_format": "org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat",
    "serde": "org.apache.hadoop.hive.serde2.OpenCSVSerde",
    "parameters": {"separatorChar": ",", "quoteChar": '"', "escapeChar": "\\"},
    "classification": "csv",
}


def _serde(fmt: str) -> dict:
    return PARQUET_SERDE if fmt == "parquet" else CSV_SERDE


def _glue_type(column_type: str, fmt: str) -> str:
    # OpenCSVSerde reads every column as text; typed columns would fail at query time.
    if fmt == "csv" and column_type in ("date", "timestamp", "boolean"):
        return "string"
    return column_type


def require_database(session: boto3.Session, settings: ProvisionedSettings) -> None:
    """Assert the Terraform-managed Glue database exists.

    Terraform owns the database (ADR-0005); this module only fills it with tables.
    """
    glue = session.client("glue")
    try:
        glue.get_database(Name=settings.glue_database)
    except ClientError as exc:
        if exc.response["Error"]["Code"] != "EntityNotFoundException":
            raise
        raise InfrastructureNotProvisioned(
            f"Glue database {settings.glue_database!r} does not exist in "
            f"account {settings.account_id}.\n{APPLY_HINT}"
        ) from exc


def _table_input(spec: TableSpec, settings: ProvisionedSettings, fmt: str) -> dict:
    serde = _serde(fmt)
    return {
        "Name": spec.name,
        "Description": spec.description,
        "TableType": "EXTERNAL_TABLE",
        "Parameters": {
            "EXTERNAL": "TRUE",
            "classification": serde["classification"],
            "has_encrypted_data": "false",
            "wc_managed_by": "wellconverge-tools",
            "wc_data_sensitivity": "synthetic",
        },
        "PartitionKeys": [
            {"Name": c.name, "Type": _glue_type(c.glue_type, fmt), "Comment": c.comment}
            for c in spec.partition_keys
        ],
        "StorageDescriptor": {
            "Columns": [
                {"Name": c.name, "Type": _glue_type(c.glue_type, fmt), "Comment": c.comment}
                for c in spec.columns
            ],
            "Location": settings.table_uri(spec.name),
            "InputFormat": serde["input_format"],
            "OutputFormat": serde["output_format"],
            "Compressed": fmt == "parquet",
            "SerdeInfo": {
                "SerializationLibrary": serde["serde"],
                "Parameters": serde["parameters"],
            },
        },
    }


def register_table(
    session: boto3.Session, settings: ProvisionedSettings, spec: TableSpec, fmt: str
) -> str:
    """Create or update a table definition. Returns 'created' or 'updated'."""
    glue = session.client("glue")
    table_input = _table_input(spec, settings, fmt)
    try:
        glue.create_table(DatabaseName=settings.glue_database, TableInput=table_input)
        return "created"
    except ClientError as exc:
        if exc.response["Error"]["Code"] != "AlreadyExistsException":
            raise
        glue.update_table(DatabaseName=settings.glue_database, TableInput=table_input)
        return "updated"


def register_partitions(
    session: boto3.Session,
    settings: ProvisionedSettings,
    spec: TableSpec,
    values: list[dict[str, str]],
    fmt: str,
) -> int:
    """Register Hive partitions so queries prune instead of scanning everything."""
    if not spec.partition_keys or not values:
        return 0

    glue = session.client("glue")
    base = _table_input(spec, settings, fmt)["StorageDescriptor"]
    keys = [c.name for c in spec.partition_keys]

    inputs = []
    for value_map in values:
        ordered = [value_map[k] for k in keys]
        suffix = "/".join(f"{k}={v}" for k, v in zip(keys, ordered))
        sd = {**base, "Location": f"{settings.table_uri(spec.name)}/{suffix}"}
        inputs.append({"Values": ordered, "StorageDescriptor": sd})

    registered = 0
    for chunk_start in range(0, len(inputs), 100):  # batch_create_partition caps at 100
        chunk = inputs[chunk_start : chunk_start + 100]
        response = glue.batch_create_partition(
            DatabaseName=settings.glue_database,
            TableName=spec.name,
            PartitionInputList=chunk,
        )
        errors = [
            e for e in response.get("Errors", [])
            if e["ErrorDetail"]["ErrorCode"] != "AlreadyExistsException"
        ]
        if errors:
            raise RuntimeError(f"Failed to register partitions on {spec.name}: {errors}")
        registered += len(chunk)
    return registered


def drop_tables(session: boto3.Session, settings: ProvisionedSettings) -> list[str]:
    """Delete the tool-registered tables, leaving the Terraform-owned database.

    Removing the database here would delete a resource Terraform believes it
    manages — `cd infra && terraform destroy` is the way to do that.
    """
    glue = session.client("glue")
    dropped: list[str] = []
    for spec in TABLES:
        try:
            glue.delete_table(DatabaseName=settings.glue_database, Name=spec.name)
            dropped.append(spec.name)
        except ClientError as exc:
            if exc.response["Error"]["Code"] != "EntityNotFoundException":
                raise
    return dropped


def describe(session: boto3.Session, settings: ProvisionedSettings) -> list[dict]:
    """Summarise what the catalog currently holds for this database."""
    glue = session.client("glue")
    try:
        tables = glue.get_tables(DatabaseName=settings.glue_database)["TableList"]
    except ClientError as exc:
        if exc.response["Error"]["Code"] == "EntityNotFoundException":
            return []
        raise

    summary = []
    for table in tables:
        partitions = glue.get_partitions(
            DatabaseName=settings.glue_database, TableName=table["Name"]
        )["Partitions"]
        summary.append(
            {
                "table": table["Name"],
                "columns": len(table["StorageDescriptor"]["Columns"]),
                "partition_keys": [k["Name"] for k in table.get("PartitionKeys", [])],
                "partitions": len(partitions),
                "location": table["StorageDescriptor"]["Location"],
                "classification": table.get("Parameters", {}).get("classification", "?"),
            }
        )
    return summary
