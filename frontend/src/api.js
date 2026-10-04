const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');

export class ApiError extends Error {
  constructor(message, { status, code, details } = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

function describeError(status, payload) {
  const code = payload?.code;
  const message = payload?.message || payload?.error;
  const detailText = Array.isArray(payload?.details)
    ? payload.details.map((detail) => detail.message || detail.field).filter(Boolean).join(', ')
    : '';

  if (status === 401) return 'Your token was not accepted. Check the agent or manager token and try again.';
  if (status === 403) return 'This operation requires manager permissions. Enter a manager token to approve actions.';
  if (status === 404) return message || 'The requested ticket or record was not found.';
  if (status === 409) return `${message || 'This operation is not valid in the current state.'}${code ? ` (${code})` : ''}`;
  if (status === 422) return `${message || 'The request was blocked by a policy guardrail.'}${detailText ? ` — ${detailText}` : ''}`;
  if (status === 400) return `${message || 'The request needs attention.'}${detailText ? ` — ${detailText}` : ''}`;
  if (status >= 500) return 'The TrustDesk service encountered an error. Please try again.';
  return message || `Request failed${status ? ` (${status})` : ''}.`;
}

async function request(path, { token, managerToken, method = 'GET', body, signal } = {}) {
  const activeToken = managerToken || token;
  if (!activeToken) {
    throw new ApiError('Add an API bearer token in the connection settings to continue.');
  }

  let response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers: {
        Authorization: `Bearer ${activeToken}`,
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      },
      ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
      signal,
    });
  } catch (error) {
    if (error.name === 'AbortError') throw error;
    throw new ApiError(`Could not reach the TrustDesk API at ${API_BASE_URL}. Confirm the backend is running and the API URL is correct.`);
  }

  const text = await response.text();
  let payload;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    payload = null;
  }

  if (!response.ok) {
    throw new ApiError(describeError(response.status, payload), {
      status: response.status,
      code: payload?.code,
      details: payload?.details,
    });
  }
  return payload;
}

const withToken = (token, managerToken) => ({ token, managerToken });

export const api = {
  listTickets: (token) => request('/tickets?page=0&size=100', withToken(token)),
  getTicket: (id, token) => request(`/tickets/${encodeURIComponent(id)}`, withToken(token)),
  triage: (id, token) => request(`/tickets/${encodeURIComponent(id)}/triage`, {
    ...withToken(token),
    method: 'POST',
  }),
  createDraft: (id, token) => request(`/tickets/${encodeURIComponent(id)}/draft-reply`, {
    ...withToken(token),
    method: 'POST',
  }),
  listDrafts: (id, token) => request(`/tickets/${encodeURIComponent(id)}/draft-replies`, withToken(token)),
  listActions: (id, token) => request(`/tickets/${encodeURIComponent(id)}/tool-actions`, withToken(token)),
  createAction: (ticketId, toolName, payload, token) => request('/tool-actions', {
    ...withToken(token),
    method: 'POST',
    body: { ticket_id: ticketId, tool_name: toolName, payload },
  }),
  approveAction: (id, reason, token, managerToken) => request(`/tool-actions/${encodeURIComponent(id)}/approve`, {
    ...withToken(token, managerToken),
    method: 'POST',
    body: { decision: 'approved', reason },
  }),
  executeAction: (id, token) => request(`/tool-actions/${encodeURIComponent(id)}/execute`, {
    ...withToken(token),
    method: 'POST',
  }),
  listRuns: (ticketId, token) => request(`/tickets/${encodeURIComponent(ticketId)}/agent-runs`, withToken(token)),
  getRun: (id, token) => request(`/agent-runs/${encodeURIComponent(id)}`, withToken(token)),
  listEvalRuns: (token) => request('/eval-runs', withToken(token)),
  runEvaluation: (token) => request('/eval-runs', { ...withToken(token), method: 'POST' }),
};
