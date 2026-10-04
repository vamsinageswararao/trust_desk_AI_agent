package com.trustdesk.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.trustdesk.AbstractIntegrationTest;
import com.trustdesk.repository.TicketRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class TicketRelationshipTest extends AbstractIntegrationTest {

    @Autowired TicketRepository tickets;

    @Test
    void ticketLinksToCustomerAndOrder() {
        Ticket t = tickets.findById("tkt_9007").orElseThrow();
        assertThat(t.getCustomer().getCustomerId()).isEqualTo("cus_1001");
        assertThat(t.getOrder().getOrderId()).isEqualTo("ord_5001");
        assertThat(t.getOrder().getCustomer().getCustomerId()).isEqualTo("cus_1001");
    }

    @Test
    void everyTicketsOrderBelongsToTheSameCustomer() {
        List<Ticket> all = tickets.findAll();
        assertThat(all).hasSize(8);
        for (Ticket t : all) {
            assertThat(t.getOrder()).as(t.getTicketId()).isNotNull();
            assertThat(t.getOrder().getCustomer().getCustomerId())
                    .as(t.getTicketId()).isEqualTo(t.getCustomer().getCustomerId());
        }
    }

    @Test
    void triageColumnsAreEmptyUntilMilestone2() {
        for (Ticket t : tickets.findAll()) {
            assertThat(t.getTriageCategory()).isNull();
            assertThat(t.getTriagePriority()).isNull();
            assertThat(t.getTriageShouldEscalate()).isNull();
            assertThat(t.getTriageRunId()).isNull();
        }
    }
}
