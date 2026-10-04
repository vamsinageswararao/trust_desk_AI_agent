package com.trustdesk.api.dto;

import java.util.List;
import java.util.Map;

/** Response shapes for triage, drafts, tool actions, traces and evaluations (snake_case JSON). */
public final class WorkflowViews {
    private WorkflowViews() {}

    public record TriageView(String ticketId, String category, String priority, String sentiment,
                             boolean shouldEscalate, String reasonSummary, String source, List<String> warnings,
                             String runId, Map<String, Object> guardrail) {}

    /** Machine-readable citation for display: which KB document backs the draft. */
    public record CitationView(String docId, String title, String version, String sourcePath) {}

    /**
     * recommendedActions entries: tool_name, requires_human_approval, risk_level, reason, payload (ready to POST to
     * /tool-actions), status "recommended". blockedRecommendations: tool_name, status "blocked", code, reason.
     */
    public record DraftView(String draftId, String ticketId, String status, String body, List<CitationView> citations,
                            List<Map<String, Object>> recommendedActions,
                            List<Map<String, Object>> blockedRecommendations, Map<String, Object> guardrail,
                            boolean requiresHumanHandling, String runId, String createdAt) {}

    public record ApprovalView(String approvalId, String decision, String reviewerId, String reason, String createdAt) {}

    public record ToolActionView(String actionId, String ticketId, String toolName, String status,
                                 boolean requiresHumanApproval, String riskLevel, Map<String, Object> payload,
                                 String idempotencyKey, ApprovalView approval, Map<String, Object> executionResult,
                                 boolean idempotentReplay, String createdAt, String updatedAt) {}

    public record AgentRunView(String runId, String ticketId, String runType, String status,
                               List<String> retrievedDocIds, List<Map<String, Object>> toolCalls,
                               Map<String, Object> guardrailResults, String createdAt) {}

    public record EvalRunView(String evalRunId, String startedAt, String completedAt, int totalCases,
                              Map<String, Object> metrics, List<Map<String, Object>> caseResults,
                              List<Map<String, Object>> adversarialChecks) {}
}
