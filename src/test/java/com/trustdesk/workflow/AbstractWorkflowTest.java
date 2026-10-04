package com.trustdesk.workflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Workflow tests (triage, drafts, tool actions, traces, evaluation) WRITE data, so they run in their own Spring context
 * with their own SQLite file and the deterministic mock AI provider. The Milestone 1/2 tests keep their separate,
 * pristine context. Tests here share one database, so each uses unique idempotency keys and never assumes empty tables.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "trustdesk.ai.provider=mock")
public abstract class AbstractWorkflowTest {

    public static final String AGENT = "Bearer test-demo-token";
    public static final String MANAGER = "Bearer test-manager-token";

    private static final String DB_URL = "jdbc:sqlite:target/trustdesk-workflow-" + UUID.randomUUID() + ".db?foreign_keys=on";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB_URL);
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;

    protected ResultActions doGet(String url, String auth) throws Exception {
        return mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, auth));
    }

    protected ResultActions doPost(String url, String auth, String body) throws Exception {
        return mvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected ResultActions doPostNoBody(String url, String auth) throws Exception {
        return mvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, auth));
    }

    protected JsonNode json(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected static String uniqueKey(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
