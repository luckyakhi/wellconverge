"""Minimal Athena runner, used to prove the catalog actually resolves.

Registering a Glue table is not evidence that anything is queryable — the serde,
the partition locations and the column types all have to line up. Running one
real query is the cheapest end-to-end proof.
"""

from __future__ import annotations

import time

import boto3

from ..config import ProvisionedSettings

TERMINAL = ("SUCCEEDED", "FAILED", "CANCELLED")


def run_query(
    session: boto3.Session, settings: ProvisionedSettings, sql: str, timeout_s: float = 120.0
) -> list[dict[str, str]]:
    athena = session.client("athena")
    execution = athena.start_query_execution(
        QueryString=sql,
        QueryExecutionContext={"Database": settings.glue_database},
        WorkGroup=settings.athena_workgroup,
        ResultConfiguration={"OutputLocation": settings.athena_output_uri},
    )
    query_id = execution["QueryExecutionId"]

    deadline = time.monotonic() + timeout_s
    state = "QUEUED"
    while time.monotonic() < deadline:
        status = athena.get_query_execution(QueryExecutionId=query_id)["QueryExecution"]["Status"]
        state = status["State"]
        if state in TERMINAL:
            break
        time.sleep(1.5)
    else:
        raise TimeoutError(f"Athena query {query_id} still {state} after {timeout_s}s")

    if state != "SUCCEEDED":
        reason = status.get("StateChangeReason", "no reason given")
        raise RuntimeError(f"Athena query {state}: {reason}")

    result = athena.get_query_results(QueryExecutionId=query_id)
    rows = result["ResultSet"]["Rows"]
    if not rows:
        return []

    header = [c.get("VarCharValue", "") for c in rows[0]["Data"]]
    return [
        {h: cell.get("VarCharValue") for h, cell in zip(header, row["Data"])}
        for row in rows[1:]
    ]
