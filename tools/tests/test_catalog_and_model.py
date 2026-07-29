"""Tests for the Glue table definitions, config resolution and the ML baseline.

The Glue tests are pure — they assert on the request payloads rather than
calling AWS, so the suite stays offline and free.
"""

from __future__ import annotations

from datetime import date

import pytest

import json

from wellconverge_tools.analysis import build_feature_table, top_coefficients, train_baseline
from wellconverge_tools.awsio.glue import _table_input
from wellconverge_tools.config import (
    InfrastructureNotProvisioned,
    ProvisionedSettings,
    load_settings,
    terraform_outputs,
)
from wellconverge_tools.datagen import ENCOUNTERS, OBSERVATIONS, PATIENTS, GenerationConfig, generate
from wellconverge_tools.datagen.schema import TABLES


@pytest.fixture(scope="module")
def dataset():
    return generate(
        GenerationConfig(patients=600, seed=11, start_date=date(2024, 1, 1), end_date=date(2025, 12, 31))
    )


@pytest.fixture
def settings(tmp_path):
    return ProvisionedSettings(
        account_id="273505519511",
        region="ap-south-1",
        bucket="test-bucket",
        data_prefix="raw",
        athena_prefix="athena-results",
        athena_workgroup="primary",
        glue_database="wellconverge_health",
        local_dir=tmp_path,
    )


@pytest.fixture(autouse=True)
def isolate_config(monkeypatch, tmp_path):
    """Config resolution must not depend on the developer's real environment."""
    for var in ("WC_AWS_ACCOUNT_ID", "WC_AWS_REGION", "WC_S3_BUCKET", "WC_GLUE_DATABASE",
                "WC_S3_DATA_PREFIX", "WC_ATHENA_WORKGROUP", "AWS_REGION", "AWS_DEFAULT_REGION"):
        monkeypatch.delenv(var, raising=False)
    terraform_outputs.cache_clear()
    yield
    terraform_outputs.cache_clear()


def _write_outputs(path, **values):
    path.write_text(json.dumps({k: {"value": v, "type": "string"} for k, v in values.items()}))
    return path


# ── config: names are read, never invented ───────────────────────────────────

def test_settings_read_resource_names_from_terraform_outputs(tmp_path):
    outputs = _write_outputs(
        tmp_path / "terraform-outputs.json",
        aws_account_id="273505519511",
        aws_region="ap-south-1",
        bucket="tf-made-bucket",
        glue_database="tf_made_db",
        data_prefix="raw",
    )
    resolved = load_settings(outputs)

    assert resolved.bucket == "tf-made-bucket"
    assert resolved.glue_database == "tf_made_db"
    assert resolved.provisioned


def test_environment_overrides_terraform_outputs(tmp_path, monkeypatch):
    outputs = _write_outputs(
        tmp_path / "terraform-outputs.json",
        aws_account_id="273505519511", bucket="tf-made-bucket", glue_database="tf_made_db",
    )
    monkeypatch.setenv("WC_S3_BUCKET", "override-bucket")

    assert load_settings(outputs).bucket == "override-bucket"


def test_no_terraform_outputs_means_not_provisioned(tmp_path):
    """The whole point of ADR-0005: absent infrastructure must not be guessed at."""
    resolved = load_settings(tmp_path / "does-not-exist.json")

    assert not resolved.provisioned
    assert resolved.bucket is None
    with pytest.raises(InfrastructureNotProvisioned, match="terraform"):
        resolved.require_provisioned()


def test_require_provisioned_names_what_is_missing(tmp_path):
    outputs = _write_outputs(tmp_path / "terraform-outputs.json", aws_account_id="273505519511")

    with pytest.raises(InfrastructureNotProvisioned, match="bucket"):
        load_settings(outputs).require_provisioned()


def test_uris_are_well_formed(settings):
    assert settings.data_uri == "s3://test-bucket/raw"
    assert settings.table_uri("encounters") == "s3://test-bucket/raw/encounters"
    assert settings.athena_output_uri.endswith("/")


# ── glue table definitions ───────────────────────────────────────────────────

@pytest.mark.parametrize("spec", TABLES, ids=lambda s: s.name)
def test_table_input_covers_every_declared_column(settings, spec):
    table = _table_input(spec, settings, "parquet")
    declared = [c["Name"] for c in table["StorageDescriptor"]["Columns"]]
    partitions = [c["Name"] for c in table["PartitionKeys"]]

    assert declared + partitions == spec.all_column_names
    # A column may not be both a data column and a partition key.
    assert not set(declared) & set(partitions)


def test_partition_keys_are_never_data_columns(settings):
    table = _table_input(ENCOUNTERS, settings, "parquet")
    assert [c["Name"] for c in table["PartitionKeys"]] == ["year"]
    assert "year" not in [c["Name"] for c in table["StorageDescriptor"]["Columns"]]


def test_table_location_points_at_the_table_prefix(settings):
    table = _table_input(OBSERVATIONS, settings, "parquet")
    assert table["StorageDescriptor"]["Location"] == "s3://test-bucket/raw/observations"


def test_parquet_uses_the_parquet_serde(settings):
    table = _table_input(PATIENTS, settings, "parquet")
    assert "parquet" in table["StorageDescriptor"]["SerdeInfo"]["SerializationLibrary"].lower()
    assert table["Parameters"]["classification"] == "parquet"


def test_csv_downgrades_typed_columns_to_string(settings):
    """OpenCSVSerde reads everything as text; typed columns would fail at query time."""
    table = _table_input(PATIENTS, settings, "csv")
    types = {c["Name"]: c["Type"] for c in table["StorageDescriptor"]["Columns"]}

    assert types["birth_date"] == "string"
    assert types["registered_at"] == "string"
    assert types["smoker"] == "string"
    assert types["bmi"] == "double"  # numerics survive


def test_tables_are_marked_synthetic(settings):
    table = _table_input(PATIENTS, settings, "parquet")
    assert table["Parameters"]["wc_data_sensitivity"] == "synthetic"
    assert table["TableType"] == "EXTERNAL_TABLE"


# ── ml baseline ──────────────────────────────────────────────────────────────

def test_feature_table_has_one_row_per_encounter(dataset):
    features = build_feature_table(dataset.patients, dataset.encounters, dataset.observations)
    assert len(features) == len(dataset.encounters)
    assert features["encounter_id"].is_unique
    assert not features["abnormal_observations"].isna().any()


def test_baseline_beats_random(dataset):
    features = build_feature_table(dataset.patients, dataset.encounters, dataset.observations)
    result = train_baseline(features)

    assert result.roc_auc > 0.65, f"baseline learned nothing: ROC-AUC {result.roc_auc:.3f}"
    assert result.n_train + result.n_test == len(features)


def test_top_coefficients_are_ranked_by_magnitude(dataset):
    features = build_feature_table(dataset.patients, dataset.encounters, dataset.observations)
    coefficients = top_coefficients(train_baseline(features), n=8)

    magnitudes = coefficients["coefficient"].abs().tolist()
    assert magnitudes == sorted(magnitudes, reverse=True)
    assert len(coefficients) == 8
