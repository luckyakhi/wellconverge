"""The bridge between the A2A protocol and WellConverge's real Membership REST API.

This agent has no LLM inside it — it's the "hands" of the multi-agent slice. It takes
the structured onboarding intent produced by the Concierge Agent (see
`concierge_agent/nlu.py`) and drives the actual `RegisterMemberUseCase` /
`CompleteOnboardingUseCase` through the same `/api/members` HTTP surface documented in
`docs/contexts/membership/spec.md` — nothing here bypasses the domain's invariants.
"""

import json

import httpx

from a2a.helpers import get_message_text, new_task_from_user_message, new_text_message, new_text_part
from a2a.server.agent_execution import AgentExecutor, RequestContext
from a2a.server.events import EventQueue
from a2a.server.tasks import TaskUpdater


class MembershipOpsAgentExecutor(AgentExecutor):
    """Executes the `register-and-onboard-member` skill against the live backend."""

    def __init__(self, membership_api_base_url: str) -> None:
        self._base_url = membership_api_base_url.rstrip("/")

    async def execute(self, context: RequestContext, event_queue: EventQueue) -> None:
        task = context.current_task
        if task is None:
            task = new_task_from_user_message(context.message)
            await event_queue.enqueue_event(task)

        updater = TaskUpdater(event_queue, task.id, task.context_id)
        await updater.start_work(new_text_message("Registering member with WellConverge..."))

        raw_intent = get_message_text(context.message)
        try:
            intent = json.loads(raw_intent)
        except json.JSONDecodeError:
            await updater.failed(
                new_text_message(f"Expected a JSON onboarding intent, got: {raw_intent!r}")
            )
            return

        missing = [f for f in ("full_name", "email", "goals") if not intent.get(f)]
        if missing:
            await updater.failed(
                new_text_message(f"Onboarding intent is missing required field(s): {', '.join(missing)}")
            )
            return

        try:
            async with httpx.AsyncClient(base_url=self._base_url, timeout=10.0) as client:
                member = await self._register_and_onboard(client, intent)
        except httpx.HTTPStatusError as exc:
            await updater.failed(
                new_text_message(
                    f"Membership API rejected the request ({exc.response.status_code}): "
                    f"{exc.response.text}"
                )
            )
            return
        except httpx.HTTPError as exc:
            await updater.failed(new_text_message(f"Could not reach the Membership API: {exc}"))
            return

        await updater.add_artifact(
            parts=[new_text_part(json.dumps(member), media_type="application/json")],
            name="member",
        )
        goals = ", ".join(member["goals"])
        await updater.complete(
            new_text_message(
                f"Welcome, {member['fullName']}! Member {member['id']} is now "
                f"{member['status']}. Goals: {goals}."
            )
        )

    async def _register_and_onboard(self, client: httpx.AsyncClient, intent: dict) -> dict:
        register_resp = await client.post(
            "/api/members",
            json={"email": intent["email"], "fullName": intent["full_name"]},
        )
        register_resp.raise_for_status()
        member_id = register_resp.json()["id"]

        onboard_resp = await client.post(
            f"/api/members/{member_id}/onboarding",
            json={"goals": intent["goals"], "dateOfBirth": intent.get("date_of_birth")},
        )
        onboard_resp.raise_for_status()
        return onboard_resp.json()

    async def cancel(self, context: RequestContext, event_queue: EventQueue) -> None:
        raise NotImplementedError("Cancel is not supported by the Membership Ops Agent.")
