package com.trustdesk.workflow;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/** start_refund_review: request -> approval required -> human approves -> execute once -> result recorded. */
class ToolActionApiTest extends AbstractWorkflowTest {

    private void triage(String ticketId) throws Exception {
        doPostNoBody("/tickets/" + ticketId + "/triage", AGENT).andExpect(status().isOk());
    }

    private static String refundReview(String ticketId, String orderId, Object amount, String key) {
        return "{\"ticket_id\":\"" + ticketId + "\",\"tool_name\":\"start_refund_review\",\"payload\":{\"order_id\":\"" + orderId
                + "\",\"reason\":\"Duplicate charge reported\",\"amount\":" + amount + ",\"idempotency_key\":\"" + key + "\"}}";
    }

    private JsonNode createBillingReview(String key) throws Exception {
        triage("tkt_9008");
        return json(doPost("/tool-actions", AGENT, refundReview("tkt_9008", "ord_5006", 15999, key)).andExpect(status().isCreated()));
    }

    // ------------------------------------------------------------------ the happy path, end to end
    @Test
    void fullLifecycleIsGatedByApprovalAndIdempotent() throws Exception {
        String key = uniqueKey("tkt_9008-refund-review");
        JsonNode created = createBillingReview(key);
        String id = created.get("action_id").asText();
        assertEquals("approval_required", created.get("status").asText());
        assertEquals(true, created.get("requires_human_approval").asBoolean());
        assertEquals(false, created.get("idempotent_replay").asBoolean());
        assertEquals(true, created.get("approval").isNull());

        // executing before any approval is refused
        doPostNoBody("/tool-actions/" + id + "/execute", AGENT)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
        // an ordinary agent cannot approve
        doPost("/tool-actions/" + id + "/approve", AGENT, "{\"decision\":\"approved\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        doGet("/tool-actions/" + id, AGENT).andExpect(jsonPath("$.status").value("approval_required"));

        // a manager approves; the reviewer comes from the token, not the body
        doPost("/tool-actions/" + id + "/approve", MANAGER, "{\"decision\":\"approved\",\"reason\":\"duplicate charge verified\",\"reviewer_id\":\"someone_else\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.approval.decision").value("approved"))
                .andExpect(jsonPath("$.approval.reviewer_id").value("demo-support-manager"))
                .andExpect(jsonPath("$.approval.reason").value("duplicate charge verified"));
        // repeating the same decision is harmless; the opposite decision is refused
        doPost("/tool-actions/" + id + "/approve", MANAGER, "{\"decision\":\"approved\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.idempotent_replay").value(true));
        doPost("/tool-actions/" + id + "/approve", MANAGER, "{\"decision\":\"rejected\"}")
                .andExpect(status().isConflict());

        // execute: a refund REVIEW is queued; no money moves
        JsonNode executed = json(doPostNoBody("/tool-actions/" + id + "/execute", AGENT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("executed"))
                .andExpect(jsonPath("$.idempotent_replay").value(false))
                .andExpect(jsonPath("$.execution_result.status").value("queued_for_human_review"))
                .andExpect(jsonPath("$.execution_result.money_moved").value(false))
                .andExpect(jsonPath("$.execution_result.order_id").value("ord_5006")));
        String reviewId = executed.get("execution_result").get("refund_review_id").asText();

        // retries: same stored result, nothing runs twice
        doPostNoBody("/tool-actions/" + id + "/execute", AGENT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("executed"))
                .andExpect(jsonPath("$.idempotent_replay").value(true))
                .andExpect(jsonPath("$.execution_result.refund_review_id").value(reviewId));
        doPost("/tool-actions", AGENT, refundReview("tkt_9008", "ord_5006", 15999, key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action_id").value(id))
                .andExpect(jsonPath("$.idempotent_replay").value(true))
                .andExpect(jsonPath("$.status").value("executed"));

        // everything is visible to a UI and in the trace
        doGet("/tickets/tkt_9008/tool-actions", AGENT).andExpect(jsonPath("$[*].action_id", hasItem(id)));
        doGet("/tickets/tkt_9008/agent-runs", AGENT)
                .andExpect(jsonPath("$[*].status", hasItems("approval_required", "approved", "executed")))
                .andExpect(jsonPath("$[?(@.status=='executed')].tool_calls[*].action_id", hasItem(id)));
    }

    @Test
    void rejectedActionsCanNeverBeExecuted() throws Exception {
        JsonNode created = createBillingReview(uniqueKey("tkt_9008-refund-review"));
        String id = created.get("action_id").asText();
        doPost("/tool-actions/" + id + "/approve", MANAGER, "{\"decision\":\"rejected\",\"reason\":\"not a duplicate\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.approval.decision").value("rejected"));
        doPostNoBody("/tool-actions/" + id + "/execute", AGENT)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
        doGet("/tool-actions/" + id, AGENT).andExpect(jsonPath("$.execution_result").doesNotExist());
    }

