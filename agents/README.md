# Agent-to-Agent (A2A) — Membership onboarding concierge

A small, separate learning slice: two independent AI agent **processes** that talk to
each other over the open [Agent2Agent (A2A) protocol](https://a2a-protocol.org) to
register and onboard a WellConverge member. It sits next to `backend/` and
`frontend/` rather than inside either — it's a client of the Membership REST API
(`docs/contexts/membership/spec.md`), not a new bounded context, so it doesn't touch
the domain, application, or adapters layers, and there's no new Gherkin spec for it.

## What A2A actually is

MCP (Model Context Protocol) connects *one* agent to *tools and data*. A2A solves a
different problem: connecting *agent to agent*, potentially built by different teams,
in different languages, on different hosts, without either one knowing the other's
internals — only its advertised capabilities. It's an open spec (Linux Foundation,
contributions from Google, AWS, Microsoft, Salesforce, and others), analogous to how
HTTP/REST let independently-built web services interoperate.

The core pieces, all visible in this slice:

| Concept | What it is | Where it shows up here |
|---|---|---|
| **Agent Card** | A JSON document at `/.well-known/agent-card.json` advertising an agent's identity, skills, and how to reach it | `membership_ops_agent/__main__.py` — declares the `register-and-onboard-member` skill |
| **Discovery** | A client fetches the other agent's Agent Card before talking to it — no hardcoded API contract | `concierge_agent/a2a_client.py::discover_agent` |
| **Task** | The unit of work created by a message; moves through states (`submitted` → `working` → `completed`/`failed`) | `MembershipOpsAgentExecutor.execute` drives it via `TaskUpdater` |
| **Message / Part** | What's actually sent — a message has one or more parts (text, structured data, files) | The Concierge sends the extracted intent as a JSON text part |
| **Artifact** | A durable output a task produces (as opposed to a transient status update) | The Ops Agent attaches the created `Member` as a JSON artifact |
| **Agent Executor** | The bridge between the protocol and your actual logic | `membership_ops_agent/agent_executor.py` |
| **Transport** | JSON-RPC (also gRPC/REST) over HTTP, served here via Starlette + uvicorn | `create_jsonrpc_routes` in `__main__.py` |

Notice what A2A *doesn't* require: the Concierge Agent never imports the Ops Agent's
code, never knows it's Python, and never knows it calls a Spring Boot REST API
underneath. It only knows the Agent Card's contract. Swap the Ops Agent for a Java
implementation of the same skill and the Concierge wouldn't need to change.

## The use case

**Concierge Agent** (`concierge_agent/`) — the only place an LLM is called. A person
types free text ("Hi, I'm Priya Shah, priya@example.com, I want to build strength and
sleep better, born 1990-04-12"). Gemini (`google-genai` SDK + a Pydantic response
schema, see `nlu.py`) extracts a structured onboarding intent — `full_name`, `email`,
`goals[]`, optional `date_of_birth` — and the concierge asks follow-up questions for
anything missing. Once complete, it's handed to the Ops Agent as an **A2A task**.

**Membership Ops Agent** (`membership_ops_agent/`) — an A2A server with no LLM inside
it at all. Its `AgentExecutor` takes the structured intent and drives the *real*
`POST /api/members` then `POST /api/members/{id}/onboarding` endpoints — the same
`RegisterMemberUseCase` / `CompleteOnboardingUseCase` the React UI uses. It reports
task progress (`working` → `completed`/`failed`) and returns the created member as an
artifact.

```
person (free text)
      │
      ▼
┌─────────────────────┐   A2A task: JSON onboarding intent   ┌──────────────────────┐
│   Concierge Agent    │ ────────────────────────────────────▶│ Membership Ops Agent │
│  (Gemini does NLU)   │◀──────────────────────────────────── │ (deterministic REST  │
└─────────────────────┘   A2A task: status + member artifact  │  wrapper, no LLM)    │
                                                                └──────────┬───────────┘
                                                                           │ HTTP
                                                                           ▼
                                                                POST /api/members
                                                                POST /api/members/{id}/onboarding
                                                                (the real Spring Boot backend)
```

## Running it

Requires **Python 3.10+** (the `a2a-sdk` minimum) — check with `python3 --version`
before creating the venv; if your default `python3` is older, point `venv` at a newer
interpreter (e.g. `python3.12 -m venv .venv`) or install one via `brew install
python@3.12`.

```bash
cd agents
python3 -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt

cp .env.example .env             # then edit .env: set GEMINI_API_KEY
```

Get a free `GEMINI_API_KEY` at [aistudio.google.com/apikey](https://aistudio.google.com/apikey) —
no billing setup required.

1. **Start the real backend** (from the repo root, separate terminal):
   ```bash
   docker compose -f deploy/docker-compose.yml up backend db
   ```
2. **Start the Membership Ops Agent** (A2A server, from `agents/`, separate terminal):
   ```bash
   python -m membership_ops_agent
   ```
   Visit `http://127.0.0.1:9999/.well-known/agent-card.json` to see its Agent Card.
3. **Talk to the Concierge Agent** (from `agents/`):
   ```bash
   python -m concierge_agent
   ```
   Try: `Hi, I'm Priya Shah, priya@example.com. I want to build strength and sleep
   better. Born 1990-04-12.`

## Why this isn't in `backend/`

`CLAUDE.md`'s SDD loop (spec → Gherkin → domain → ports → adapters) governs how the
*Membership bounded context itself* grows. This slice is a consumer sitting outside
that boundary — like the React frontend, it only talks to `/api/members` over HTTP.
If a future iteration wants Membership to *publish* onboarding intents to other
agents as a first-class capability (vs. a REST client wrapping it), that would earn
its own spec update and probably an ADR.
