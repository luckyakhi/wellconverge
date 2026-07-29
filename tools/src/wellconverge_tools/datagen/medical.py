"""Synthetic medical data generator.

Everything here is fabricated from seeded RNG — there is no real patient data in
this repo and none should ever be added. The generator deliberately bakes a
*learnable* signal into `readmitted_30d` (age, length of stay, abnormal labs,
encounter class and chronic burden all push the risk up) so the ML baseline in
`analysis/readmission.py` has something real to find instead of pure noise.
"""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from datetime import date, datetime, timedelta

import numpy as np
import pandas as pd

from .schema import ENCOUNTERS, OBSERVATIONS, PATIENTS

GIVEN_NAMES = [
    "Aarav", "Ana", "Beatriz", "Caleb", "Chen", "Divya", "Elena", "Farhan",
    "Grace", "Hiroshi", "Imani", "Jonas", "Kavya", "Lucas", "Maya", "Nadia",
    "Omar", "Priya", "Quinn", "Rohan", "Sofia", "Tariq", "Uma", "Viktor",
    "Wren", "Ximena", "Yusuf", "Zara", "Isabel", "Marcus", "Leah", "Dmitri",
]
FAMILY_NAMES = [
    "Alvarez", "Baptiste", "Chowdhury", "Delgado", "Eriksson", "Ferrari",
    "Gonzalez", "Haddad", "Iyer", "Johansson", "Kowalski", "Lindqvist",
    "Mbeki", "Nakamura", "Okafor", "Petrov", "Quintero", "Rasmussen",
    "Silva", "Tanaka", "Ueda", "Vasquez", "Wagner", "Xu", "Yamamoto", "Zhao",
]
STATES = ["CA", "TX", "NY", "FL", "IL", "PA", "OH", "GA", "NC", "MI", "WA", "MA"]
LANGUAGES = ["English", "Spanish", "Mandarin", "Hindi", "Arabic", "Portuguese"]
RACES = [
    "White", "Black or African American", "Asian",
    "American Indian or Alaska Native", "Native Hawaiian or Pacific Islander",
    "Other",
]
ETHNICITIES = ["Hispanic or Latino", "Not Hispanic or Latino"]
INSURANCE = ["Medicare", "Medicaid", "Commercial", "Self-pay"]
DEPARTMENTS = [
    "Cardiology", "Endocrinology", "Pulmonology", "Internal Medicine",
    "Nephrology", "Orthopedics", "Emergency", "Oncology",
]
DISPOSITIONS = ["home", "home health", "skilled nursing", "transferred", "expired"]

# (ICD-10-CM code, description, base 30-day readmission risk contribution)
DIAGNOSES = [
    ("I50.9", "Heart failure, unspecified", 0.85),
    ("J44.1", "COPD with acute exacerbation", 0.75),
    ("E11.65", "Type 2 diabetes mellitus with hyperglycemia", 0.55),
    ("N18.4", "Chronic kidney disease, stage 4", 0.70),
    ("I21.4", "Non-ST elevation myocardial infarction", 0.60),
    ("J18.9", "Pneumonia, unspecified organism", 0.45),
    ("A41.9", "Sepsis, unspecified organism", 0.80),
    ("M17.11", "Unilateral primary osteoarthritis, right knee", 0.10),
    ("K92.2", "Gastrointestinal hemorrhage, unspecified", 0.50),
    ("Z00.00", "General adult medical examination", 0.02),
]

