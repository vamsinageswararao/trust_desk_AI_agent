# TrustDesk: AI Support Operations Agent

TrustDesk helps a support team answer customers quickly **without letting an AI, a customer, or a retrieved document
bypass company policy**. For each ticket it triages (category, priority, escalation), retrieves policy from a knowledge
base, drafts a reply that **cites** its sources, recommends actions, and requires a **human to approve** sensitive ones.
Every AI run leaves a trace, and an evaluation runner measures quality and safety on the provided cases.

The AI model only writes text. **The application owns every safety decision**: evidence selection, citations, output
scanning, tool permissions, approvals, and idempotency. Customer text, KB text and model output are all untrusted data.

## Stack

Java 21, Spring Boot 3.3, Spring Data JPA/Hibernate, SQLite (local), Spring Security (demo bearer tokens),
Jakarta Validation, Maven, JUnit 5 + Spring Boot Test + MockMvc. No external services required:
the default AI provider is a **deterministic offline mock**; an OpenAI-compatible adapter (Groq, Ollama, ...) is optional.

## Quick start

Prerequisites: JDK 21, Maven 3.9+.

```powershell
# Windows PowerShell, from the repository root
$env:TRUSTDESK_DEMO_TOKEN    = 'agent-token'      # required: any non-empty secret
$env:TRUSTDESK_MANAGER_TOKEN = 'manager-token'    # optional: needed to approve actions (see Roles)
mvn spring-boot:run
```

```bash
# macOS/Linux
export TRUSTDESK_DEMO_TOKEN=agent-token TRUSTDESK_MANAGER_TOKEN=manager-token
mvn spring-boot:run
```

On start the app creates `trustdesk.db` (schema from `src/main/resources/schema.sql`) and loads the seed data from
`data/` (6 customers, 6 orders, 8 tickets, 8 KB documents, 6 tool definitions, 8 eval cases). Seeding is idempotent.
Delete `trustdesk.db` to reset all state.

Check it in Windows PowerShell (where `curl` is an alias for `Invoke-WebRequest`):

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/tickets' -Headers @{ Authorization = 'Bearer agent-token' }
```

To use curl in PowerShell, call `curl.exe` explicitly:

```powershell
curl.exe -H "Authorization: Bearer agent-token" http://localhost:8080/tickets
```

Replace `agent-token` with the value configured in `TRUSTDESK_DEMO_TOKEN`.

**Roles.** `TRUSTDESK_DEMO_TOKEN` = support agent. `TRUSTDESK_MANAGER_TOKEN` = support manager (can also approve
actions). If the manager token is unset, the single demo token acts as both (simple demo mode).

## Frontend dashboard

The React + Vite support-operations dashboard lives in `frontend/`. Start the backend as above, then in a second
terminal run:

```powershell
cd frontend
npm install
npm run dev
```

Open the Vite URL and enter the demo bearer token; enter `TRUSTDESK_MANAGER_TOKEN` as well to approve gated actions.
Entered tokens are retained in browser local storage. API requests default to `/api`, which Vite proxies to
`http://localhost:8080`. Copy `frontend/.env.example` to `frontend/.env.local` to set `VITE_API_BASE_URL` or
`VITE_API_PROXY_TARGET`.

For the step-by-step frontend demo—including triage, citations, guardrails, manager approval, safe execution,
idempotency, traces, adversarial tickets, and evaluation—see [`docs/DEMO.md`](docs/DEMO.md).

## Tests

```bash
mvn clean verify
```

tests (data loading, APIs, auth, validation, retrieval), plus unit tests for the guards, action
policy, triage and draft engines, an end-to-end seed-data pipeline test, and MockMvc workflow tests (triage, drafts,
approval-gated action, traces, evaluation). Workflow tests use their own SQLite file and the mock AI provider.

## Run the evaluation

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/eval-runs -Headers @{Authorization='Bearer agent-token'} |
  Select-Object -ExpandProperty metrics
```

Reports triage accuracy (category, priority), citation coverage, unsafe-action blocking, allowed-action recall and
escalation accuracy, with per-case results and an explicit verdict for the three adversarial cases (`eval_005/006/007`).
See `docs/EVALUATION_REPORT.md`.

## Demo

Follow [`docs/DEMO.md`](docs/DEMO.md) for the frontend walkthrough: open a ticket, triage and draft, review citations
and guardrails, request/approve/execute a refund review, verify idempotency, inspect traces, test adversarial tickets,
and run the evaluation.

## Documentation

| File                        | Content                                                                   |
| --------------------------- | ------------------------------------------------------------------------- |
| `docs/API.md`               | every endpoint, request/response shapes, error codes                      |
| `docs/ARCHITECTURE.md`      | layers, AI/guardrail design, trust boundaries, decisions                  |
| `docs/RETRIEVAL_API.md`     | knowledge search and ranking                                              |
| `docs/DEMO.md`              | frontend-first demo walkthrough, API cross-reference, and troubleshooting |
| `docs/EVALUATION_REPORT.md` | evaluation results and failure modes                                      |

## AI and guardrail architecture (summary)

- **Provider port** `AiProvider`: `mock` (default), `openai-compatible`, or `unconfigured`. Output is parsed strictly;
  malformed output, unknown categories/priorities, or provider errors fall back to a safe **escalation**.
- **Input guard** flags prompt injection, secret extraction, identity-check bypass, "hide this from the reviewer",
  coupon requests and safety issues. Flags can only add caution (force escalation, block actions).
- **Evidence policy**: only documents from trusted internal audiences count as evidence. `KB-ADVERSARIAL-001`
  (imported vendor note) stays stored and searchable but is **never** sent to the model, cited, or obeyed.
- **Grounding**: a draft needs evidence above a score threshold, and every citation must point at evidence
  that was actually retrieved; otherwise the system declines to answer and escalates.
- **Output guard** blocks replies that leak the system prompt/secrets/other customers' data, contain a full card
  number, ask for CVV/OTP/password, or promise refunds/replacements/coupons.
- **Action policy** (applied to AI recommendations and UI requests alike): category allow-list, required fields, payload
  must belong to the ticket, return window and warranty computed from the **ticket's `created_at`** (never the clock),
  final-sale items blocked, coupon cap, adversarial tickets only escalate, account lock not automatable.
- **Approval gate**: `start_refund_review` goes `approval_required` -> manager approves -> execute (once). It queues a
  human refund review; it **never moves money** and no reply promises a refund. Retries are idempotent.

## Known limitations

- The default mock model is rule/template based. It proves the pipeline and safety layers deterministically; it is not
  an LLM. The OpenAI-compatible adapter is implemented but not covered by automated tests (needs a live endpoint).
- Lexical retrieval only (no embeddings). Evaluation runs synchronously (8 fast cases).
- Two demo tokens stand in for real users/roles. SQLite and `schema.sql` (no migration tool) suit a demo, not production.
- Only `start_refund_review` is fully gated and executable end to end; `escalate_to_human` and
  `open_carrier_investigation` are simulated; other tools are blocked or return `501`.
- The frontend is a local demo dashboard; two bearer tokens stand in for production identity and role management.
