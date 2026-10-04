# TrustDesk API

Base URL `http://localhost:8080`. JSON is snake_case. All endpoints require `Authorization: Bearer <token>`.

**Roles:** `TRUSTDESK_DEMO_TOKEN` = agent; `TRUSTDESK_MANAGER_TOKEN` = manager. Only managers may call
`POST /tool-actions/{id}/approve` (agents get `403`). If no manager token is configured the single token has both roles.
The reviewer recorded on an approval is the authenticated principal (`demo-support-agent` / `demo-support-manager`).

## Errors (all endpoints)
```json
{"timestamp":"...","status":422,"error":"Unprocessable Entity","code":"GUARDRAIL_BLOCKED",
 "message":"...","path":"/tool-actions","details":[{"field":"policy_code","message":"FINAL_SALE"}]}
```
| HTTP | code | meaning |
|---|---|---|
| 400 | VALIDATION_FAILED / MALFORMED_REQUEST / UNKNOWN_TOOL / INVALID_IDEMPOTENCY_KEY | bad input (`details[]` names fields) |
| 401 / 403 | UNAUTHORIZED / FORBIDDEN | missing or wrong token / not a manager |
| 404 | NOT_FOUND | unknown ticket, action, run, eval run |
| 405 | METHOD_NOT_ALLOWED | |
| 409 | INVALID_STATE / IDEMPOTENCY_CONFLICT / CONFLICT | action in the wrong state; key reused for another ticket/tool |
| 422 | GUARDRAIL_BLOCKED | application policy refused it; `details[0].message` is the policy code |
| 501 | TOOL_NOT_IMPLEMENTED | approved, but no executor for that tool |
| 500 | INTERNAL_ERROR | never contains internals |

Policy codes: `NOT_TRIAGED, CATEGORY_NOT_ALLOWED, MISSING_FIELD, ORDER_MISMATCH, CUSTOMER_MISMATCH, TICKET_MISMATCH,
ADVERSARIAL_TICKET, INVALID_AMOUNT, COUPON_LIMIT, FINAL_SALE, RETURN_WINDOW_CLOSED, SAFETY_ESCALATION_FIRST,
WARRANTY_NOT_COVERED, NOT_AUTOMATABLE`.

## Tickets (Milestone 1)
- `GET /tickets?page=&size=&status=` -> `{items:[{ticket_id,customer_id,order_id,channel,subject,status,created_at,triage_category,triage_priority,triage_should_escalate}],page,size,total_elements,total_pages}`
- `GET /tickets/{ticketId}` -> ticket + `customer{...}` + `order{...,items[]}` + `triage` (null until triaged:
  `category,priority,sentiment,should_escalate,reason_summary,run_id,triaged_at`). Evaluation labels are never exposed.

## Knowledge search (Milestone 2)
`GET /documents/search?q=&topK=` -> see `docs/RETRIEVAL_API.md`. Results carry `content_trust: "untrusted_data"`.

## Triage
`POST /tickets/{ticketId}/triage` (no body) -> `200`
```json
{"ticket_id":"tkt_9001","category":"refund","priority":"medium","sentiment":"frustrated","should_escalate":false,
 "reason_summary":"Damaged or defective item reported","source":"model","warnings":[],"run_id":"run_...",
 "guardrail":{"decision":"passed","flags":[],"violations":[],"notes":["..."],"explanation":["..."]}}
```
`category` in `shipping|refund|warranty|billing|account_security|general`; `priority` in `low|medium|high|urgent`.
`source` is `model` or `fallback` (invalid/failed model output -> escalated general/medium). Result is stored on the ticket.

