"""A2A server: publishes the Membership Ops Agent and exposes its Agent Card.

Run with:  python -m membership_ops_agent   (from the agents/ directory)
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import uvicorn
from a2a.server.request_handlers import DefaultRequestHandler
from a2a.server.routes import create_agent_card_routes, create_jsonrpc_routes
from a2a.server.tasks import InMemoryTaskStore
from a2a.types import AgentCapabilities, AgentCard, AgentInterface, AgentSkill
from starlette.applications import Starlette

from common.settings import MEMBERSHIP_API_BASE_URL, MEMBERSHIP_OPS_AGENT_HOST, MEMBERSHIP_OPS_AGENT_PORT
from membership_ops_agent.agent_executor import MembershipOpsAgentExecutor

if __name__ == "__main__":
    agent_url = f"http://{MEMBERSHIP_OPS_AGENT_HOST}:{MEMBERSHIP_OPS_AGENT_PORT}"

    skill = AgentSkill(
        id="register-and-onboard-member",
        name="Register and onboard a member",
        description=(
            "Given a structured intent (full name, email, wellness goals, optional date of "
            "birth) as a JSON text message, registers a new WellConverge member and "
            "completes their onboarding via the real Membership REST API."
        ),
        input_modes=["text/plain"],
        output_modes=["text/plain", "application/json"],
        tags=["membership", "onboarding", "wellconverge"],
        examples=[
            '{"full_name": "Priya Shah", "email": "priya@example.com", '
            '"goals": ["BUILD_STRENGTH", "SLEEP_BETTER"], "date_of_birth": "1990-04-12"}'
        ],
    )

    agent_card = AgentCard(
        name="Membership Ops Agent",
        description="Owns registration and onboarding against the WellConverge Membership context.",
        version="0.1.0",
        default_input_modes=["text/plain"],
        default_output_modes=["text/plain", "application/json"],
        capabilities=AgentCapabilities(streaming=False),
        supported_interfaces=[
            AgentInterface(protocol_binding="JSONRPC", url=agent_url, protocol_version="1.0")
        ],
        skills=[skill],
    )

    request_handler = DefaultRequestHandler(
        agent_executor=MembershipOpsAgentExecutor(MEMBERSHIP_API_BASE_URL),
        task_store=InMemoryTaskStore(),
        agent_card=agent_card,
    )

    routes = []
    routes.extend(create_agent_card_routes(agent_card))
    routes.extend(create_jsonrpc_routes(request_handler, "/"))

    app = Starlette(routes=routes)
    print(f"Membership Ops Agent listening on {agent_url}")
    print(f"Agent Card:               {agent_url}/.well-known/agent-card.json")
    print(f"Proxying to Membership API: {MEMBERSHIP_API_BASE_URL}")
    uvicorn.run(app, host=MEMBERSHIP_OPS_AGENT_HOST, port=MEMBERSHIP_OPS_AGENT_PORT)
