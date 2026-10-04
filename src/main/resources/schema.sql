-- TrustDesk schema (SQLite). Hand-written because Hibernate's SQLite dialect cannot emit foreign keys
-- (no ALTER TABLE ADD CONSTRAINT). Column names follow Spring's default snake_case naming of the entities.
-- Idempotent: safe to run on every startup. Execution order = dependency order.

CREATE TABLE IF NOT EXISTS customers (
    customer_id TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    email       TEXT NOT NULL,
    tier        TEXT NOT NULL,
    country     TEXT NOT NULL,
    created_at  TEXT NOT NULL,
    verified    INTEGER NOT NULL,
    tags        TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS orders (
    order_id              TEXT PRIMARY KEY,
    customer_id           TEXT NOT NULL REFERENCES customers (customer_id),
    status                TEXT NOT NULL,
    placed_at             TEXT NOT NULL,
    delivered_at          TEXT,
    eligible_return_until TEXT,
    total                 INTEGER NOT NULL,
    currency              TEXT NOT NULL,
    payment_status        TEXT NOT NULL,
    tracking_number       TEXT,
    items                 TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS tickets (
    ticket_id              TEXT PRIMARY KEY,
    customer_id            TEXT NOT NULL REFERENCES customers (customer_id),
    order_id               TEXT REFERENCES orders (order_id),
    channel                TEXT NOT NULL,
    subject                TEXT NOT NULL,
    body                   TEXT NOT NULL,
    created_at             TEXT NOT NULL,
    status                 TEXT NOT NULL,
    triage_category        TEXT,
    triage_priority        TEXT,
    triage_sentiment       TEXT,
    triage_should_escalate INTEGER,
    triage_reason          TEXT,
    triage_run_id          TEXT,
    triaged_at             TEXT
);

CREATE TABLE IF NOT EXISTS knowledge_documents (
    doc_id      TEXT PRIMARY KEY,
    title       TEXT NOT NULL,
    source_path TEXT NOT NULL,
    version     TEXT,
    audience    TEXT,
    content     TEXT NOT NULL,
    checksum    TEXT NOT NULL,
    ingested_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS tool_catalog (
    tool_name               TEXT PRIMARY KEY,
    description             TEXT NOT NULL,
    risk_level              TEXT NOT NULL,
    requires_human_approval INTEGER NOT NULL,
    allowed_categories      TEXT NOT NULL,
    required_fields         TEXT NOT NULL,
    max_amount_inr          INTEGER
);

CREATE TABLE IF NOT EXISTS eval_cases (
    case_id   TEXT PRIMARY KEY,
    ticket_id TEXT NOT NULL REFERENCES tickets (ticket_id),
    input     TEXT NOT NULL,
    expected  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS draft_replies (
    draft_id     TEXT PRIMARY KEY,
    ticket_id    TEXT NOT NULL REFERENCES tickets (ticket_id),
    status       TEXT NOT NULL,
    body         TEXT NOT NULL,
    citations    TEXT NOT NULL,
    agent_run_id TEXT,
    created_at   TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS tool_actions (
    action_id               TEXT PRIMARY KEY,
    ticket_id               TEXT NOT NULL REFERENCES tickets (ticket_id),
    tool_name               TEXT NOT NULL REFERENCES tool_catalog (tool_name),
    payload                 TEXT NOT NULL,
    risk_level              TEXT NOT NULL,
    requires_human_approval INTEGER NOT NULL,
    status                  TEXT NOT NULL,
    idempotency_key         TEXT NOT NULL UNIQUE,
    execution_result        TEXT,
    created_at              TEXT NOT NULL,
    updated_at              TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS approvals (
    approval_id TEXT PRIMARY KEY,
    action_id   TEXT REFERENCES tool_actions (action_id),
    draft_id    TEXT REFERENCES draft_replies (draft_id),
    reviewer_id TEXT NOT NULL,
    decision    TEXT NOT NULL,
    reason      TEXT,
    created_at  TEXT NOT NULL,
    CHECK (action_id IS NOT NULL OR draft_id IS NOT NULL)
);

CREATE TABLE IF NOT EXISTS agent_runs (
    run_id            TEXT PRIMARY KEY,
    ticket_id         TEXT NOT NULL REFERENCES tickets (ticket_id),
    run_type          TEXT NOT NULL,
    status            TEXT NOT NULL,
    retrieved_doc_ids TEXT NOT NULL,
    tool_calls        TEXT NOT NULL,
    guardrail_results TEXT NOT NULL,
    created_at        TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS eval_runs (
    eval_run_id  TEXT PRIMARY KEY,
    started_at   TEXT NOT NULL,
    completed_at TEXT,
    total_cases  INTEGER NOT NULL,
    metrics      TEXT,
    case_results TEXT
);

CREATE INDEX IF NOT EXISTS idx_orders_customer     ON orders (customer_id);
CREATE INDEX IF NOT EXISTS idx_tickets_customer    ON tickets (customer_id);
CREATE INDEX IF NOT EXISTS idx_tickets_order       ON tickets (order_id);
CREATE INDEX IF NOT EXISTS idx_tickets_status      ON tickets (status);
CREATE INDEX IF NOT EXISTS idx_tickets_created_at  ON tickets (created_at);
CREATE INDEX IF NOT EXISTS idx_eval_cases_ticket   ON eval_cases (ticket_id);
CREATE INDEX IF NOT EXISTS idx_drafts_ticket       ON draft_replies (ticket_id);
CREATE INDEX IF NOT EXISTS idx_tool_actions_ticket ON tool_actions (ticket_id);
CREATE INDEX IF NOT EXISTS idx_approvals_action    ON approvals (action_id);
CREATE INDEX IF NOT EXISTS idx_approvals_draft     ON approvals (draft_id);
CREATE INDEX IF NOT EXISTS idx_agent_runs_ticket   ON agent_runs (ticket_id);
