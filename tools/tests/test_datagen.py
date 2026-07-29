"""Tests for the synthetic data generator.

These guard the properties the catalog and the ML baseline depend on:
determinism, referential integrity, schema conformance and a learnable label.
"""

from __future__ import annotations

from datetime import date

import pandas as pd
import pytest

from wellconverge_tools.datagen import (
    ENCOUNTERS,
    OBSERVATIONS,
    PATIENTS,
    TABLES,
    GenerationConfig,
    generate,
    partitions_on_disk,
    write_dataset,
)

SMALL = GenerationConfig(patients=200, seed=7, start_date=date(2024, 1, 1), end_date=date(2025, 12, 31))


@pytest.fixture(scope="module")
def dataset():
    return generate(SMALL)


def test_generation_is_deterministic():
    first, second = generate(SMALL), generate(SMALL)
    for name in (PATIENTS.name, ENCOUNTERS.name, OBSERVATIONS.name):
        pd.testing.assert_frame_equal(first.as_dict()[name], second.as_dict()[name])


def test_a_different_seed_produces_different_data():
    other = generate(GenerationConfig(patients=SMALL.patients, seed=SMALL.seed + 1,
                                      start_date=SMALL.start_date, end_date=SMALL.end_date))
    assert other.patients["patient_id"].tolist() != generate(SMALL).patients["patient_id"].tolist()


@pytest.mark.parametrize("spec", TABLES, ids=lambda s: s.name)
def test_columns_match_the_declared_schema(dataset, spec):
    assert list(dataset.as_dict()[spec.name].columns) == spec.all_column_names


def test_keys_are_unique(dataset):
    assert dataset.patients["patient_id"].is_unique
    assert dataset.patients["mrn"].is_unique
    assert dataset.encounters["encounter_id"].is_unique
    assert dataset.observations["observation_id"].is_unique


def test_referential_integrity(dataset):
    patient_ids = set(dataset.patients["patient_id"])
    encounter_ids = set(dataset.encounters["encounter_id"])

    assert set(dataset.encounters["patient_id"]) <= patient_ids
    assert set(dataset.observations["patient_id"]) <= patient_ids
    assert set(dataset.observations["encounter_id"]) <= encounter_ids


def test_every_patient_has_at_least_one_encounter(dataset):
    assert set(dataset.encounters["patient_id"]) == set(dataset.patients["patient_id"])


def test_encounters_end_after_they_start(dataset):
    assert (dataset.encounters["end_ts"] >= dataset.encounters["start_ts"]).all()


def test_partition_key_matches_the_event_year(dataset):
    assert (dataset.encounters["year"] == dataset.encounters["start_ts"].dt.year).all()
    assert (dataset.observations["year"] == dataset.observations["effective_ts"].dt.year).all()


def test_events_stay_inside_the_configured_window(dataset):
    starts = dataset.encounters["start_ts"]
    assert starts.min() >= pd.Timestamp(SMALL.start_date)
    assert starts.max() <= pd.Timestamp(SMALL.end_date) + pd.Timedelta(days=1)


def test_abnormal_flag_agrees_with_the_reference_range(dataset):
    obs = dataset.observations
    expected = pd.Series("N", index=obs.index)
    expected[obs["value_num"] < obs["reference_low"]] = "L"
    expected[obs["value_num"] > obs["reference_high"]] = "H"
    pd.testing.assert_series_equal(obs["abnormal_flag"], expected, check_names=False)


def test_readmission_label_is_plausible_and_not_degenerate(dataset):
    rate = dataset.encounters["readmitted_30d"].mean()
    assert 0.03 < rate < 0.45, f"implausible readmission rate: {rate:.1%}"


def test_readmission_correlates_with_length_of_stay(dataset):
    enc = dataset.encounters
    assert (
        enc.loc[enc["readmitted_30d"], "length_of_stay_days"].mean()
        > enc.loc[~enc["readmitted_30d"], "length_of_stay_days"].mean()
    )


def test_invalid_config_is_rejected():
    with pytest.raises(ValueError):
        generate(GenerationConfig(patients=0))
    with pytest.raises(ValueError):
        generate(GenerationConfig(start_date=date(2025, 1, 1), end_date=date(2024, 1, 1)))


def test_write_dataset_produces_a_hive_layout(dataset, tmp_path):
    written = write_dataset(dataset, tmp_path, fmt="parquet")

    assert (tmp_path / "patients" / "part-0000.parquet").exists()
    assert len(written["encounters"]) == dataset.encounters["year"].nunique()

    for path in written["encounters"]:
        assert path.parent.name.startswith("year=")

    # Partition columns belong in the path, not in the file.
    part = pd.read_parquet(written["encounters"][0])
    assert "year" not in part.columns


def test_partitions_on_disk_reflects_what_was_written(dataset, tmp_path):
    write_dataset(dataset, tmp_path, fmt="parquet")
    found = partitions_on_disk(tmp_path / "encounters", ENCOUNTERS)
    assert sorted(v["year"] for v in found) == sorted(
        str(y) for y in dataset.encounters["year"].unique()
    )
    assert partitions_on_disk(tmp_path / "patients", PATIENTS) == []


def test_csv_is_written_without_a_header(dataset, tmp_path):
    write_dataset(dataset, tmp_path, fmt="csv")
    first_line = (tmp_path / "patients" / "part-0000.csv").read_text().splitlines()[0]
    assert not first_line.startswith("patient_id")


def test_unknown_format_is_rejected(dataset, tmp_path):
    with pytest.raises(ValueError):
        write_dataset(dataset, tmp_path, fmt="avro")
