"""Dataset upload into the Terraform-managed data lake.

Objects are data; the bucket holding them is infrastructure. This module writes
and deletes objects, and only ever *checks* that the bucket exists.
"""

from __future__ import annotations

from pathlib import Path

import boto3
from botocore.exceptions import ClientError

from ..config import APPLY_HINT, InfrastructureNotProvisioned, ProvisionedSettings


def bucket_exists(session: boto3.Session, bucket: str) -> bool:
    try:
        session.client("s3").head_bucket(Bucket=bucket)
        return True
    except ClientError as exc:
        code = exc.response["Error"]["Code"]
        if code in ("404", "NoSuchBucket"):
            return False
        if code == "403":
            raise RuntimeError(
                f"Bucket {bucket!r} exists but is owned by another account, or access is denied."
            ) from exc
        raise


def require_bucket(session: boto3.Session, settings: ProvisionedSettings) -> None:
    """Assert the Terraform-managed bucket exists.

    This module does not create buckets — Terraform does (ADR-0005). Failing here
    with an apply instruction is better than conjuring a bucket that no one
    declared and no one can destroy.
    """
    if not bucket_exists(session, settings.bucket):
        raise InfrastructureNotProvisioned(
            f"Bucket {settings.bucket!r} does not exist in account {settings.account_id}.\n"
            f"{APPLY_HINT}"
        )


def upload_directory(
    session: boto3.Session, settings: ProvisionedSettings, local_root: Path, replace: bool = True
) -> list[str]:
    """Upload `local_root` under the configured data prefix, preserving layout."""
    s3 = session.client("s3")
    prefix = settings.data_prefix

    if replace:
        delete_prefix(session, settings.bucket, f"{prefix}/")

    uploaded: list[str] = []
    for path in sorted(p for p in local_root.rglob("*") if p.is_file()):
        key = f"{prefix}/{path.relative_to(local_root).as_posix()}"
        s3.upload_file(str(path), settings.bucket, key)
        uploaded.append(key)
    return uploaded


def delete_prefix(session: boto3.Session, bucket: str, prefix: str) -> int:
    """Delete every object under a prefix. Returns the number deleted."""
    s3 = session.client("s3")
    if not bucket_exists(session, bucket):
        return 0

    deleted = 0
    paginator = s3.get_paginator("list_objects_v2")
    for page in paginator.paginate(Bucket=bucket, Prefix=prefix):
        keys = [{"Key": obj["Key"]} for obj in page.get("Contents", [])]
        if not keys:
            continue
        # delete_objects caps at 1000 keys per call, which matches the page size.
        s3.delete_objects(Bucket=bucket, Delete={"Objects": keys})
        deleted += len(keys)
    return deleted
