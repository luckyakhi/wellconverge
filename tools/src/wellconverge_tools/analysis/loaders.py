"""Load the dataset for analysis, from local disk or straight from S3.

Both paths return identical frames, so a notebook developed against local data
runs unchanged against the lake.
"""

from __future__ import annotations

from pathlib import Path

import pandas as pd

from ..config import Settings, load_settings
from ..datagen.schema import TABLES_BY_NAME


def read_local(table: str, root: Path | None = None) -> pd.DataFrame:
    """Read a table from a local Hive-partitioned Parquet tree."""
    settings = load_settings()
    root = root or settings.local_dir
    spec = TABLES_BY_NAME[table]
    table_root = Path(root) / table

    if not table_root.exists():
        raise FileNotFoundError(
            f"No local data at {table_root}. Run `wc-tools generate` first."
        )

    frame = pd.read_parquet(table_root)
    # pandas infers partition columns as categorical; make them plain ints.
    for key in spec.partition_keys:
        if key.name in frame.columns:
            frame[key.name] = frame[key.name].astype(int)
    return frame[spec.all_column_names]


def read_s3(table: str, settings: Settings | None = None) -> pd.DataFrame:
    """Read a table directly from the data lake (needs read access to the bucket)."""
    provisioned = (settings or load_settings()).require_provisioned()
    spec = TABLES_BY_NAME[table]
    frame = pd.read_parquet(provisioned.table_uri(table))
    for key in spec.partition_keys:
        if key.name in frame.columns:
            frame[key.name] = frame[key.name].astype(int)
    return frame[spec.all_column_names]


def load_all(source: str = "local", root: Path | None = None) -> dict[str, pd.DataFrame]:
    reader = (lambda t: read_local(t, root)) if source == "local" else read_s3
    return {name: reader(name) for name in TABLES_BY_NAME}