## Grounded draft reply
`POST /tickets/{ticketId}/draft-reply` (no body; triages first if needed) -> `201`
```json
{"draft_id":"draft_...","ticket_id":"tkt_9001","status":"generated","body":"Hi Aisha, ...",
 "citations":[{"doc_id":"KB-REFUND-001","title":"Refund and Return Policy","version":"2026.07","source_path":"data/knowledge_base/refund_policy.md"}],
 "recommended_actions":[{"tool_name":"start_refund_review","requires_human_approval":true,"risk_level":"medium",
   "reason":"Refund requested for order ord_5001","status":"recommended",
   "payload":{"order_id":"ord_5001","reason":"...","amount":8999,"idempotency_key":"tkt_9001-refund-review-1"}}],
 "blocked_recommendations":[{"tool_name":"...","status":"blocked","code":"FINAL_SALE","reason":"..."}],
 "guardrail":{"decision":"passed|escalated|blocked","flags":[],"violations":[],"notes":[],"draft_status":"grounded",
   "requires_human_handling":false,"excluded_documents":[{"doc_id":"KB-ADVERSARIAL-001","reason":"untrusted_source: ..."}],
   "explanation":["triage: ...","retrieved: [...]","evidence used: [...]","recommended actions: [...]"]},
 "requires_human_handling":false,"run_id":"run_...","created_at":"..."}
```
`draft_status`: `grounded | insufficient_evidence | ungrounded_fallback | model_error_fallback | output_blocked`.
A non-grounded draft contains only a safe hand-off message with no citations. `recommended_actions[].payload` can be POSTed
as-is to `/tool-actions`. Recommendations are advice: nothing is created or executed.
`GET /tickets/{ticketId}/draft-replies` -> list of the same shape (oldest first).

## Tool actions (approval-gated)
- `POST /tool-actions` body `{ticket_id, tool_name, payload{...incl. idempotency_key}}`. Policy-checked.
  `201` new (`status` `approval_required`, or `requested` for low-risk tools); `200` with `idempotent_replay:true` if the key
  was used before; `422` if blocked (a `blocked` trace is recorded, no action is created).
- `POST /tool-actions/{id}/approve` (**manager**) body `{decision:"approved"|"rejected", reason?}` -> `approved`/`rejected`.
  Repeating the same decision returns `idempotent_replay:true`; the opposite decision or a no-approval tool -> `409`.
- `POST /tool-actions/{id}/execute` (no body). Only from `approved` (or `requested` for no-approval tools), else `409`.
  Policy is re-checked at execution. Re-executing returns the stored result with `idempotent_replay:true` (never runs twice).
- `GET /tool-actions/{id}`, `GET /tickets/{ticketId}/tool-actions`.

Action view: `{action_id,ticket_id,tool_name,status,requires_human_approval,risk_level,payload,idempotency_key,
approval:{approval_id,decision,reviewer_id,reason,created_at}|null,execution_result|null,idempotent_replay,created_at,updated_at}`.
Statuses: `requested, approval_required, approved, rejected, executed, failed`.
`start_refund_review` result: `{refund_review_id,status:"queued_for_human_review",order_id,amount_under_review,money_moved:false,message}`.

## Traces
- `GET /agent-runs/{runId}`, `GET /tickets/{ticketId}/agent-runs` ->
  `{run_id,ticket_id,run_type:"triage|draft_reply|tool_recommendation|eval_case",status,retrieved_doc_ids[],tool_calls[],guardrail_results{...,explanation[]},created_at}`.
  Tool lifecycle events (`approval_required`, `approved`, `executed`, `blocked`, `failed`) are `tool_recommendation` runs.

## Evaluation
- `POST /eval-runs` optional body `{case_ids:["eval_006"]}` (default all) -> `201`
  `{eval_run_id,started_at,completed_at,total_cases,metrics{total_cases,cases_passed,category_accuracy,priority_accuracy,
  citation_coverage,unsafe_action_block_rate,allowed_action_recall,escalation_accuracy},case_results[],adversarial_checks[]}`.
- `GET /eval-runs/{id}`, `GET /eval-runs` (newest first).
Evaluation never writes triage/drafts to tickets; it records one `eval_case` trace per case.
