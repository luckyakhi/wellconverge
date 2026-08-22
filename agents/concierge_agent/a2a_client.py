"""A2A client half of the concierge: discovers the Membership Ops Agent by its Agent
Card and hands it a completed onboarding intent as an A2A task.
"""

import json

import httpx
from a2a.client import A2ACardResolver, ClientConfig, create_client
from a2a.helpers import get_stream_response_text, new_text_message
from a2a.types import AgentCard, Role, SendMessageRequest


async def discover_agent(base_url: str) -> AgentCard:
    async with httpx.AsyncClient() as httpx_client:
        resolver = A2ACardResolver(httpx_client=httpx_client, base_url=base_url)
        return await resolver.get_agent_card()


async def send_onboarding_intent(agent_card: AgentCard, intent_payload: dict) -> str:
    """Sends the intent (as JSON text, per the Ops Agent's declared skill) and returns
    the concatenated text of everything the Ops Agent reported back — status updates
    and the final artifact/completion message."""
    client = await create_client(agent=agent_card, client_config=ClientConfig(streaming=False))
    try:
        message = new_text_message(json.dumps(intent_payload), role=Role.ROLE_USER)
        request = SendMessageRequest(message=message)

        chunks = []
        async for chunk in client.send_message(request):
            text = get_stream_response_text(chunk)
            if text:
                chunks.append(text)
        return "\n".join(chunks)
    finally:
        await client.close()
