"""Environment-driven configuration shared by both agents.

Loads `.env` (if present) so `GEMINI_API_KEY` and friends can live outside the shell.
The `google-genai` SDK itself also reads `GEMINI_API_KEY` / `GOOGLE_API_KEY` directly —
this module doesn't need to pass a key around, just the model name and the URLs the two
agents use to find each other and the real Membership REST API.
"""

import os

from dotenv import load_dotenv

load_dotenv()

GEMINI_MODEL = os.environ.get("GEMINI_MODEL", "gemini-2.5-flash")

MEMBERSHIP_API_BASE_URL = os.environ.get("MEMBERSHIP_API_BASE_URL", "http://localhost:8080")

MEMBERSHIP_OPS_AGENT_HOST = os.environ.get("MEMBERSHIP_OPS_AGENT_HOST", "127.0.0.1")
MEMBERSHIP_OPS_AGENT_PORT = int(os.environ.get("MEMBERSHIP_OPS_AGENT_PORT", "9999"))
MEMBERSHIP_OPS_AGENT_URL = os.environ.get(
    "MEMBERSHIP_OPS_AGENT_URL",
    f"http://{MEMBERSHIP_OPS_AGENT_HOST}:{MEMBERSHIP_OPS_AGENT_PORT}",
)
