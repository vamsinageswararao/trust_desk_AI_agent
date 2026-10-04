package com.trustdesk.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/** A tool action the agent RECOMMENDS. Nothing is executed or even recorded as a request until a human acts. */
public record RecommendedAction(String toolName, boolean requiresHumanApproval, String riskLevel, String reason,
                                Map<String, Object> payload) {
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tool_name", toolName);
        m.put("requires_human_approval", requiresHumanApproval);
        m.put("risk_level", riskLevel);
        m.put("reason", reason);
        m.put("payload", payload);
        m.put("status", "recommended");
        return m;
    }
}
