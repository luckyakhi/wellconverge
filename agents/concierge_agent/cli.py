"""Interactive front door for the demo.

    python -m concierge_agent

The Concierge Agent chats with the person, uses Gemini to build up a structured
onboarding intent, and — once it has everything required — hands that intent to the
Membership Ops Agent over A2A. The Ops Agent is a separate process (`python -m
membership_ops_agent`) that could just as easily be owned by a different team, written
in a different language, or moved to a different host: A2A is the interoperability
seam between them.
"""

import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from a2a.helpers import display_agent_card
from google import genai

from common.settings import GEMINI_MODEL, MEMBERSHIP_OPS_AGENT_URL
from concierge_agent.a2a_client import discover_agent, send_onboarding_intent
from concierge_agent.nlu import extract_intent

WELCOME = """\
WellConverge Concierge — tell me about yourself and what you'd like from your
wellness journey (e.g. "Hi, I'm Priya Shah, priya@example.com, I want to build
strength and sleep better, born 1990-04-12"). Type 'exit' to quit.
"""


async def run() -> None:
    print(WELCOME)
    print(f"Discovering the Membership Ops Agent at {MEMBERSHIP_OPS_AGENT_URL} ...")
    try:
        agent_card = await discover_agent(MEMBERSHIP_OPS_AGENT_URL)
    except Exception as exc:  # noqa: BLE001 - surface any discovery failure to the user
        print(
            f"Could not reach the Membership Ops Agent ({exc}). "
            f"Start it first with: python -m membership_ops_agent"
        )
        return

    display_agent_card(agent_card)

    client = genai.Client()
    conversation: list[dict] = []

    while True:
        user_text = input("\nyou > ").strip()
        if not user_text or user_text.lower() == "exit":
            break
        conversation.append({"role": "user", "content": user_text})

        intent = extract_intent(client, conversation, GEMINI_MODEL)
        if not intent.is_complete:
            still_needed = ", ".join(intent.missing_info)
            prompt = f"I still need your {still_needed} before I can register you."
            print(f"concierge > {prompt}")
            conversation.append({"role": "assistant", "content": prompt})
            continue

        print("concierge > Got it — handing this off to the Membership Ops Agent...")
        result = await send_onboarding_intent(agent_card, intent.as_a2a_payload())
        print(f"membership-ops-agent > {result}")
        conversation.append(
            {"role": "assistant", "content": "Onboarding request sent. Ready for the next member."}
        )


if __name__ == "__main__":
    asyncio.run(run())
