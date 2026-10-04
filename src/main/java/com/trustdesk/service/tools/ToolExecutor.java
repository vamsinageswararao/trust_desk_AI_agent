package com.trustdesk.service.tools;

import com.trustdesk.domain.ToolAction;
import java.util.Map;

/** Performs one tool's effect. Only called after the approval gate (if required) and policy re-check. */
public interface ToolExecutor {
    String toolName();

    /** Must be deterministic and free of external side effects in this capstone (results are simulated/recorded). */
    Map<String, Object> execute(ToolAction action);
}