# (LOINC, name, unit, ref_low, ref_high, healthy_mean, healthy_sd)
PANEL = [
    ("8867-4", "Heart rate", "/min", 60.0, 100.0, 76.0, 11.0),
    ("8480-6", "Systolic blood pressure", "mm[Hg]", 90.0, 120.0, 122.0, 16.0),
    ("8462-4", "Diastolic blood pressure", "mm[Hg]", 60.0, 80.0, 76.0, 10.0),
    ("2160-0", "Creatinine [Mass/volume] in Serum", "mg/dL", 0.6, 1.3, 1.0, 0.35),
    ("4548-4", "Hemoglobin A1c", "%", 4.0, 5.7, 5.6, 1.1),
    ("2345-7", "Glucose [Mass/volume] in Serum", "mg/dL", 70.0, 99.0, 102.0, 28.0),
    ("718-7", "Hemoglobin [Mass/volume] in Blood", "g/dL", 12.0, 17.5, 13.8, 1.6),
    ("2951-2", "Sodium [Moles/volume] in Serum", "mmol/L", 135.0, 145.0, 139.0, 3.2),
    ("6690-2", "Leukocytes [#/volume] in Blood", "10*3/uL", 4.5, 11.0, 7.6, 2.6),
    ("2093-3", "Cholesterol [Mass/volume] in Serum", "mg/dL", 0.0, 200.0, 192.0, 38.0),
]


@dataclass(frozen=True)
class GenerationConfig:
    patients: int = 2_000
    seed: int = 20260728
    start_date: date = date(2023, 1, 1)
    end_date: date = date(2026, 6, 30)
    max_encounters_per_patient: int = 6
    observations_per_encounter: int = 4


@dataclass(frozen=True)
class Dataset:
    patients: pd.DataFrame
    encounters: pd.DataFrame
    observations: pd.DataFrame

    def as_dict(self) -> dict[str, pd.DataFrame]:
        return {
            PATIENTS.name: self.patients,
            ENCOUNTERS.name: self.encounters,
            OBSERVATIONS.name: self.observations,
        }

    def summary(self) -> str:
        readmit_rate = float(self.encounters["readmitted_30d"].mean()) if len(self.encounters) else 0.0
        return (
            f"{len(self.patients):,} patients | {len(self.encounters):,} encounters | "
            f"{len(self.observations):,} observations | 30-day readmission rate "
            f"{readmit_rate:.1%}"
        )


def _sigmoid(x: np.ndarray) -> np.ndarray:
    return 1.0 / (1.0 + np.exp(-x))


def _uuids(rng: np.random.Generator, n: int) -> list[str]:
    """Deterministic UUIDv4-shaped ids, driven by the seeded RNG rather than os.urandom."""
    raw = rng.integers(0, 256, size=(n, 16), dtype=np.uint8)
    return [str(uuid.UUID(bytes=bytes(row), version=4)) for row in raw]


def _generate_patients(rng: np.random.Generator, cfg: GenerationConfig) -> pd.DataFrame:
    n = cfg.patients
    # Age skews older than the general population: this is a care-management cohort.
    ages = np.clip(rng.gamma(shape=9.0, scale=6.5, size=n), 18, 98).astype(int)
    as_of = cfg.end_date

    birth_dates = [
        as_of - timedelta(days=int(age * 365.25) + int(offset))
        for age, offset in zip(ages, rng.integers(0, 365, size=n))
    ]

    # Older patients are far likelier to be on Medicare; keep the mix plausible.
    insurance_p = np.where(
        ages[:, None] >= 65,
        np.array([0.72, 0.10, 0.15, 0.03]),
        np.array([0.05, 0.22, 0.66, 0.07]),
    )
    insurance = [
        rng.choice(INSURANCE, p=row / row.sum()) for row in insurance_p
    ]

    chronic = rng.poisson(lam=0.6 + ages / 45.0, size=n).clip(0, 8)

    return pd.DataFrame(
        {
            "patient_id": _uuids(rng, n),
            "mrn": [f"MRN{i:07d}" for i in rng.permutation(n) + 1_000_000],
            "given_name": rng.choice(GIVEN_NAMES, size=n),
            "family_name": rng.choice(FAMILY_NAMES, size=n),
            "birth_date": birth_dates,
            "age_years": ages,
            "sex": rng.choice(["female", "male", "other"], size=n, p=[0.505, 0.487, 0.008]),
            "race": rng.choice(RACES, size=n, p=[0.58, 0.14, 0.09, 0.02, 0.01, 0.16]),
            "ethnicity": rng.choice(ETHNICITIES, size=n, p=[0.19, 0.81]),
            "state": rng.choice(STATES, size=n),
            "postal_code": [f"{z:05d}" for z in rng.integers(10_000, 99_999, size=n)],
            "primary_language": rng.choice(LANGUAGES, size=n, p=[0.72, 0.13, 0.05, 0.04, 0.03, 0.03]),
            "insurance_type": insurance,
            "smoker": rng.random(n) < 0.17,
            "bmi": np.round(np.clip(rng.normal(28.4, 6.1, size=n), 15.0, 58.0), 1),
            "chronic_conditions": chronic,
            "registered_at": [
                datetime.combine(cfg.start_date, datetime.min.time())
                - timedelta(days=int(d), hours=int(h))
                for d, h in zip(rng.integers(0, 900, size=n), rng.integers(0, 24, size=n))
            ],
        }
    )


