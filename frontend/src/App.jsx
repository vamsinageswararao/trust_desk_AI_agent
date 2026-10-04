import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertTriangle,
  ArrowDownLeft,
  ArrowUpRight,
  Bot,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  CircleHelp,
  Clock3,
  FileText,
  Fingerprint,
  Gauge,
  Inbox,
  LoaderCircle,
  LockKeyhole,
  LogOut,
  MessageSquareText,
  RefreshCw,
  Search,
  ShieldAlert,
  ShieldCheck,
  Sparkles,
  Ticket,
  UserRound,
  Wallet,
  XCircle,
} from 'lucide-react';
import { api, ApiError } from './api.js';

const TOKEN_KEY = 'trustdesk.agent-token';
const MANAGER_KEY = 'trustdesk.manager-token';
const numberFormat = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 });

function formatDate(value) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

function money(amount, currency = 'INR') {
  if (amount === undefined || amount === null || amount === '') return 'Not specified';
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency, maximumFractionDigits: 2 })
    .format(Number(amount) / 100);
}

function titleCase(value) {
  return String(value || 'unknown').replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function humanizeError(error) {
  if (error instanceof ApiError) return error.message;
  return error?.message || 'Something went wrong. Please try again.';
}

function StatusBadge({ children, tone = 'neutral', dot = false }) {
  return <span className={`badge badge-${tone}`}>{dot && <i />} {titleCase(children)}</span>;
}

function Metric({ label, value, subtext, icon: Icon, tone = 'blue' }) {
  return (
    <div className="metric-card">
      <div className={`metric-icon metric-${tone}`}><Icon size={18} /></div>
      <div>
        <div className="metric-label">{label}</div>
        <div className="metric-value">{value}</div>
        {subtext && <div className="metric-subtext">{subtext}</div>}
      </div>
    </div>
  );
}

function ErrorNotice({ message, onDismiss }) {
  if (!message) return null;
  return (
    <div className="notice notice-error" role="alert">
      <AlertTriangle size={17} />
      <span>{message}</span>
      {onDismiss && <button className="icon-button notice-close" aria-label="Dismiss error" onClick={onDismiss}><XCircle size={16} /></button>}
    </div>
  );
}

function SectionTitle({ eyebrow, title, action }) {
  return (
    <div className="section-title">
      <div>
        {eyebrow && <div className="eyebrow">{eyebrow}</div>}
        <h2>{title}</h2>
      </div>
      {action}
    </div>
  );
}

function Card({ children, className = '' }) {
  return <section className={`panel ${className}`}>{children}</section>;
}

function ConnectionPanel({ token, managerToken, onSave }) {
  const [agentDraft, setAgentDraft] = useState(token);
  const [managerDraft, setManagerDraft] = useState(managerToken);

  useEffect(() => {
    setAgentDraft(token);
    setManagerDraft(managerToken);
  }, [token, managerToken]);

  function submit(event) {
    event.preventDefault();
    onSave(agentDraft.trim(), managerDraft.trim());
  }

  return (
    <div className="connection-screen">
      <div className="connection-card">
        <div className="brand-mark brand-mark-large"><ShieldCheck size={25} /></div>
        <div className="eyebrow">SUPPORT OPERATIONS</div>
        <h1>Connect to TrustDesk</h1>
        <p>Enter the demo bearer token configured for your local backend. The token stays in this browser only.</p>
        <form onSubmit={submit} className="connection-form">
          <label>
            Agent token <span className="required">*</span>
            <input autoComplete="off" type="password" value={agentDraft} onChange={(event) => setAgentDraft(event.target.value)} placeholder="TRUSTDESK_DEMO_TOKEN" />
          </label>
          <label>
            Manager token <span className="optional">Optional for approval</span>
            <input autoComplete="off" type="password" value={managerDraft} onChange={(event) => setManagerDraft(event.target.value)} placeholder="TRUSTDESK_MANAGER_TOKEN" />
          </label>
          <button className="button button-primary button-full" type="submit" disabled={!agentDraft.trim()}>
            Connect dashboard <ArrowUpRight size={16} />
          </button>
        </form>
        <div className="connection-footnote"><LockKeyhole size={14} /> Requests use Authorization: Bearer &lt;token&gt;</div>
      </div>
    </div>
  );
}

function GuardrailCard({ report, draft }) {
  const decision = report?.decision || (draft?.requires_human_handling ? 'escalated' : null);
  const tone = decision === 'passed' ? 'success' : decision === 'blocked' || decision === 'escalated' ? 'warning' : 'neutral';
  const explanations = report?.explanation || [];
  const flags = report?.flags || [];
  const violations = report?.violations || [];

  return (
    <Card className="guardrail-panel">
      <SectionTitle eyebrow="SAFETY" title="Guardrail review" />
      {decision ? (
        <>
          <div className={`guardrail-state state-${tone}`}>
            {tone === 'success' ? <ShieldCheck size={19} /> : <ShieldAlert size={19} />}
            <div>
              <strong>{titleCase(decision)}</strong>
              <span>{report?.draft_status ? titleCase(report.draft_status) : 'Policy and output checks'}</span>
            </div>
          </div>
          {draft?.requires_human_handling && <p className="safety-callout">Human handling is required. This AI output does not authorize an action.</p>}
          {flags.length > 0 && <div className="tag-list">{flags.map((flag) => <span className="tag tag-warning" key={flag}>{titleCase(flag)}</span>)}</div>}
          {violations.length > 0 && <div className="tag-list">{violations.map((flag) => <span className="tag tag-danger" key={flag}>{titleCase(flag)}</span>)}</div>}
          {report?.excluded_documents?.map((doc) => (
            <div className="excluded-doc" key={doc.doc_id}>
              <ShieldAlert size={15} />
              <span><strong>{doc.doc_id}</strong> excluded · untrusted source</span>
            </div>
          ))}
          {explanations.length > 0 && (
            <ul className="explanation-list">
              {explanations.map((line, index) => <li key={`${line}-${index}`}>{line}</li>)}
            </ul>
          )}
          {!flags.length && !violations.length && !explanations.length && (
            <p className="muted small">No additional guardrail notes were returned.</p>
          )}
        </>
      ) : (
        <div className="empty-state compact">
          <ShieldCheck size={22} />
          <span>Run triage or generate a draft to see its safety review.</span>
        </div>
      )}
    </Card>
  );
}

function TicketView({
  ticket,
  detail,
  triage,
  draft,
  actions,
  runs,
  onTriage,
  onDraft,
  onCreateAction,
  onApprove,
  approvalReason,
  onApprovalReasonChange,
  onExecute,
  onRefresh,
  onOpenTrace,
  busy,
  errors,
  clearError,
}) {
  const triageView = triage || detail?.triage;
  const recommendation = draft?.recommended_actions?.find((item) => item.tool_name === 'start_refund_review');
  const latestAction = actions.find((item) => item.tool_name === 'start_refund_review');
  const priority = triageView?.priority || ticket?.triage_priority;
  const isEscalated = triageView?.should_escalate ?? ticket?.triage_should_escalate;
  const canExecute = latestAction && (
    latestAction.status === 'approved'
    || (latestAction.status === 'requested' && !latestAction.requires_human_approval)
    || latestAction.status === 'executed'
  );
  const actionButtonLabel = latestAction?.status === 'executed' ? 'Replay safely' : 'Execute action';

  if (!ticket) return null;

  return (
    <div className="ticket-workspace">
      <div className="ticket-heading">
        <div className="ticket-heading-main">
          <div className="breadcrumb"><span>Tickets</span><ChevronRight size={14} /><span>{ticket.ticket_id}</span></div>
          <h1>{detail?.subject || ticket.subject}</h1>
          <div className="ticket-meta">
            <span className="mono">{ticket.ticket_id}</span><span className="meta-divider" />
            <span><UserRound size={14} /> {detail?.customer?.name || ticket.customer_id}</span><span className="meta-divider" />
            <span>{titleCase(detail?.channel || ticket.channel)}</span>
            <StatusBadge tone={priority === 'urgent' || priority === 'high' ? 'danger' : 'neutral'}>{priority || 'untriaged'}</StatusBadge>
            {isEscalated && <StatusBadge tone="warning" dot>escalated</StatusBadge>}
          </div>
        </div>
        <button className="button button-quiet" onClick={onRefresh} disabled={busy.refresh}>
          {busy.refresh ? <LoaderCircle className="spin" size={15} /> : <RefreshCw size={15} />} Refresh
        </button>
      </div>

      <ErrorNotice message={errors.ticket} onDismiss={() => clearError('ticket')} />

      <div className="workflow-banner">
        <div className="workflow-icon"><Bot size={19} /></div>
        <div><strong>AI assistance with human control</strong><span>Recommendations are not actions. Sensitive tools remain approval-gated.</span></div>
        <span className="live-indicator"><i /> Authenticated</span>
      </div>

      <div className="detail-grid">
        <Card className="customer-message">
          <SectionTitle eyebrow="CUSTOMER MESSAGE" title="Ticket overview" />
          {detail ? (
            <>
              <blockquote>{detail.body || 'No message body was included in this ticket.'}</blockquote>
              <div className="overview-facts">
                <div><span>Customer</span><strong>{detail.customer?.name || detail.customer?.customer_id || ticket.customer_id}</strong><small>{detail.customer?.email || 'Email not provided'}</small></div>
                <div><span>Order</span><strong>{detail.order?.order_id || ticket.order_id || 'No linked order'}</strong><small>{detail.order?.status ? titleCase(detail.order.status) : 'Order status unavailable'}</small></div>
                <div><span>Created</span><strong>{formatDate(detail.created_at)}</strong><small>{detail.channel ? titleCase(detail.channel) : 'Channel unavailable'}</small></div>
              </div>
              {detail.order?.items?.length > 0 && (
                <div className="order-items">
                  <div className="subheading">Order items</div>
                  {detail.order.items.map((item, index) => (
                    <div className="order-item" key={item.sku || index}>
                      <span>{item.name || item.productName || item.sku || `Item ${index + 1}`}</span>
                      <span>{item.quantity ? `Qty ${item.quantity}` : ''}</span>
                    </div>
                  ))}
                </div>
              )}
            </>
          ) : <div className="skeleton-block" />}
          <div className="button-row">
            <button className="button button-secondary" onClick={onTriage} disabled={busy.triage}>
              {busy.triage ? <LoaderCircle className="spin" size={16} /> : <Sparkles size={16} />} Run triage
            </button>
            <button className="button button-primary" onClick={onDraft} disabled={busy.draft}>
              {busy.draft ? <LoaderCircle className="spin" size={16} /> : <MessageSquareText size={16} />} Generate draft
            </button>
          </div>
        </Card>

        <Card className="triage-panel">
          <SectionTitle eyebrow="AI TRIAGE" title="Ticket classification" />
          {triageView ? (
            <>
              <div className="triage-pills">
                <div className="triage-pill"><span>Category</span><strong>{titleCase(triageView.category)}</strong></div>
                <div className="triage-pill"><span>Priority</span><strong className={`priority-text priority-${triageView.priority}`}>{titleCase(triageView.priority)}</strong></div>
                <div className="triage-pill"><span>Sentiment</span><strong>{titleCase(triageView.sentiment)}</strong></div>
              </div>
              <div className={`escalation-box ${triageView.should_escalate ? 'escalation-needed' : 'escalation-clear'}`}>
                {triageView.should_escalate ? <AlertTriangle size={16} /> : <CheckCircle2 size={16} />}
                <span>{triageView.should_escalate ? 'Human escalation recommended' : 'No escalation requested'}</span>
              </div>
              {triageView.reason_summary && <p className="triage-reason">{triageView.reason_summary}</p>}
              <div className="run-reference">Run <span className="mono">{triageView.run_id || 'stored on ticket'}</span></div>
            </>
          ) : (
            <div className="empty-state"><Sparkles size={23} /><span>Triage has not run yet.</span><small>Classification and escalation are determined by TrustDesk.</small></div>
          )}
        </Card>

        <Card className="draft-panel">
          <SectionTitle
            eyebrow="CUSTOMER RESPONSE"
            title="Draft reply"
            action={draft && <StatusBadge tone="blue">{draft.guardrail?.draft_status || draft.status}</StatusBadge>}
          />
          {draft ? (
            <>
              <div className="draft-warning"><FileText size={16} /><span>Draft only — review before sending. TrustDesk does not send messages.</span></div>
              <div className="draft-body">{draft.body}</div>
              <div className="draft-footer">
                <span>Draft ID <span className="mono">{draft.draft_id}</span></span>
                <span>{formatDate(draft.created_at)}</span>
              </div>
              <div className="citation-section">
                <div className="subheading">Policy citations <span className="count-chip">{draft.citations?.length || 0}</span></div>
                {draft.citations?.length ? (
                  <div className="citation-list">
                    {draft.citations.map((citation) => (
                      <div className={`citation ${citation.doc_id === 'KB-ADVERSARIAL-001' ? 'citation-untrusted' : ''}`} key={citation.doc_id}>
                        <div className="citation-icon">{citation.doc_id === 'KB-ADVERSARIAL-001' ? <ShieldAlert size={16} /> : <FileText size={16} />}</div>
                        <div><strong>{citation.title}</strong><span className="mono">{citation.doc_id}</span></div>
                        {citation.version && <span className="version">v{citation.version}</span>}
                        {citation.doc_id === 'KB-ADVERSARIAL-001' && <span className="tag tag-danger">Untrusted</span>}
                      </div>
                    ))}
                  </div>
                ) : <p className="muted small">No citations were returned for this draft.</p>}
              </div>
              {draft.blocked_recommendations?.length > 0 && (
                <div className="blocked-list">
                  <div className="subheading text-danger"><ShieldAlert size={15} /> Blocked recommendations</div>
                  {draft.blocked_recommendations.map((item, index) => (
                    <div className="blocked-row" key={`${item.tool_name}-${index}`}>
                      <div><strong>{titleCase(item.tool_name)}</strong><span>{item.reason}</span></div>
                      <StatusBadge tone="danger">{item.code || 'blocked'}</StatusBadge>
                    </div>
                  ))}
                </div>
              )}
            </>
          ) : (
            <div className="empty-state"><MessageSquareText size={23} /><span>No draft generated.</span><small>Generated drafts include their own citations and guardrail review.</small></div>
          )}
        </Card>

        <GuardrailCard report={draft?.guardrail || (triage && triage.guardrail)} draft={draft} />

        <Card className="action-panel">
          <SectionTitle eyebrow="HUMAN-IN-THE-LOOP" title="Refund review workflow" />
          <div className="safety-callout prominent"><Wallet size={16} /><span><strong>Refund review only.</strong> This action queues a human review; it does not automatically issue a refund or move money.</span></div>
          {latestAction ? (
            <div className="action-content">
              <div className={`approval-stage stage-${latestAction.status}`}>
                <strong>{latestAction.status === 'approval_required' ? 'Manager approval required' : latestAction.status === 'approved' ? 'Approved · ready to execute' : latestAction.status === 'executed' ? 'Review queued' : titleCase(latestAction.status)}</strong>
                <span>{latestAction.status === 'approval_required'
                  ? 'This action has been requested. A manager must approve it before execution.'
                  : latestAction.status === 'approved'
                    ? 'A manager approved this action. It has not executed yet.'
                    : latestAction.status === 'executed'
                      ? 'Execution completed; the refund review was queued for a human.'
                      : 'The backend action lifecycle is shown below.'}</span>
              </div>
              <div className="action-header">
                <div className="action-tool-icon"><ArrowDownLeft size={18} /></div>
                <div className="action-tool"><strong>{titleCase(latestAction.tool_name)}</strong><span>Risk level: {titleCase(latestAction.risk_level)}</span></div>
                <StatusBadge tone={latestAction.status === 'executed' ? 'success' : latestAction.status === 'rejected' || latestAction.status === 'failed' ? 'danger' : 'warning'} dot>{latestAction.status}</StatusBadge>
              </div>
              <div className="action-facts">
                <div><span>Proposed amount</span><strong>{money(latestAction.payload?.amount, detail?.order?.currency)}</strong></div>
                <div><span>Approval required</span><strong>{latestAction.requires_human_approval ? 'Yes' : 'No'}</strong></div>
                <div><span>Idempotency key</span><strong className="mono small">{latestAction.idempotency_key || latestAction.payload?.idempotency_key || '—'}</strong></div>
              </div>
              {latestAction.approval && (
                <div className="approval-record"><CheckCircle2 size={15} /><span>{titleCase(latestAction.approval.decision)} by {latestAction.approval.reviewer_id}{latestAction.approval.reason ? ` · ${latestAction.approval.reason}` : ''}</span></div>
              )}
              {latestAction.status === 'approval_required' && (
                <label className="approval-reason">
                  Approval reason <span>Optional</span>
                  <input value={approvalReason} onChange={(event) => onApprovalReasonChange(event.target.value)} maxLength={500} placeholder="Add a short review note" />
                </label>
              )}
              {latestAction.execution_result && (
                <div className="execution-result">
                  <div className="result-title"><CheckCircle2 size={16} /> Execution result</div>
                  <p>{latestAction.execution_result.message || 'Action completed.'}</p>
                  <div className="result-details">
                    {latestAction.execution_result.status && <span>Status: {titleCase(latestAction.execution_result.status)}</span>}
                    {latestAction.execution_result.refund_review_id && <span>Review: {latestAction.execution_result.refund_review_id}</span>}
                    {latestAction.execution_result.money_moved === false && <span>Money moved: No</span>}
                  </div>
                  {latestAction.idempotent_replay && <div className="replay-note"><RefreshCw size={14} /> Idempotent replay — stored result returned; action did not run twice.</div>}
                </div>
              )}
              <div className="button-row action-buttons">
                {latestAction.status === 'approval_required' && (
                  <button className="button button-primary" onClick={onApprove} disabled={busy.approve}>
                    {busy.approve ? <LoaderCircle className="spin" size={16} /> : <Check size={16} />} Approve as manager
                  </button>
                )}
                {canExecute && (
                  <button className="button button-secondary" onClick={onExecute} disabled={busy.execute}>
                    {busy.execute ? <LoaderCircle className="spin" size={16} /> : <ArrowUpRight size={16} />} {actionButtonLabel}
                  </button>
                )}
                {latestAction.status === 'approval_required' && <span className="action-hint">Approval must be recorded before execution.</span>}
              </div>
            </div>
          ) : recommendation ? (
            <div className="recommendation">
              <div className="approval-flow" aria-label="Approval workflow stages">
                <span className="flow-current"><b>1</b> AI recommends</span>
                <ChevronRight size={13} />
                <span><b>2</b> Request approval</span>
                <ChevronRight size={13} />
                <span><b>3</b> Manager approves</span>
                <ChevronRight size={13} />
                <span><b>4</b> Execute</span>
              </div>
              <div className="recommendation-top">
                <div className="action-tool-icon"><Sparkles size={18} /></div>
                <div><strong>{titleCase(recommendation.tool_name)}</strong><span>AI recommendation · not yet requested</span></div>
                <StatusBadge tone="blue">recommended</StatusBadge>
              </div>
              <p>{recommendation.reason}</p>
              <div className="action-facts">
                <div><span>Proposed amount</span><strong>{money(recommendation.payload?.amount, detail?.order?.currency)}</strong></div>
                <div><span>Approval required</span><strong>{recommendation.requires_human_approval ? 'Yes' : 'No'}</strong></div>
              </div>
              <button className="button button-primary button-full request-approval-button" onClick={onCreateAction} disabled={busy.createAction}>
                {busy.createAction ? <LoaderCircle className="spin" size={16} /> : <CheckCircle2 size={16} />} Create action &amp; request human approval
              </button>
              <div className="small muted center">Next, a manager must approve this action before it can be executed.</div>
            </div>
          ) : (
            <div className="empty-state compact">
              <CircleHelp size={22} />
              <span>No refund-review action available for this ticket.</span>
              <small>Blocked recommendations, if any, are shown with the draft above.</small>
            </div>
          )}
        </Card>

        <Card className="trace-panel">
          <SectionTitle
            eyebrow="AUDIT TRAIL"
            title="Agent trace"
            action={<span className="count-chip">{runs.length} runs</span>}
          />
          {runs.length ? (
            <div className="timeline">
              {runs.map((run) => (
                <button className="timeline-row" key={run.run_id} onClick={() => onOpenTrace(run)}>
                  <span className={`timeline-marker ${run.status === 'blocked' || run.status === 'failed' ? 'marker-warning' : ''}`} />
                  <span className="timeline-content">
                    <strong>{titleCase(run.run_type)}</strong>
                    <span>{formatDate(run.created_at)} <span className="mono">{run.run_id}</span></span>
                    {run.retrieved_doc_ids?.length > 0 && <small>Documents: {run.retrieved_doc_ids.join(', ')}</small>}
                  </span>
                  <StatusBadge tone={run.status === 'blocked' || run.status === 'failed' ? 'warning' : 'neutral'}>{run.status}</StatusBadge>
                  <ChevronRight size={16} />
                </button>
              ))}
            </div>
          ) : (
            <div className="empty-state compact"><Clock3 size={21} /><span>No agent runs recorded yet.</span><small>Run triage or generate a draft to create an auditable trace.</small></div>
          )}
        </Card>
      </div>
    </div>
  );
}

function EvaluationView({ token }) {
  const [runs, setRuns] = useState([]);
  const [selected, setSelected] = useState(null);
  const [loading, setLoading] = useState(false);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const result = await api.listEvalRuns(token);
      setRuns(result || []);
      if (result?.length) setSelected((current) => current || result[0]);
    } catch (caught) {
      setError(humanizeError(caught));
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => { load(); }, [load]);

  async function runEvaluation() {
    setRunning(true);
    setError('');
    try {
      const result = await api.runEvaluation(token);
      setSelected(result);
      await load();
      setSelected(result);
    } catch (caught) {
      setError(humanizeError(caught));
    } finally {
      setRunning(false);
    }
  }

  const metrics = selected?.metrics || {};
  const metricRows = [
    ['Category accuracy', 'category_accuracy'],
    ['Priority accuracy', 'priority_accuracy'],
    ['Citation coverage', 'citation_coverage'],
    ['Unsafe-action blocking', 'unsafe_action_block_rate'],
    ['Allowed-action recall', 'allowed_action_recall'],
    ['Escalation accuracy', 'escalation_accuracy'],
  ].filter(([, key]) => Object.hasOwn(metrics, key));
  const passed = metrics.cases_passed;
  const total = metrics.total_cases ?? selected?.total_cases;

  return (
    <div className="evaluation-page">
      <div className="ticket-heading">
        <div><div className="eyebrow">QUALITY & SAFETY</div><h1>Evaluation</h1><p className="page-description">Measure triage quality, grounding, safe action handling, and escalation accuracy across the seeded test cases.</p></div>
        <button className="button button-primary" onClick={runEvaluation} disabled={running}>
          {running ? <LoaderCircle className="spin" size={16} /> : <Gauge size={16} />} Run evaluation
        </button>
      </div>
      <ErrorNotice message={error} onDismiss={() => setError('')} />
      {loading && !selected ? <div className="loading-state"><LoaderCircle className="spin" size={20} /> Loading evaluation runs…</div> : null}
      {selected ? (
        <>
          <div className="eval-summary">
            <Metric label="Cases passed" value={`${passed ?? '—'}${total !== undefined ? ` / ${total}` : ''}`} subtext={passed === total ? 'All cases passed' : 'Latest evaluation run'} icon={CheckCircle2} tone="green" />
            <Metric label="Total cases" value={total ?? '—'} subtext="In this evaluation run" icon={Ticket} tone="blue" />
            <Metric label="Started" value={formatDate(selected.started_at)} subtext={`Run ${selected.eval_run_id}`} icon={Clock3} tone="purple" />
          </div>
          <Card className="metrics-panel">
            <SectionTitle eyebrow="RETURNED BY BACKEND" title="Evaluation metrics" action={<StatusBadge tone={passed === total ? 'success' : 'neutral'}>{passed === total ? 'passed' : 'completed'}</StatusBadge>} />
            <div className="metric-table">
              {metricRows.map(([label, key]) => {
                const value = metrics[key];
                const numeric = Number(value);
                const percent = Number.isFinite(numeric) && numeric >= 0 && numeric <= 1 ? `${numberFormat.format(numeric * 100)}%` : value;
                return <div className="metric-table-row" key={key}><span>{label}</span><strong>{percent}</strong></div>;
              })}
              {!metricRows.length && <div className="empty-state compact">This run did not return metric values.</div>}
            </div>
          </Card>
          {selected.case_results?.length > 0 && (
            <Card className="case-panel">
              <SectionTitle eyebrow="CASE-BY-CASE" title="Evaluation results" action={<span className="count-chip">{selected.case_results.length} cases</span>} />
              <div className="table-scroll">
                <table>
                  <thead><tr><th>Case</th><th>Result</th><th>Category</th><th>Priority</th><th>Escalate</th></tr></thead>
                  <tbody>
                    {selected.case_results.map((item, index) => (
                      <tr key={item.case_id || index}>
                        <td className="mono">{item.case_id || `Case ${index + 1}`}</td>
                        <td><StatusBadge tone={item.passed ? 'success' : 'danger'}>{item.passed ? 'passed' : 'failed'}</StatusBadge></td>
                        <td>{titleCase(item.predicted_category)}</td><td>{titleCase(item.predicted_priority)}</td>
                        <td>{item.predicted_escalate === undefined ? '—' : item.predicted_escalate ? 'Yes' : 'No'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
          )}
          {selected.adversarial_checks?.length > 0 && (
            <Card>
              <SectionTitle eyebrow="SAFETY CHECKS" title="Adversarial cases" />
              <div className="adversarial-list">
                {selected.adversarial_checks.map((item, index) => (
                  <div className="adversarial-row" key={item.case_id || index}>
                    <span className="mono">{item.case_id || `Case ${index + 1}`}</span>
                    <StatusBadge tone={item.safe ? 'success' : 'danger'}>{item.safe ? 'safe' : 'unsafe'}</StatusBadge>
                  </div>
                ))}
              </div>
            </Card>
          )}
          <div className="eval-history">
            <span>Past runs</span>
            {runs.map((run) => (
              <button key={run.eval_run_id} className={run.eval_run_id === selected.eval_run_id ? 'history-chip selected' : 'history-chip'} onClick={() => setSelected(run)}>
                {formatDate(run.started_at)} · {run.metrics?.cases_passed ?? '—'}/{run.metrics?.total_cases ?? run.total_cases}
              </button>
            ))}
          </div>
        </>
      ) : !loading && (
        <div className="empty-page">
          <div className="empty-icon"><Gauge size={25} /></div>
          <h2>No evaluation runs yet</h2>
          <p>Run the backend evaluation to measure performance on all seeded cases.</p>
          <button className="button button-primary" onClick={runEvaluation} disabled={running}>{running ? <LoaderCircle className="spin" size={16} /> : <Gauge size={16} />} Run first evaluation</button>
        </div>
      )}
    </div>
  );
}

function TraceDialog({ run, onClose }) {
  if (!run) return null;
  const guardrail = run.guardrail_results || {};
  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <div className="trace-modal" role="dialog" aria-modal="true" aria-labelledby="trace-title" onClick={(event) => event.stopPropagation()}>
        <div className="modal-header">
          <div><div className="eyebrow">AUDIT TRACE</div><h2 id="trace-title">{titleCase(run.run_type)}</h2></div>
          <button className="icon-button" onClick={onClose} aria-label="Close trace"><XCircle size={19} /></button>
        </div>
        <div className="trace-modal-meta"><span>Ticket <b className="mono">{run.ticket_id}</b></span><span>Run <b className="mono">{run.run_id}</b></span><span>{formatDate(run.created_at)}</span></div>
        <div className="trace-modal-body">
          <div className="trace-detail-block"><div className="subheading">Final status</div><StatusBadge tone={run.status === 'blocked' || run.status === 'failed' ? 'warning' : 'success'}>{run.status}</StatusBadge></div>
          <div className="trace-detail-block"><div className="subheading">Retrieved document IDs</div>
            {run.retrieved_doc_ids?.length ? <div className="tag-list">{run.retrieved_doc_ids.map((id) => <span className={id === 'KB-ADVERSARIAL-001' ? 'tag tag-danger' : 'tag'} key={id}>{id}{id === 'KB-ADVERSARIAL-001' ? ' · untrusted' : ''}</span>)}</div> : <span className="muted">None</span>}
          </div>
          <div className="trace-detail-block"><div className="subheading">Guardrail result</div>
            {Object.keys(guardrail).length ? (
              <>
                {guardrail.decision && <StatusBadge tone={guardrail.decision === 'passed' ? 'success' : 'warning'}>{guardrail.decision}</StatusBadge>}
                {guardrail.explanation?.length > 0 && <ul className="explanation-list">{guardrail.explanation.map((item, index) => <li key={`${item}-${index}`}>{item}</li>)}</ul>}
                {guardrail.flags?.length > 0 && <div className="tag-list">{guardrail.flags.map((flag) => <span className="tag tag-warning" key={flag}>{titleCase(flag)}</span>)}</div>}
              </>
            ) : <span className="muted">No guardrail details were returned.</span>}
          </div>
          <div className="trace-detail-block"><div className="subheading">Tool calls and lifecycle events</div>
            {run.tool_calls?.length ? run.tool_calls.map((call, index) => (
              <pre className="trace-json" key={index}>{JSON.stringify(call, null, 2)}</pre>
            )) : <span className="muted">No tool calls recorded.</span>}
          </div>
        </div>
      </div>
    </div>
  );
}

export default function App() {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY) || '');
  const [managerToken, setManagerToken] = useState(() => localStorage.getItem(MANAGER_KEY) || '');
  const [page, setPage] = useState('tickets');
  const [tickets, setTickets] = useState([]);
  const [selectedId, setSelectedId] = useState('');
  const selectedIdRef = useRef('');
  const [detail, setDetail] = useState(null);
  const [triage, setTriage] = useState(null);
  const [draft, setDraft] = useState(null);
  const [actions, setActions] = useState([]);
  const [runs, setRuns] = useState([]);
  const [trace, setTrace] = useState(null);
  const [approvalReason, setApprovalReason] = useState('');
  const [queueSearch, setQueueSearch] = useState('');
  const [queueFilter, setQueueFilter] = useState('all');
  const [queueLoading, setQueueLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState({});
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const [showConnection, setShowConnection] = useState(false);

  const setError = (key, message) => setErrors((current) => ({ ...current, [key]: message }));
  const clearError = (key) => setErrors((current) => ({ ...current, [key]: '' }));
  const setBusyFlag = (key, value) => setBusy((current) => ({ ...current, [key]: value }));

  async function openTicket(ticketId) {
    if (!ticketId || !token) return;
    selectedIdRef.current = ticketId;
    setSelectedId(ticketId);
    setPage('tickets');
    setMobileSidebarOpen(false);
    setDetailLoading(true);
    clearError('ticket');
    setTriage(null);
    setDraft(null);
    setActions([]);
    setRuns([]);
    try {
      const [ticketResult, draftResult, actionResult, runResult] = await Promise.all([
        api.getTicket(ticketId, token),
        api.listDrafts(ticketId, token),
        api.listActions(ticketId, token),
        api.listRuns(ticketId, token),
      ]);
      setDetail(ticketResult);
      setTriage(ticketResult?.triage || null);
      setDraft(draftResult?.at(-1) || null);
      setActions([...(actionResult || [])].reverse());
      setRuns([...(runResult || [])].reverse());
    } catch (error) {
      setError('ticket', humanizeError(error));
    } finally {
      setDetailLoading(false);
    }
  }

  const loadTickets = useCallback(async (preferId = '') => {
    if (!token) return;
    setQueueLoading(true);
    clearError('queue');
    try {
      const result = await api.listTickets(token);
      const items = result?.items || [];
      setTickets(items);
      const nextId = preferId && items.some((item) => item.ticket_id === preferId)
        ? preferId
        : selectedIdRef.current && items.some((item) => item.ticket_id === selectedIdRef.current)
          ? selectedIdRef.current
          : items.find((item) => item.ticket_id === 'tkt_9001')?.ticket_id || items[0]?.ticket_id || '';
      if (nextId && nextId !== selectedIdRef.current) await openTicket(nextId);
      else {
        if (!nextId) {
          selectedIdRef.current = '';
          setSelectedId('');
          setDetail(null);
        }
      }
    } catch (error) {
      setError('queue', humanizeError(error));
    } finally {
      setQueueLoading(false);
    }
  }, [token]);

  const refreshWorkflow = useCallback(async (ticketId = selectedId) => {
    if (!ticketId || !token) return;
    setBusyFlag('refresh', true);
    clearError('ticket');
    try {
      const [ticketResult, draftResult, actionResult, runResult] = await Promise.all([
        api.getTicket(ticketId, token),
        api.listDrafts(ticketId, token),
        api.listActions(ticketId, token),
        api.listRuns(ticketId, token),
      ]);
      setDetail(ticketResult);
      setTriage(ticketResult?.triage || null);
      setDraft(draftResult?.at(-1) || null);
      setActions([...(actionResult || [])].reverse());
      setRuns([...(runResult || [])].reverse());
    } catch (error) {
      setError('ticket', humanizeError(error));
    } finally {
      setBusyFlag('refresh', false);
    }
  }, [selectedId, token]);

  useEffect(() => {
    if (token) loadTickets();
  }, [token, loadTickets]);

  const filteredTickets = useMemo(() => tickets.filter((item) => {
    const search = queueSearch.toLowerCase();
    const matchesSearch = !search || [item.ticket_id, item.subject, item.customer_id, item.order_id]
      .some((value) => String(value || '').toLowerCase().includes(search));
    const matchesFilter = queueFilter === 'all'
      || (queueFilter === 'escalated' && item.triage_should_escalate)
      || (queueFilter === 'high' && ['high', 'urgent'].includes(item.triage_priority));
    return matchesSearch && matchesFilter;
  }), [tickets, queueSearch, queueFilter]);

  function saveConnection(nextToken, nextManagerToken) {
    localStorage.setItem(TOKEN_KEY, nextToken);
    if (nextManagerToken) localStorage.setItem(MANAGER_KEY, nextManagerToken);
    else localStorage.removeItem(MANAGER_KEY);
    setToken(nextToken);
    setManagerToken(nextManagerToken);
    setShowConnection(false);
  }

  function disconnect() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(MANAGER_KEY);
    setToken('');
    setManagerToken('');
    setTickets([]);
    setSelectedId('');
    selectedIdRef.current = '';
    setDetail(null);
    setDraft(null);
    setTriage(null);
    setActions([]);
    setRuns([]);
  }

  async function runTriage() {
    setBusyFlag('triage', true);
    clearError('ticket');
    try {
      const result = await api.triage(selectedId, token);
      setTriage(result);
      await refreshWorkflow(selectedId);
      await loadTickets(selectedId);
    } catch (error) {
      setError('ticket', humanizeError(error));
    } finally {
      setBusyFlag('triage', false);
    }
  }

  async function generateDraft() {
    setBusyFlag('draft', true);
    clearError('ticket');
    try {
      const result = await api.createDraft(selectedId, token);
      setDraft(result);
      await refreshWorkflow(selectedId);
      await loadTickets(selectedId);
    } catch (error) {
      setError('ticket', humanizeError(error));
    } finally {
      setBusyFlag('draft', false);
    }
  }

  async function createAction() {
    const recommendation = draft?.recommended_actions?.find((item) => item.tool_name === 'start_refund_review');
    if (!recommendation) return;
    setBusyFlag('createAction', true);
    clearError('ticket');
    try {
      await api.createAction(selectedId, recommendation.tool_name, recommendation.payload, token);
      await refreshWorkflow(selectedId);
      await loadTickets(selectedId);
    } catch (error) {
      await refreshWorkflow(selectedId);
      setError('ticket', humanizeError(error));
    } finally {
      setBusyFlag('createAction', false);
    }
  }

  async function approveAction() {
    const action = actions[0];
    if (!action) return;
    setBusyFlag('approve', true);
    clearError('ticket');
    try {
      await api.approveAction(action.action_id, approvalReason.trim(), token, managerToken);
      await refreshWorkflow(selectedId);
      setApprovalReason('');
    } catch (error) {
      setError('ticket', humanizeError(error));
    } finally {
      setBusyFlag('approve', false);
    }
  }

  async function executeAction() {
    const action = actions[0];
    if (!action) return;
    setBusyFlag('execute', true);
    clearError('ticket');
    try {
      const result = await api.executeAction(action.action_id, token);
      await refreshWorkflow(selectedId);
      if (result?.idempotent_replay) {
        setActions((current) => current.map((item, index) => index === 0
          ? { ...item, idempotent_replay: true }
          : item));
      }
    } catch (error) {
      setError('ticket', humanizeError(error));
    } finally {
      setBusyFlag('execute', false);
    }
  }

  async function inspectTrace(run) {
    try {
      const result = await api.getRun(run.run_id, token);
      setTrace(result);
    } catch (error) {
      setError('ticket', humanizeError(error));
    }
  }

  if (!token || showConnection) {
    return <ConnectionPanel token={token} managerToken={managerToken} onSave={saveConnection} />;
  }

  return (
    <div className="app-shell">
      {mobileSidebarOpen && <button className="mobile-scrim" aria-label="Close navigation" onClick={() => setMobileSidebarOpen(false)} />}
      <aside className={`sidebar ${mobileSidebarOpen ? 'sidebar-open' : ''}`}>
        <div className="sidebar-brand">
          <div className="brand-mark"><ShieldCheck size={20} /></div>
          <div><strong>TrustDesk</strong><span>SUPPORT OPERATIONS</span></div>
          <button className="icon-button mobile-close" onClick={() => setMobileSidebarOpen(false)} aria-label="Close navigation"><XCircle size={18} /></button>
        </div>
        <div className="workspace-label">WORKSPACE</div>
        <nav className="side-nav" aria-label="Main navigation">
          <button className={page === 'tickets' ? 'nav-item active' : 'nav-item'} onClick={() => { setPage('tickets'); setMobileSidebarOpen(false); }}>
            <Inbox size={18} /><span>Ticket queue</span><span className="nav-count">{tickets.length}</span>
          </button>
          <button className={page === 'evaluation' ? 'nav-item active' : 'nav-item'} onClick={() => { setPage('evaluation'); setMobileSidebarOpen(false); }}>
            <Gauge size={18} /><span>Evaluations</span>
          </button>
        </nav>
        <div className="sidebar-divider" />
        <div className="queue-label"><span>OPEN TICKETS</span><span>{tickets.length}</span></div>
        <div className="queue-filters">
          <button className={queueFilter === 'all' ? 'filter-chip current' : 'filter-chip'} onClick={() => setQueueFilter('all')}>All</button>
          <button className={queueFilter === 'high' ? 'filter-chip current' : 'filter-chip'} onClick={() => setQueueFilter('high')}>High</button>
          <button className={queueFilter === 'escalated' ? 'filter-chip current' : 'filter-chip'} onClick={() => setQueueFilter('escalated')}>Escalated</button>
        </div>
        <label className="queue-search">
          <Search size={15} />
          <input value={queueSearch} onChange={(event) => setQueueSearch(event.target.value)} placeholder="Search tickets" aria-label="Search tickets" />
          {queueSearch && <button className="icon-button" onClick={() => setQueueSearch('')} aria-label="Clear search"><XCircle size={14} /></button>}
        </label>
        <div className="ticket-list">
          {queueLoading && !tickets.length ? <div className="queue-empty"><LoaderCircle className="spin" size={17} /> Loading queue</div> : null}
          {!queueLoading && filteredTickets.map((item) => (
            <button key={item.ticket_id} className={selectedId === item.ticket_id && page === 'tickets' ? 'queue-ticket selected' : 'queue-ticket'} onClick={() => openTicket(item.ticket_id)}>
              <span className="queue-ticket-top"><span className="mono">{item.ticket_id}</span>
                {(item.triage_priority === 'high' || item.triage_priority === 'urgent') && <span className={`queue-priority ${item.triage_priority}`} />}
              </span>
              <strong>{item.subject}</strong>
              <span className="queue-ticket-bottom"><span>{item.customer_id}</span><span>{titleCase(item.status)}</span></span>
              <span className="queue-classification">
                <span>{titleCase(item.triage_category || 'untriaged')}</span>
                {item.triage_priority && <span>{titleCase(item.triage_priority)}</span>}
              </span>
              {item.triage_should_escalate && <span className="queue-escalation"><AlertTriangle size={11} /> Escalation recommended</span>}
            </button>
          ))}
          {!queueLoading && !filteredTickets.length && <div className="queue-empty">No matching tickets.</div>}
          {errors.queue && <div className="queue-error">{errors.queue}</div>}
        </div>
        <div className="sidebar-bottom">
          <div className="sidebar-safety"><ShieldCheck size={15} /><span>Safety controls active</span></div>
          <button className="profile-control" onClick={() => setShowConnection(true)}>
            <div className="avatar">A</div><span><strong>Support agent</strong><small>Demo workspace</small></span><ChevronDown size={15} />
          </button>
          <button className="sidebar-action" onClick={disconnect}><LogOut size={15} /> Disconnect tokens</button>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <button className="icon-button mobile-menu" onClick={() => setMobileSidebarOpen(true)} aria-label="Open navigation"><Ticket size={19} /></button>
          <div className="topbar-context"><span>TrustDesk</span><ChevronRight size={14} /><strong>{page === 'evaluation' ? 'Evaluation' : 'Ticket workspace'}</strong></div>
          <div className="topbar-actions">
            <span className="api-status"><i /> Token configured</span>
            <button className="token-button" onClick={() => setShowConnection(true)}><Fingerprint size={16} /><span>Token settings</span></button>
          </div>
        </header>
        {page === 'evaluation' ? (
          <EvaluationView token={token} />
        ) : detailLoading && !detail ? (
          <div className="loading-page"><LoaderCircle className="spin" size={22} /> Opening ticket…</div>
        ) : detail ? (
          <TicketView
            ticket={tickets.find((item) => item.ticket_id === selectedId)}
            detail={detail}
            triage={triage}
            draft={draft}
            actions={actions}
            runs={runs}
            onTriage={runTriage}
            onDraft={generateDraft}
            onCreateAction={createAction}
            onApprove={approveAction}
            approvalReason={approvalReason}
            onApprovalReasonChange={setApprovalReason}
            onExecute={executeAction}
            onRefresh={() => refreshWorkflow(selectedId)}
            onOpenTrace={inspectTrace}
            busy={busy}
            errors={errors}
            clearError={clearError}
          />
        ) : (
          <div className="empty-page">
            <div className="empty-icon"><Inbox size={25} /></div>
            <h2>{errors.queue ? 'Could not load tickets' : 'Your queue is clear'}</h2>
            <p>{errors.queue || 'Select a ticket from the queue to review its support workflow.'}</p>
            <button className="button button-secondary" onClick={() => loadTickets()}><RefreshCw size={15} /> Retry</button>
          </div>
        )}
        <footer className="app-footer"><span>TrustDesk · AI assisted, human controlled</span><span><LockKeyhole size={13} /> Local demo environment</span></footer>
      </main>
      <TraceDialog run={trace} onClose={() => setTrace(null)} />
    </div>
  );
}
