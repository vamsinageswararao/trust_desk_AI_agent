package com.trustdesk.workflow;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class EvalRunApiTest extends AbstractWorkflowTest {

    @Test
    void requiresAuthentication() throws Exception {
        doPostNoBody("/eval-runs", "Bearer nope").andExpect(status().isUnauthorized());
        doGet("/eval-runs", "Bearer nope").andExpect(status().isUnauthorized());
    }

    @Test
    void runsAllSeededCasesAndReportsTheRequiredMetrics() throws Exception {
        JsonNode run = json(doPostNoBody("/eval-runs", AGENT)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eval_run_id", startsWith("eval_run_")))
                .andExpect(jsonPath("$.total_cases").value(8))
                .andExpect(jsonPath("$.metrics.total_cases").value(8))
                .andExpect(jsonPath("$.metrics.cases_passed").value(8))
                .andExpect(jsonPath("$.metrics.category_accuracy").value(1.0))
                .andExpect(jsonPath("$.metrics.priority_accuracy").value(1.0))
                .andExpect(jsonPath("$.metrics.citation_coverage").value(1.0))
                .andExpect(jsonPath("$.metrics.unsafe_action_block_rate").value(1.0))
                .andExpect(jsonPath("$.metrics.allowed_action_recall").value(1.0))
                .andExpect(jsonPath("$.metrics.escalation_accuracy").value(1.0))
                .andExpect(jsonPath("$.case_results", hasSize(8)))
                .andExpect(jsonPath("$.case_results[*].case_id",
                        hasItems("eval_001", "eval_002", "eval_003", "eval_004", "eval_005", "eval_006", "eval_007", "eval_008")))
                .andExpect(jsonPath("$.case_results[?(@.passed==false)]", hasSize(0))));
        doGet("/eval-runs/" + run.get("eval_run_id").asText(), AGENT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.cases_passed").value(8))
                .andExpect(jsonPath("$.case_results", hasSize(8)));
        doGet("/eval-runs", AGENT).andExpect(jsonPath("$[*].eval_run_id", hasItem(run.get("eval_run_id").asText())));
        doGet("/eval-runs/eval_run_missing", AGENT).andExpect(status().isNotFound());
    }

    @Test
    void callsOutTheThreeAdversarialCasesExplicitly() throws Exception {
        doPostNoBody("/eval-runs", AGENT)
                .andExpect(jsonPath("$.adversarial_checks[*].case_id", hasItems("eval_005", "eval_006", "eval_007")))
                .andExpect(jsonPath("$.adversarial_checks[?(@.safe==false)]", hasSize(0)));
    }

    @Test
    void canRunASubsetAndRejectsUnknownCases() throws Exception {
        doPost("/eval-runs", AGENT, "{\"case_ids\":[\"eval_006\"]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total_cases").value(1))
                .andExpect(jsonPath("$.case_results[0].case_id").value("eval_006"))
                .andExpect(jsonPath("$.case_results[0].unsafe_actions_recommended", hasSize(0)));
        doPost("/eval-runs", AGENT, "{}").andExpect(status().isCreated()).andExpect(jsonPath("$.total_cases").value(8));
        doPost("/eval-runs", AGENT, "{\"case_ids\":[\"eval_999\"]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void aBodylessPostWithAnyContentTypeStillWorks() throws Exception {
        // PowerShell's Invoke-RestMethod adds application/x-www-form-urlencoded to body-less POSTs
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/eval-runs")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, AGENT)
                        .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total_cases").value(8));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/eval-runs")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, AGENT)
                        .contentType(org.springframework.http.MediaType.TEXT_PLAIN).content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void evaluationLeavesTicketsAndDraftsUntouchedButWritesTraces() throws Exception {
        int draftsBefore = json(doGet("/tickets/tkt_9002/draft-replies", AGENT)).size();
        doPostNoBody("/eval-runs", AGENT).andExpect(status().isCreated());
        org.junit.jupiter.api.Assertions.assertEquals(draftsBefore, json(doGet("/tickets/tkt_9002/draft-replies", AGENT)).size());
        doGet("/tickets/tkt_9006/agent-runs", AGENT).andExpect(jsonPath("$[*].run_type", hasItem("eval_case")));
    }
}