def _generate_encounters(
    rng: np.random.Generator, cfg: GenerationConfig, patients: pd.DataFrame
) -> pd.DataFrame:
    span_days = (cfg.end_date - cfg.start_date).days
    # Sicker patients show up more often.
    counts = 1 + rng.poisson(
        lam=0.4 + patients["chronic_conditions"].to_numpy() * 0.35, size=len(patients)
    )
    counts = np.clip(counts, 1, cfg.max_encounters_per_patient)

    patient_idx = np.repeat(np.arange(len(patients)), counts)
    n = len(patient_idx)

    ages = patients["age_years"].to_numpy()[patient_idx]
    chronic = patients["chronic_conditions"].to_numpy()[patient_idx]
    smoker = patients["smoker"].to_numpy()[patient_idx]
    insurance = patients["insurance_type"].to_numpy()[patient_idx]

    encounter_class = rng.choice(
        ["inpatient", "outpatient", "emergency"], size=n, p=[0.28, 0.57, 0.15]
    )
    dx_idx = rng.integers(0, len(DIAGNOSES), size=n)
    dx_codes = np.array([DIAGNOSES[i][0] for i in dx_idx])
    dx_desc = np.array([DIAGNOSES[i][1] for i in dx_idx])
    dx_risk = np.array([DIAGNOSES[i][2] for i in dx_idx])

    start_offsets = rng.integers(0, span_days, size=n)
    start_ts = [
        datetime.combine(cfg.start_date, datetime.min.time())
        + timedelta(days=int(d), hours=int(h), minutes=int(m))
        for d, h, m in zip(start_offsets, rng.integers(0, 24, size=n), rng.integers(0, 60, size=n))
    ]

    base_los = np.where(
        encounter_class == "inpatient",
        rng.gamma(2.2, 2.1, size=n),
        np.where(encounter_class == "emergency", rng.gamma(1.3, 0.5, size=n), rng.gamma(1.0, 0.08, size=n)),
    )
    los = np.round(np.clip(base_los + chronic * 0.25, 0.05, 45.0), 2)
    end_ts = [s + timedelta(days=float(d)) for s, d in zip(start_ts, los)]

    # The label: a logistic model over genuine drivers plus noise.
    logit = (
        -3.30
        + 0.022 * (ages - 55)
        + 0.115 * los
        + 0.240 * chronic
        + 1.150 * dx_risk
        + 0.320 * (encounter_class == "inpatient")
        + 0.480 * (encounter_class == "emergency")
        + 0.260 * smoker
        + 0.300 * (insurance == "Medicaid")
        + 0.180 * (insurance == "Self-pay")
        + rng.normal(0.0, 0.45, size=n)
    )
    readmitted = rng.random(n) < _sigmoid(logit)

    charge_base = np.where(
        encounter_class == "inpatient", 9_400.0,
        np.where(encounter_class == "emergency", 2_600.0, 480.0),
    )
    charges = np.round(charge_base * (1 + los * 0.28) * rng.lognormal(0.0, 0.30, size=n), 2)

    disposition = np.where(
        encounter_class == "outpatient",
        "home",
        rng.choice(DISPOSITIONS, size=n, p=[0.62, 0.14, 0.13, 0.08, 0.03]),
    )

    return pd.DataFrame(
        {
            "encounter_id": _uuids(rng, n),
            "patient_id": patients["patient_id"].to_numpy()[patient_idx],
            "encounter_class": encounter_class,
            "department": rng.choice(DEPARTMENTS, size=n),
            "start_ts": start_ts,
            "end_ts": end_ts,
            "length_of_stay_days": los,
            "primary_diagnosis_code": dx_codes,
            "primary_diagnosis_desc": dx_desc,
            "discharge_disposition": disposition,
            "total_charge_usd": charges,
            "readmitted_30d": readmitted,
            "year": [ts.year for ts in start_ts],
        }
    )


