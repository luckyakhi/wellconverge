"""boto3 session creation with an explicit account guard.

Every write path in this package goes through `guarded_session`, so a stale
`AWS_PROFILE` can never quietly land synthetic medical data in someone else's
account.
"""

from __future__ import annotations

import boto3
from botocore.exceptions import ClientError, NoCredentialsError

from ..config import ProvisionedSettings


class AccountMismatch(RuntimeError):
    pass


class MissingCredentials(RuntimeError):
    pass


def caller_identity(session: boto3.Session) -> dict[str, str]:
    try:
        return session.client("sts").get_caller_identity()
    except (NoCredentialsError, ClientError) as exc:
        raise MissingCredentials(
            "No usable AWS credentials. Configure them with `aws configure`, or export "
            "AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY (plus AWS_SESSION_TOKEN if temporary)."
        ) from exc


def guarded_session(settings: ProvisionedSettings) -> boto3.Session:
    """Return a session, refusing to proceed against an unexpected account."""
    session = boto3.Session(region_name=settings.region)
    identity = caller_identity(session)

    if identity["Account"] != settings.account_id:
        raise AccountMismatch(
            f"Credentials resolve to account {identity['Account']} "
            f"({identity['Arn']}), but this run targets {settings.account_id}. "
            "Set WC_AWS_ACCOUNT_ID or switch profiles — refusing to write."
        )
    return session
