"""Turns a prospective member's free-text message into a structured onboarding intent.

This is the only place an LLM is called directly (via the Gemini API's structured
output) — the Membership Ops Agent on the other side of the A2A call is deliberately a
plain deterministic wrapper around the REST API, so the LLM's job is scoped to natural
language understanding, not to touching the domain.
"""

from dataclasses import dataclass, field
from enum import Enum
from typing import Optional

from google import genai
from pydantic import BaseModel, Field

_VALID_GOALS = ["SLEEP_BETTER", "MOVE_MORE", "EAT_WELL", "STRESS_LESS", "BUILD_STRENGTH"]

_SYSTEM_PROMPT = f"""\
You are the intake step of a wellness membership concierge. Extract a structured \
onboarding intent from what the prospective member says, across the whole \
conversation so far.

Wellness goals must be mapped to exactly these codes: {", ".join(_VALID_GOALS)}. \
Infer the closest matching codes from natural language (e.g. "sleep better" -> \
SLEEP_BETTER, "get stronger" -> BUILD_STRENGTH, "eat healthier" -> EAT_WELL).

date_of_birth must be an ISO 8601 date (YYYY-MM-DD) or omitted if not mentioned — it \
is optional. full_name, email, and at least one goal are required. List any required \
field the user has not yet given you in missing_info; leave it empty once you have \
everything required."""


class WellnessGoal(str, Enum):
    SLEEP_BETTER = "SLEEP_BETTER"
    MOVE_MORE = "MOVE_MORE"
    EAT_WELL = "EAT_WELL"
    STRESS_LESS = "STRESS_LESS"
    BUILD_STRENGTH = "BUILD_STRENGTH"


class MissingField(str, Enum):
    FULL_NAME = "full_name"
    EMAIL = "email"
    GOALS = "goals"


class ExtractedIntent(BaseModel):
    """The response schema handed to Gemini — it will only ever return JSON shaped like this."""

    full_name: Optional[str] = None
    email: Optional[str] = None
    goals: list[WellnessGoal] = Field(default_factory=list)
    date_of_birth: Optional[str] = Field(
        default=None, description="ISO 8601 date, e.g. 1990-04-12, or omitted if not mentioned."
    )
    missing_info: list[MissingField] = Field(
        default_factory=list, description="Required fields still missing."
    )


@dataclass
class OnboardingIntent:
    full_name: str | None
    email: str | None
    goals: list[str] = field(default_factory=list)
    date_of_birth: str | None = None
    missing_info: list[str] = field(default_factory=list)

    @property
    def is_complete(self) -> bool:
        return not self.missing_info

    def as_a2a_payload(self) -> dict:
        return {
            "full_name": self.full_name,
            "email": self.email,
            "goals": self.goals,
            "date_of_birth": self.date_of_birth,
        }


def _to_gemini_contents(conversation: list[dict]) -> list[dict]:
    """Maps our {"role": "user"|"assistant", "content": str} turns to Gemini's
    Content/Part shape, where the model's own turns are role "model"."""
    return [
        {
            "role": "model" if turn["role"] == "assistant" else "user",
            "parts": [{"text": turn["content"]}],
        }
        for turn in conversation
    ]


def extract_intent(client: genai.Client, conversation: list[dict], model: str) -> OnboardingIntent:
    """Extract (or refine) the onboarding intent from the conversation so far.

    `conversation` is a list of `{"role": "user"|"assistant", "content": str}` turns —
    the concierge appends to this as it asks follow-up questions for missing fields.
    """
    response = client.models.generate_content(
        model=model,
        contents=_to_gemini_contents(conversation),
        config={
            "system_instruction": _SYSTEM_PROMPT,
            "response_format": {
                "type": "text",
                "mime_type": "application/json",
                "schema": ExtractedIntent.model_json_schema(),
            },
        },
    )
    data = ExtractedIntent.model_validate_json(response.text)
    return OnboardingIntent(
        full_name=data.full_name,
        email=data.email,
        goals=[g.value for g in data.goals],
        date_of_birth=data.date_of_birth,
        missing_info=[m.value for m in data.missing_info],
    )
