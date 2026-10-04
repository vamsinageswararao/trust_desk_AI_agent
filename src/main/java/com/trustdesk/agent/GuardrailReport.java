package com.trustdesk.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** decision: passed | escalated | blocked. Stored in the trace and returned to the UI. */
public record GuardrailReport(String decision, List<String> flags, List<String> violations, List<String> notes) {
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("decision", decision);
        m.put("flags", flags);
        m.put("violations", violations);
        m.put("notes", notes);
        return m;
    }
}
