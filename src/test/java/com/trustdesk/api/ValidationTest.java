package com.trustdesk.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trustdesk.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class ValidationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder b) {
        return b.header(HttpHeaders.AUTHORIZATION, BEARER);
    }

    @Test
    void rejectsNegativePage() throws Exception {
        mvc.perform(authed(get("/tickets").param("page", "-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details", hasSize(1)))
                .andExpect(jsonPath("$.details[0].field").value("page"));
    }

    @Test
    void rejectsSizeOutOfRange() throws Exception {
        mvc.perform(authed(get("/tickets").param("size", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("size"));
        mvc.perform(authed(get("/tickets").param("size", "101")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("size"));
    }

    @Test
    void rejectsNonNumericPage() throws Exception {
        mvc.perform(authed(get("/tickets").param("page", "abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("page"));
    }

    @Test
    void rejectsMalformedStatusFilter() throws Exception {
        mvc.perform(authed(get("/tickets").param("status", "open; drop table tickets")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("status"));
    }

    @Test
    void reportsEveryInvalidParameter() throws Exception {
        mvc.perform(authed(get("/tickets").param("page", "-5").param("size", "500")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasSize(2)));
    }

    @Test
    void unsupportedMethodReturnsConsistentError() throws Exception {
        mvc.perform(authed(post("/tickets")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.status").value(405));
    }
}