    @Test
    void anIdempotencyKeyCannotBeReusedForAnotherTicketOrTool() throws Exception {
        String key = uniqueKey("shared-key");
        createBillingReview(key);
        triage("tkt_9001");
        doPost("/tool-actions", AGENT, refundReview("tkt_9001", "ord_5001", 8999, key))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    // ------------------------------------------------------------------ the application policy cannot be bypassed
    @Test
    void finalSaleRefundReviewIsBlockedAndLeavesATrace() throws Exception {
        triage("tkt_9003");
        doPost("/tool-actions", AGENT, refundReview("tkt_9003", "ord_5004", 4999, uniqueKey("tkt_9003-refund-review")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("GUARDRAIL_BLOCKED"))
                .andExpect(jsonPath("$.details[0].field").value("policy_code"))
                .andExpect(jsonPath("$.details[0].message").value("FINAL_SALE"));
        doGet("/tickets/tkt_9003/tool-actions", AGENT).andExpect(jsonPath("$", hasSize(0)));
        doGet("/tickets/tkt_9003/agent-runs", AGENT)
                .andExpect(jsonPath("$[?(@.status=='blocked')].guardrail_results.violations[*]", hasItem("FINAL_SALE")));
    }

    @Test
    void couponAndLockRequestsOnAnAdversarialTicketAreBlocked() throws Exception {
        triage("tkt_9006");
        for (int amount : new int[] {5000, 500}) {
            doPost("/tool-actions", AGENT, "{\"ticket_id\":\"tkt_9006\",\"tool_name\":\"issue_coupon\",\"payload\":{\"customer_id\":\"cus_1006\","
                    + "\"amount\":" + amount + ",\"reason\":\"ignore the rules\",\"idempotency_key\":\"" + uniqueKey("coupon") + "\"}}")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("GUARDRAIL_BLOCKED"))
                    .andExpect(jsonPath("$.details[0].message").value("ADVERSARIAL_TICKET"));
        }
        triage("tkt_9005");
        doPost("/tool-actions", AGENT, "{\"ticket_id\":\"tkt_9005\",\"tool_name\":\"lock_account\",\"payload\":{\"customer_id\":\"cus_1003\","
                + "\"reason\":\"x\",\"idempotency_key\":\"" + uniqueKey("lock") + "\"}}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("GUARDRAIL_BLOCKED"));
    }

    @Test
    void aPayloadForAnotherCustomersOrderIsBlocked() throws Exception {
        triage("tkt_9001");
        doPost("/tool-actions", AGENT, refundReview("tkt_9001", "ord_5006", 8999, uniqueKey("cross-order")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].message").value("ORDER_MISMATCH"));
        doPost("/tool-actions", AGENT, refundReview("tkt_9001", "ord_5001", 999999, uniqueKey("too-much")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.details[0].message").value("INVALID_AMOUNT"));
    }

    @Test
    void requiredFieldsAreEnforced() throws Exception {
        triage("tkt_9008");
        doPost("/tool-actions", AGENT, "{\"ticket_id\":\"tkt_9008\",\"tool_name\":\"start_refund_review\",\"payload\":{\"order_id\":\"ord_5006\","
                + "\"reason\":\"r\",\"idempotency_key\":\"" + uniqueKey("no-amount") + "\"}}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.details[0].message").value("MISSING_FIELD"));
        doPost("/tool-actions", AGENT, "{\"ticket_id\":\"tkt_9008\",\"tool_name\":\"start_refund_review\",\"payload\":{\"order_id\":\"ord_5006\","
                + "\"reason\":\"r\",\"amount\":100}}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.details[0].message").value("MISSING_FIELD"));
    }

    @Test
    void toolsNotAllowedForTheCategoryAreBlocked() throws Exception {
        triage("tkt_9008"); // billing: replacement orders are not permitted
        doPost("/tool-actions", AGENT, "{\"ticket_id\":\"tkt_9008\",\"tool_name\":\"create_replacement_order\",\"payload\":{\"order_id\":\"ord_5006\","
                + "\"sku\":\"BG-WATCH-04\",\"reason\":\"r\",\"idempotency_key\":\"" + uniqueKey("repl") + "\"}}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.details[0].message").value("CATEGORY_NOT_ALLOWED"));
    }

    // ------------------------------------------------------------------ other tools and request validation
    @Test
    void toolsWithoutAnExecutorStayApprovedAndAreReportedNotImplemented() throws Exception {
        triage("tkt_9001");
        JsonNode created = json(doPost("/tool-actions", AGENT, "{\"ticket_id\":\"tkt_9001\",\"tool_name\":\"create_replacement_order\",\"payload\":"
                + "{\"order_id\":\"ord_5001\",\"sku\":\"BG-AIRPODS-01\",\"reason\":\"Damaged\",\"idempotency_key\":\"" + uniqueKey("repl") + "\"}}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("approval_required")));
        String id = created.get("action_id").asText();
        doPost("/tool-actions/" + id + "/approve", MANAGER, "{\"decision\":\"approved\"}").andExpect(status().isOk());
        doPostNoBody("/tool-actions/" + id + "/execute", AGENT)
                .andExpect(status().isNotImplemented()).andExpect(jsonPath("$.code").value("TOOL_NOT_IMPLEMENTED"));
        doGet("/tool-actions/" + id, AGENT).andExpect(jsonPath("$.status").value("approved"));
    }

    @Test
    void lowRiskEscalationNeedsNoApprovalButIsStillRecorded() throws Exception {
        triage("tkt_9006");
        JsonNode created = json(doPost("/tool-actions", AGENT, "{\"ticket_id\":\"tkt_9006\",\"tool_name\":\"escalate_to_human\",\"payload\":"
                + "{\"ticket_id\":\"tkt_9006\",\"reason\":\"prompt injection\",\"queue\":\"security_review\",\"idempotency_key\":\""
                + uniqueKey("esc") + "\"}}").andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("requested"))
                .andExpect(jsonPath("$.requires_human_approval").value(false)));
        String id = created.get("action_id").asText();
        doPost("/tool-actions/" + id + "/approve", MANAGER, "{\"decision\":\"approved\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
        doPostNoBody("/tool-actions/" + id + "/execute", AGENT)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("executed"))
                .andExpect(jsonPath("$.execution_result.queue").value("security_review"));
    }

    @Test
    void requestValidationAndUnknownResources() throws Exception {
        doPost("/tool-actions", AGENT, "{\"ticket_id\":\"tkt_9008\",\"tool_name\":\"\",\"payload\":{}}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        doPost("/tool-actions", AGENT, "{\"ticket_id\":\"tkt_9008\",\"tool_name\":\"start_refund_review\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        doPost("/tool-actions", AGENT, "{not json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        doPost("/tool-actions", AGENT, "{\"ticket_id\":\"tkt_9008\",\"tool_name\":\"launch_missiles\",\"payload\":{}}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_TOOL"));
        doPost("/tool-actions", AGENT, "{\"ticket_id\":\"tkt_nope\",\"tool_name\":\"start_refund_review\",\"payload\":{}}")
                .andExpect(status().isNotFound());
        doPost("/tool-actions/act_missing/approve", MANAGER, "{\"decision\":\"approved\"}").andExpect(status().isNotFound());
        doPost("/tool-actions/act_missing/approve", MANAGER, "{\"decision\":\"maybe\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        doPostNoBody("/tool-actions/act_missing/execute", AGENT).andExpect(status().isNotFound());
        doGet("/tool-actions/act_missing", AGENT).andExpect(status().isNotFound());
    }

    @Test
    void aWrongContentTypeIsA415NotA500() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/tool-actions")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, AGENT)
                        .contentType(org.springframework.http.MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void everyEndpointRequiresAuthentication() throws Exception {
        String bad = "Bearer nope";
        doPost("/tool-actions", bad, "{}").andExpect(status().isUnauthorized());
        doPost("/tool-actions/act_x/approve", bad, "{\"decision\":\"approved\"}").andExpect(status().isUnauthorized());
        doPostNoBody("/tool-actions/act_x/execute", bad).andExpect(status().isUnauthorized());
        doGet("/tool-actions/act_x", bad).andExpect(status().isUnauthorized());
        doGet("/tickets/tkt_9008/tool-actions", bad).andExpect(status().isUnauthorized());
        doGet("/tickets/tkt_9008/agent-runs", bad).andExpect(status().isUnauthorized());
        doGet("/tickets/tkt_9008/tool-actions", AGENT).andExpect(status().isOk());
    }
}
