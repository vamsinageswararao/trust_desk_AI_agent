package com.trustdesk.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trustdesk.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

class TicketApiTest extends AbstractIntegrationTest {

    private static final List<String> ALL_TICKETS = List.of("tkt_9001", "tkt_9002", "tkt_9003", "tkt_9004",
            "tkt_9005", "tkt_9006", "tkt_9007", "tkt_9008");

    @Autowired MockMvc mvc;

    @Test
    void listsAllTicketsOldestFirst() throws Exception {
        mvc.perform(get("/tickets").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(8))
                .andExpect(jsonPath("$.items", hasSize(8)))
                .andExpect(jsonPath("$.items[*].ticket_id", contains("tkt_9003", "tkt_9001", "tkt_9004",
                        "tkt_9002", "tkt_9005", "tkt_9006", "tkt_9007", "tkt_9008")))
                .andExpect(jsonPath("$.items[1].subject").value("Received damaged earbuds"))
                .andExpect(jsonPath("$.items[1].customer_id").value("cus_1001"))
                .andExpect(jsonPath("$.items[1].order_id").value("ord_5001"));
    }

    @Test
    void paginates() throws Exception {
        mvc.perform(get("/tickets").param("page", "0").param("size", "3").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.total_pages").value(3))
                .andExpect(jsonPath("$.total_elements").value(8));
        mvc.perform(get("/tickets").param("page", "2").param("size", "3").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void filtersByStatus() throws Exception {
        mvc.perform(get("/tickets").param("status", "open").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(jsonPath("$.total_elements").value(8));
        mvc.perform(get("/tickets").param("status", "closed").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.total_elements").value(0));
    }

    @Test
    void returnsTicketWithLinkedCustomerAndOrder() throws Exception {
        mvc.perform(get("/tickets/tkt_9001").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket_id").value("tkt_9001"))
                .andExpect(jsonPath("$.channel").value("email"))
                .andExpect(jsonPath("$.body", containsString("left earbud cracked")))
                .andExpect(jsonPath("$.created_at").value("2026-06-28T10:15:00+05:30"))
                .andExpect(jsonPath("$.customer.customer_id").value("cus_1001"))
                .andExpect(jsonPath("$.customer.tier").value("gold"))
                .andExpect(jsonPath("$.customer.verified").value(true))
                .andExpect(jsonPath("$.order.order_id").value("ord_5001"))
                .andExpect(jsonPath("$.order.customer_id").value("cus_1001"))
                .andExpect(jsonPath("$.order.eligible_return_until").value("2026-07-01"))
                .andExpect(jsonPath("$.order.total").value(8999))
                .andExpect(jsonPath("$.order.items[0].sku").value("BG-AIRPODS-01"))
                .andExpect(jsonPath("$.order.items[0].final_sale").value(false));
    }

    @Test
    void exposesUnverifiedCustomerFlagForAccountSecurityTicket() throws Exception {
        mvc.perform(get("/tickets/tkt_9005").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.customer_id").value("cus_1003"))
                .andExpect(jsonPath("$.customer.verified").value(false))
                .andExpect(jsonPath("$.order.order_id").value("ord_5003"));
    }

    @Test
    void everySeedTicketResolvesItsCustomerAndOrder() throws Exception {
        for (String id : ALL_TICKETS) {
            mvc.perform(get("/tickets/" + id).header(HttpHeaders.AUTHORIZATION, BEARER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ticket_id").value(id))
                    .andExpect(jsonPath("$.customer.customer_id").isNotEmpty())
                    .andExpect(jsonPath("$.order.order_id").isNotEmpty());
        }
    }

    @Test
    void neverExposesSeedOnlyEvaluationLabels() throws Exception {
        mvc.perform(get("/tickets/tkt_9006").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(jsonPath("$.expected_category").doesNotExist())
                .andExpect(jsonPath("$.expected_priority").doesNotExist())
                .andExpect(jsonPath("$.expected_escalation").doesNotExist())
                .andExpect(jsonPath("$.expected_actions").doesNotExist());
        mvc.perform(get("/tickets").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(jsonPath("$.items[0].expected_category").doesNotExist());
    }

    @Test
    void unknownTicketReturnsConsistentNotFound() throws Exception {
        mvc.perform(get("/tickets/tkt_does_not_exist").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/tickets/tkt_does_not_exist"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