def _generate_observations(
    rng: np.random.Generator, cfg: GenerationConfig, patients: pd.DataFrame, encounters: pd.DataFrame
) -> pd.DataFrame:
    per = cfg.observations_per_encounter
    enc_idx = np.repeat(np.arange(len(encounters)), per)
    n = len(enc_idx)

    age_by_patient = patients.set_index("patient_id")["age_years"]
    chronic_by_patient = patients.set_index("patient_id")["chronic_conditions"]
    enc_patients = encounters["patient_id"].to_numpy()[enc_idx]
    ages = age_by_patient.loc[enc_patients].to_numpy()
    chronic = chronic_by_patient.loc[enc_patients].to_numpy()
    readmitted = encounters["readmitted_30d"].to_numpy()[enc_idx]

    panel_idx = rng.integers(0, len(PANEL), size=n)
    loinc = np.array([PANEL[i][0] for i in panel_idx])
    names = np.array([PANEL[i][1] for i in panel_idx])
    units = np.array([PANEL[i][2] for i in panel_idx])
    ref_low = np.array([PANEL[i][3] for i in panel_idx])
    ref_high = np.array([PANEL[i][4] for i in panel_idx])
    mean = np.array([PANEL[i][5] for i in panel_idx])
    sd = np.array([PANEL[i][6] for i in panel_idx])

    # Sicker / older / soon-to-be-readmitted patients drift out of range.
    drift = 1.0 + 0.004 * (ages - 55) + 0.035 * chronic + 0.070 * readmitted
    values = np.round(np.clip(rng.normal(mean * drift, sd * 1.15), 0.1, None), 2)

    starts = encounters["start_ts"].to_numpy()[enc_idx]
    effective = [
        pd.Timestamp(s) + pd.Timedelta(hours=float(h))
        for s, h in zip(starts, rng.uniform(0.5, 30.0, size=n))
    ]

    flag = np.where(values < ref_low, "L", np.where(values > ref_high, "H", "N"))

    return pd.DataFrame(
        {
            "observation_id": _uuids(rng, n),
            "patient_id": enc_patients,
            "encounter_id": encounters["encounter_id"].to_numpy()[enc_idx],
            "effective_ts": effective,
            "loinc_code": loinc,
            "observation_name": names,
            "value_num": values,
            "unit": units,
            "reference_low": ref_low,
            "reference_high": ref_high,
            "abnormal_flag": flag,
            "year": [ts.year for ts in effective],
        }
    )


def generate(cfg: GenerationConfig | None = None) -> Dataset:
    """Generate a full, referentially consistent synthetic dataset."""
    cfg = cfg or GenerationConfig()
    if cfg.patients < 1:
        raise ValueError("patients must be >= 1")
    if cfg.end_date <= cfg.start_date:
        raise ValueError("end_date must be after start_date")

    rng = np.random.default_rng(cfg.seed)
    patients = _generate_patients(rng, cfg)
    encounters = _generate_encounters(rng, cfg, patients)
    observations = _generate_observations(rng, cfg, patients, encounters)

    patients["birth_date"] = pd.to_datetime(patients["birth_date"]).dt.date
    for frame, col in ((encounters, "start_ts"), (encounters, "end_ts"), (observations, "effective_ts")):
        frame[col] = pd.to_datetime(frame[col])
    patients["registered_at"] = pd.to_datetime(patients["registered_at"])

    return Dataset(
        patients=patients[PATIENTS.all_column_names],
        encounters=encounters[ENCOUNTERS.all_column_names],
        observations=observations[OBSERVATIONS.all_column_names],
    )
