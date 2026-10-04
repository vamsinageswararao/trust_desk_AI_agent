package com.trustdesk.service.tools;

import com.trustdesk.domain.ToolAction;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OpenCarrierInvestigationExecutor implements ToolExecutor {

    @Override
    public String toolName() {
        return "open_carrier_investigation";
    }

    @Override
    public Map<String, Object> execute(ToolAction action) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "investigation_opened");
        r.put("tracking_number", action.getPayload().get("tracking_number"));
        r.put("message", "Carrier investigation opened (simulated). No replacement or refund is promised.");
        return r;
    }
}
