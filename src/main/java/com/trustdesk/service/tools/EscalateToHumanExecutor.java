package com.trustdesk.service.tools;

import com.trustdesk.domain.ToolAction;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EscalateToHumanExecutor implements ToolExecutor {

    @Override
    public String toolName() {
        return "escalate_to_human";
    }

    @Override
    public Map<String, Object> execute(ToolAction action) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "escalated");
        r.put("queue", action.getPayload().get("queue"));
        r.put("message", "Ticket routed to a human specialist (simulated).");
        return r;
    }
}
