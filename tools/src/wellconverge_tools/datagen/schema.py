"""Dataset shape, declared once and reused by the generator and the catalog.

Keeping the column list in one place means a Glue table can never drift from the
Parquet/CSV files that back it — the failure mode that makes data catalogs
untrustworthy in the first place.
"""

from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True)
class Column:
    name: str
    glue_type: str
    comment: str = ""


@dataclass(frozen=True)
class TableSpec:
    name: str
    columns: list[Column]
    partition_keys: list[Column] = field(default_factory=list)
    description: str = ""

    @property
    def all_column_names(self) -> list[str]:
        return [c.name for c in self.columns] + [c.name for c in self.partition_keys]


YEAR_PARTITION = Column("year", "int", "Calendar year of the event; Hive-style partition key")

PATIENTS = TableSpec(
    name="patients",
    description="Synthetic patient master record. No real persons; safe to share.",
    columns=[
        Column("patient_id", "string", "Surrogate patient key (UUIDv4)"),
        Column("mrn", "string", "Medical record number, unique per patient"),
        Column("given_name", "string", "Synthetic first name"),
        Column("family_name", "string", "Synthetic last name"),
        Column("birth_date", "date", "Date of birth"),
        Column("age_years", "int", "Age as of the dataset generation date"),
        Column("sex", "string", "female | male | other"),
        Column("race", "string", "Self-reported race category"),
        Column("ethnicity", "string", "Hispanic or Latino | Not Hispanic or Latino"),
        Column("state", "string", "Two-letter US state code of residence"),
        Column("postal_code", "string", "5-digit postal code"),
        Column("primary_language", "string", "Preferred language for care"),
        Column("insurance_type", "string", "Medicare | Medicaid | Commercial | Self-pay"),
        Column("smoker", "boolean", "Current tobacco use flag"),
        Column("bmi", "double", "Body mass index, kg/m^2"),
        Column("chronic_conditions", "int", "Count of active chronic conditions"),
        Column("registered_at", "timestamp", "When the patient enrolled with WellConverge"),
    ],
)

ENCOUNTERS = TableSpec(
    name="encounters",
    description="Synthetic clinical encounters, partitioned by admission year.",
    columns=[
        Column("encounter_id", "string", "Surrogate encounter key (UUIDv4)"),
        Column("patient_id", "string", "FK -> patients.patient_id"),
        Column("encounter_class", "string", "inpatient | outpatient | emergency"),
        Column("department", "string", "Admitting department"),
        Column("start_ts", "timestamp", "Encounter start"),
        Column("end_ts", "timestamp", "Encounter end / discharge"),
        Column("length_of_stay_days", "double", "Discharge minus admission, in days"),
        Column("primary_diagnosis_code", "string", "ICD-10-CM code"),
        Column("primary_diagnosis_desc", "string", "Human-readable diagnosis"),
        Column("discharge_disposition", "string", "home | skilled nursing | transferred | expired"),
        Column("total_charge_usd", "double", "Billed amount before adjustments"),
        Column("readmitted_30d", "boolean", "ML label: unplanned readmission within 30 days"),
    ],
    partition_keys=[YEAR_PARTITION],
)

OBSERVATIONS = TableSpec(
    name="observations",
    description="Synthetic vitals and lab results, partitioned by observation year.",
    columns=[
        Column("observation_id", "string", "Surrogate observation key (UUIDv4)"),
        Column("patient_id", "string", "FK -> patients.patient_id"),
        Column("encounter_id", "string", "FK -> encounters.encounter_id"),
        Column("effective_ts", "timestamp", "When the measurement was taken"),
        Column("loinc_code", "string", "LOINC code for the measurement"),
        Column("observation_name", "string", "Human-readable measurement name"),
        Column("value_num", "double", "Measured value"),
        Column("unit", "string", "Unit of measure (UCUM)"),
        Column("reference_low", "double", "Lower bound of the normal range"),
        Column("reference_high", "double", "Upper bound of the normal range"),
        Column("abnormal_flag", "string", "L | N | H"),
    ],
    partition_keys=[YEAR_PARTITION],
)

TABLES: list[TableSpec] = [PATIENTS, ENCOUNTERS, OBSERVATIONS]
TABLES_BY_NAME: dict[str, TableSpec] = {t.name: t for t in TABLES}
