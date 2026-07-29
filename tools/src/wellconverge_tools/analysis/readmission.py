"""Baseline 30-day readmission model.

Deliberately a *baseline*: logistic regression on a feature table you can read
end to end. Its job is to give any future model something honest to beat, and to
exercise the whole path (catalog -> dataframe -> features -> metric).
"""

from __future__ import annotations

from dataclasses import dataclass

import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import average_precision_score, roc_auc_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

NUMERIC_FEATURES = [
    "age_years",
    "bmi",
    "chronic_conditions",
    "length_of_stay_days",
    "total_charge_usd",
    "abnormal_observations",
]
CATEGORICAL_FEATURES = [
    "sex",
    "insurance_type",
    "encounter_class",
    "department",
    "primary_diagnosis_code",
    "smoker",
]
LABEL = "readmitted_30d"


def build_feature_table(
    patients: pd.DataFrame, encounters: pd.DataFrame, observations: pd.DataFrame
) -> pd.DataFrame:
    """One row per encounter: patient attributes + encounter facts + lab abnormality count."""
    abnormal = (
        observations.assign(is_abnormal=observations["abnormal_flag"].ne("N").astype(int))
        .groupby("encounter_id", as_index=False)["is_abnormal"]
        .sum()
        .rename(columns={"is_abnormal": "abnormal_observations"})
    )

    features = (
        encounters.merge(
            patients[["patient_id", "age_years", "sex", "bmi", "smoker",
                      "chronic_conditions", "insurance_type"]],
            on="patient_id",
            how="left",
            validate="many_to_one",
        )
        .merge(abnormal, on="encounter_id", how="left", validate="one_to_one")
    )
    features["abnormal_observations"] = features["abnormal_observations"].fillna(0).astype(int)
    return features


def _pipeline() -> Pipeline:
    return Pipeline(
        [
            (
                "prep",
                ColumnTransformer(
                    [
                        ("num", StandardScaler(), NUMERIC_FEATURES),
                        (
                            "cat",
                            OneHotEncoder(handle_unknown="ignore", min_frequency=10),
                            CATEGORICAL_FEATURES,
                        ),
                    ]
                ),
            ),
            (
                "clf",
                LogisticRegression(max_iter=2_000, class_weight="balanced", random_state=42),
            ),
        ]
    )


@dataclass(frozen=True)
class TrainingResult:
    model: Pipeline
    roc_auc: float
    average_precision: float
    positive_rate: float
    n_train: int
    n_test: int

    def __str__(self) -> str:
        return (
            f"ROC-AUC {self.roc_auc:.3f} | PR-AUC {self.average_precision:.3f} | "
            f"positives {self.positive_rate:.1%} | train {self.n_train:,} / test {self.n_test:,}"
        )


def train_baseline(features: pd.DataFrame, test_size: float = 0.25, seed: int = 42) -> TrainingResult:
    X = features[NUMERIC_FEATURES + CATEGORICAL_FEATURES]
    y = features[LABEL].astype(int)

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=test_size, random_state=seed, stratify=y
    )

    model = _pipeline().fit(X_train, y_train)
    scores = model.predict_proba(X_test)[:, 1]

    return TrainingResult(
        model=model,
        roc_auc=float(roc_auc_score(y_test, scores)),
        average_precision=float(average_precision_score(y_test, scores)),
        positive_rate=float(y.mean()),
        n_train=len(X_train),
        n_test=len(X_test),
    )


def top_coefficients(result: TrainingResult, n: int = 12) -> pd.DataFrame:
    """Largest-magnitude coefficients — a sanity check that the model learned the obvious."""
    prep = result.model.named_steps["prep"]
    clf = result.model.named_steps["clf"]
    return (
        pd.DataFrame(
            {"feature": prep.get_feature_names_out(), "coefficient": clf.coef_[0]}
        )
        .assign(magnitude=lambda d: d["coefficient"].abs())
        .sort_values("magnitude", ascending=False)
        .head(n)
        .drop(columns="magnitude")
        .reset_index(drop=True)
    )
