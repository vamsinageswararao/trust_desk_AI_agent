# TrustDesk Architecture

```
HTTP API (controllers, validation, ApiError)
   -> services (transactions, persistence, views)        TriageService, DraftReplyService, ToolActionService,
                                                          EvaluationService, AgentRunService, TicketService
   -> framework-free agent core                          TriageEngine, DraftEngine, ActionPolicy, ActionRecommender,
                                                          EvalScorer, PolicyFacts, PromptBuilder
        |- guardrails: InputGuard, OutputGuard, EvidencePolicy
        |- retrieval port: KnowledgeRetriever (LexicalKnowledgeRetriever)
        |- AI port: AiProvider (MockAiProvider | OpenAiCompatibleAiProvider | UnconfiguredAiProvider)
   -> repositories (Spring Data) -> SQLite (schema.sql)
```
The agent core has no Spring/JPA dependency, so it is unit-testable without a container and the persistence or AI
provider can be swapped without touching business rules.

## Trust boundaries
Untrusted: customer ticket text, KB document text, model output, request bodies. Trusted: application code, the
tool catalog, ticket/order records, the authenticated principal.
- Untrusted text appears only in `untrusted_*` JSON fields of the prompt, never in the system instruction.
- Model output is parsed strictly; only validated typed fields are used. Extra fields (for example a "tool") are ignored.
- Recommended actions come from `ActionRecommender` (application facts + policy), never from model text.
- Escalation and priority can only be raised by guards/floors, never lowered by the model.

## Draft pipeline
ticket -> triage (stored or run) -> input guard flags -> retrieval query (customer words + category/flag terms; words only)
-> `EvidencePolicy` (provenance: trusted audiences only) -> sufficiency (score >= threshold) -> model draft -> citation
validation (every cited id must be retrieved, trusted evidence) -> `OutputGuard` -> actions + guardrail report -> persist
draft and trace. Any failure produces a safe hand-off message and an escalation recommendation.

## Decision: KB-ADVERSARIAL-001
Excluded by **provenance** (its audience, "Imported vendor documentation", is not a trusted internal audience), not by
ID or by guessing at its text (the security playbook legitimately quotes injection examples, so content scanning would
wrongly exclude it). It stays stored and searchable; the trace lists it under `excluded_documents`. Trusted audiences
are configurable (`trustdesk.guardrails.trusted-audiences`).

## Decision: dates
Policy windows use the ticket's `created_at` in its own offset (`PolicyFacts`, `ActionPolicy`); the wall clock is never read.
Timestamps in the database (created/approved/executed) are audit data only.

## Decision: approval and idempotency
`tool_actions.idempotency_key` is UNIQUE. Create returns the existing action for a repeated key (conflict if another
ticket/tool). `approve` is manager-only and idempotent for the same decision. `execute` runs the executor once and stores the
result; later calls return it. Policy is checked at request time and again at execution.

## Persistence
`schema.sql` defines tables, foreign keys, unique and check constraints (Hibernate's SQLite dialect cannot emit foreign
keys). Structured values are JSON text columns. Evaluation labels live only in `eval_cases`; tickets carry none.
No schema changes were needed after Milestone 1: triage and trace data use existing columns.

## Configuration (environment)
`TRUSTDESK_DEMO_TOKEN` (required), `TRUSTDESK_MANAGER_TOKEN`, `TRUSTDESK_DB_PATH`, `TRUSTDESK_DATA_DIR`,
`TRUSTDESK_AI_PROVIDER` (`mock` default | `openai-compatible` | `unconfigured`), `TRUSTDESK_AI_BASE_URL`,
`TRUSTDESK_AI_API_KEY`, `TRUSTDESK_AI_MODEL`. Guardrail tuning: `trustdesk.guardrails.*`, retrieval limits: `trustdesk.retrieval.*`.
