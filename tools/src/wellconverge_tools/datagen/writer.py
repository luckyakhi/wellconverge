"""Write a Dataset to disk in Hive-partitioned layout.

The on-disk layout is exactly what lands in S3, so anything that works locally
works in the catalog:

    <root>/patients/part-0000.parquet
    <root>/encounters/year=2024/part-0000.parquet
    <root>/observations/year=2024/part-0000.parquet
"""

from __future__ import annotations

import shutil
from pathlib import Path

import pandas as pd

from .medical import Dataset
from .schema import TABLES_BY_NAME, TableSpec

FORMATS = ("parquet", "csv")


def _write_frame(frame: pd.DataFrame, path: Path, fmt: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if fmt == "parquet":
        frame.to_parquet(path, index=False, compression="snappy")
    elif fmt == "csv":
        # Athena's CSV serde has no header concept — writing one would surface
        # the header row as data in every query.
        frame.to_csv(path, index=False, header=False)
    else:  # pragma: no cover - guarded by the CLI
        raise ValueError(f"unsupported format: {fmt}")


def write_table(frame: pd.DataFrame, spec: TableSpec, root: Path, fmt: str) -> list[Path]:
    table_root = root / spec.name
    if table_root.exists():
        shutil.rmtree(table_root)

    suffix = "parquet" if fmt == "parquet" else "csv"
    written: list[Path] = []

    if not spec.partition_keys:
        target = table_root / f"part-0000.{suffix}"
        _write_frame(frame, target, fmt)
        return [target]

    keys = [c.name for c in spec.partition_keys]
    for values, group in frame.groupby(keys, sort=True):
        values = values if isinstance(values, tuple) else (values,)
        rel = Path(*[f"{k}={v}" for k, v in zip(keys, values)])
        target = table_root / rel / f"part-0000.{suffix}"
        # Partition values live in the path, never in the file.
        _write_frame(group.drop(columns=keys), target, fmt)
        written.append(target)

    return written


def write_dataset(dataset: Dataset, root: Path, fmt: str = "parquet") -> dict[str, list[Path]]:
    if fmt not in FORMATS:
        raise ValueError(f"format must be one of {FORMATS}, got {fmt!r}")

    root.mkdir(parents=True, exist_ok=True)
    return {
        name: write_table(frame, TABLES_BY_NAME[name], root, fmt)
        for name, frame in dataset.as_dict().items()
    }


def partition_values(frame: pd.DataFrame, spec: TableSpec) -> list[dict[str, str]]:
    """Distinct partition values present in a frame, as Glue wants them (strings)."""
    if not spec.partition_keys:
        return []
    keys = [c.name for c in spec.partition_keys]
    distinct = frame[keys].drop_duplicates().sort_values(keys)
    return [{k: str(row[k]) for k in keys} for _, row in distinct.iterrows()]


def partitions_on_disk(table_root: Path, spec: TableSpec) -> list[dict[str, str]]:
    """Partition values read back off the written layout.

    The catalog is registered from this rather than from the dataframe, so it can
    only ever describe partitions that really have files behind them.
    """
    if not spec.partition_keys:
        return []

    keys = [c.name for c in spec.partition_keys]
    found: list[dict[str, str]] = []

    def walk(directory: Path, depth: int, acc: dict[str, str]) -> None:
        if depth == len(keys):
            if any(p.is_file() for p in directory.iterdir()):
                found.append(acc)
            return
        for child in sorted(d for d in directory.iterdir() if d.is_dir()):
            name, _, value = child.name.partition("=")
            if name != keys[depth]:
                continue
            walk(child, depth + 1, {**acc, name: value})

    if table_root.exists():
        walk(table_root, 0, {})
    return found
