package com.trustdesk.agent;

import com.trustdesk.guardrails.EvidencePolicy;
import java.util.List;
import java.util.Map;

/**
 * Everything produced for one draft, ready to persist and to show in a UI.
 * status: grounded | insufficient_evidence | ungrounded_fallback | model_error_fallback | output_blocked
 */
public record DraftOutcome(String body, List<Citation> citations, List<String> retrievedDocIds,
                           List<EvidencePolicy.Excluded> excluded, GuardrailReport guardrail,
                           List<RecommendedAction> recommendedActions, List<Map<String, Object>> blockedRecommendations,
                           String status, boolean requiresHumanHandling, List<String> explanation) {

    /** Machine-readable citation for the UI. */
    public record Citation(String docId, String title, String version, String sourcePath) {}

    public List<String> citationIds() {
        return citations.stream().map(Citation::docId).toList();
    }
}
