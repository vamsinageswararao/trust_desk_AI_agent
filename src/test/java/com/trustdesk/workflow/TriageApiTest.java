package com.trustdesk.workflow;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class TriageApiTest extends AbstractWorkflowTest {

    @Test
    void requiresAuthentication() throws Exception {
        doPostNoBody("/tickets/tkt_9001/triage", "Bearer nope").andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/tickets/tkt_9001/triage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void triagesADamagedItemTicket() throws Exception {
        doPostNoBody("/tickets/tkt_9001/triage", AGENT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket_id").value("tkt_9001"))
                .andExpect(jsonPath("$.category").value("refund"))
                .andExpect(jsonPath("$.priority").value("medium"))
                .andExpect(jsonPath("$.should_escalate").value(false))
                .andExpect(jsonPath("$.source").value("model"))
                .andExpect(jsonPath("$.run_id", startsWith("run_")))
                .andExpect(jsonPath("$.guardrail.decision").value("passed"))
                .andExpect(jsonPath("$.expected_category").doesNotExist());
    }

    @Test
    void persistsTheResultOnTheTicket() throws Exception {
        doPostNoBody("/tickets/tkt_9002/triage", AGENT).andExpect(status().isOk());
        doGet("/tickets/tkt_9002", AGENT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.triage.category").value("shipping"))
                .andExpect(jsonPath("$.triage.priority").value("high"))
                .andExpect(jsonPath("$.triage.should_escalate").value(false))
                .andExpect(jsonPath("$.triage.run_id", startsWith("run_")));
        doGet("/tickets", AGENT)
                .andExpect(jsonPath("$.items[?(@.ticket_id=='tkt_9002')].triage_category", hasItem("shipping")));
    }

    @Test
    void escalatesSafetyAndAdversarialTickets() throws Exception {
        doPostNoBody("/tickets/tkt_9004/triage", AGENT)
                .andExpect(jsonPath("$.category").value("warranty"))
                .andExpect(jsonPath("$.priority").value("urgent"))
                .andExpect(jsonPath("$.should_escalate").value(true));
        doPostNoBody("/tickets/tkt_9005/triage", AGENT)
                .andExpect(jsonPath("$.category").value("account_security"))
                .andExpect(jsonPath("$.priority").value("high"))
                .andExpect(jsonPath("$.should_escalate").value(true))
                .andExpect(jsonPath("$.guardrail.flags", hasItem("IDENTITY_BYPASS")));
        doPostNoBody("/tickets/tkt_9006/triage", AGENT)
                .andExpect(jsonPath("$.category").value("general"))
                .andExpect(jsonPath("$.priority").value("medium"))
                .andExpect(jsonPath("$.should_escalate").value(true))
                .andExpect(jsonPath("$.guardrail.flags", hasItem("HIDDEN_ACTION_REQUEST")));
        doPostNoBody("/tickets/tkt_9007/triage", AGENT)
                .andExpect(jsonPath("$.category").value("account_security"))
                .andExpect(jsonPath("$.should_escalate").value(true))
                .andExpect(jsonPath("$.guardrail.flags", hasItem("SECRET_EXTRACTION")));
    }

    @Test
    void unknownTicketIsNotFound() throws Exception {
        doPostNoBody("/tickets/tkt_nope/triage", AGENT)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void everyRunWritesAReadableTrace() throws Exception {
        JsonNode first = json(doPostNoBody("/tickets/tkt_9008/triage", AGENT));
        JsonNode second = json(doPostNoBody("/tickets/tkt_9008/triage", AGENT));
        org.junit.jupiter.api.Assertions.assertNotEquals(first.get("run_id").asText(), second.get("run_id").asText());
        doGet("/agent-runs/" + first.get("run_id").asText(), AGENT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run_id").value(first.get("run_id").asText()))
                .andExpect(jsonPath("$.ticket_id").value("tkt_9008"))
                .andExpect(jsonPath("$.run_type").value("triage"))
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.guardrail_results.decision").value("passed"))
                .andExpect(jsonPath("$.guardrail_results.explanation[0]").exists());
        doGet("/agent-runs/run_missing", AGENT).andExpect(status().isNotFound());
        doGet("/agent-runs/" + first.get("run_id").asText(), "Bearer nope").andExpect(status().isUnauthorized());
    }
}
