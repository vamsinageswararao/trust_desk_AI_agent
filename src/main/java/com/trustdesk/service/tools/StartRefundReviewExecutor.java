package com.trustdesk.service.tools;

import com.trustdesk.domain.ToolAction;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Opens a human-reviewed refund workflow. It NEVER moves money: it records that a review was queued
 * (KB-REFUND-001: refund review needs human approval before any payment action; no refund is promised).
 */
@Component
public class StartRefundReviewExecutor implements ToolExecutor {

    @Override
    public String toolName() {
        return "start_refund_review";
    }

    @Override
    public Map<String, Object> execute(ToolAction action) {
        Map<String, Object> p = action.getPayload();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("refund_review_id", "rr_" + action.getActionId().replaceFirst("^act_", ""));
        r.put("status", "queued_for_human_review");
        r.put("order_id", p.get("order_id"));
        r.put("amount_under_review", p.get("amount"));
        r.put("money_moved", false);
        r.put("message", "Refund review started. No refund has been issued; any payment action needs separate human approval.");
        return r;
    }
}
