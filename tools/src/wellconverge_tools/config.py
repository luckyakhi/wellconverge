"""Resolved settings for the tools module.

Resource names are **read, never invented**. Terraform owns the bucket and the
Glue database (ADR-0005), so this module resolves them in order:

1. explicit environment variables — highest precedence, for one-off overrides
2. `infra/terraform-outputs.json`, written by `make -C infra outputs`
3. nothing — and every AWS command fails with an instruction to apply Terraform

There is deliberately no fallback that guesses a bucket name: guessing is how
code and infrastructure drift apart.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
INFRA_DIR = REPO_ROOT / "infra"
OUTPUTS_FILE = INFRA_DIR / "terraform-outputs.json"

DEFAULT_ATHENA_WORKGROUP = "primary"

APPLY_HINT = (
    "Provision it with Terraform (ADR-0005):\n"
    "  cd infra\n"
    "  terraform init && terraform apply\n"
    "  terraform output -json > terraform-outputs.json\n"
    f"That creates the bucket and Glue database, and writes {OUTPUTS_FILE.relative_to(REPO_ROOT)}."
)


class InfrastructureNotProvisioned(RuntimeError):
    """Raised when a command needs AWS resources that Terraform has not created."""


@lru_cache(maxsize=1)
def terraform_outputs(path: Path | None = None) -> dict[str, str]:
    """Read `terraform output -json`. Returns {} when it has not been generated."""
    path = path or OUTPUTS_FILE
    if not path.exists():
        return {}

    try:
        raw = json.loads(path.read_text())
    except json.JSONDecodeError as exc:
        raise InfrastructureNotProvisioned(
            f"{path} is not valid JSON. Regenerate it with `make -C infra outputs`."
        ) from exc

    # `terraform output -json` wraps each value: {"bucket": {"value": "...", ...}}
    return {key: entry["value"] for key, entry in raw.items() if "value" in entry}


@dataclass(frozen=True)
class Settings:
    account_id: str | None
    region: str
    bucket: str | None
    data_prefix: str
    athena_prefix: str
    athena_workgroup: str
    glue_database: str | None
    local_dir: Path

    @property
    def provisioned(self) -> bool:
        return bool(self.bucket and self.glue_database and self.account_id)

    def require_provisioned(self) -> "ProvisionedSettings":
        """Narrow to settings that are safe to use against AWS, or explain what's missing."""
        missing = [
            name
            for name, value in (
                ("bucket", self.bucket),
                ("glue_database", self.glue_database),
                ("aws_account_id", self.account_id),
            )
            if not value
        ]
        if missing:
            raise InfrastructureNotProvisioned(
                f"Missing infrastructure settings: {', '.join(missing)}.\n{APPLY_HINT}"
            )
        return ProvisionedSettings(
            account_id=self.account_id,
            region=self.region,
            bucket=self.bucket,
            data_prefix=self.data_prefix,
            athena_prefix=self.athena_prefix,
            athena_workgroup=self.athena_workgroup,
            glue_database=self.glue_database,
            local_dir=self.local_dir,
        )


@dataclass(frozen=True)
class ProvisionedSettings:
    """Settings with the Terraform-managed values known to be present."""

    account_id: str
    region: str
    bucket: str
    data_prefix: str
    athena_prefix: str
    athena_workgroup: str
    glue_database: str
    local_dir: Path

    @property
    def data_uri(self) -> str:
        return f"s3://{self.bucket}/{self.data_prefix}"

    @property
    def athena_output_uri(self) -> str:
        return f"s3://{self.bucket}/{self.athena_prefix}/"

    def table_uri(self, table: str) -> str:
        return f"{self.data_uri}/{table}"


def load_settings(outputs_path: Path | None = None) -> Settings:
    tf = terraform_outputs(outputs_path)

    def resolve(env_var: str, output_key: str, fallback: str | None = None) -> str | None:
        return os.getenv(env_var) or tf.get(output_key) or fallback

    region = (
        os.getenv("WC_AWS_REGION")
        or tf.get("aws_region")
        or os.getenv("AWS_REGION")
        or os.getenv("AWS_DEFAULT_REGION")
        or "ap-south-1"
    )

    return Settings(
        account_id=resolve("WC_AWS_ACCOUNT_ID", "aws_account_id"),
        region=region,
        bucket=resolve("WC_S3_BUCKET", "bucket"),
        data_prefix=(resolve("WC_S3_DATA_PREFIX", "data_prefix", "raw") or "raw").strip("/"),
        athena_prefix=(
            resolve("WC_S3_ATHENA_PREFIX", "athena_prefix", "athena-results") or "athena-results"
        ).strip("/"),
        athena_workgroup=os.getenv("WC_ATHENA_WORKGROUP", DEFAULT_ATHENA_WORKGROUP),
        glue_database=resolve("WC_GLUE_DATABASE", "glue_database"),
        local_dir=Path(os.getenv("WC_LOCAL_DIR", REPO_ROOT / "tools" / "out")),
    )
