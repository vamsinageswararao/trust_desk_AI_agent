package com.trustdesk.api;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trustdesk.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

class AuthenticationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mvc.perform(get("/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(content().string(not(containsString("tkt_"))));
    }

    @Test
    void rejectsWrongToken() throws Exception {
        mvc.perform(get("/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsWrongSchemeAndEmptyBearer() throws Exception {
        mvc.perform(get("/tickets").header(HttpHeaders.AUTHORIZATION, "Basic " + TOKEN))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/tickets").header(HttpHeaders.AUTHORIZATION, "Bearer "))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/tickets").header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void doesNotAcceptTokenInQueryString() throws Exception {
        mvc.perform(get("/tickets").param("token", TOKEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectsDetailEndpointAndUnknownPathsToo() throws Exception {
        mvc.perform(get("/tickets/tkt_9001")).andExpect(status().isUnauthorized());
        mvc.perform(get("/no-such-endpoint")).andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsConfiguredToken() throws Exception {
        mvc.perform(get("/tickets").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isOk());
        mvc.perform(get("/tickets/tkt_9001").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isOk());
    }
}
