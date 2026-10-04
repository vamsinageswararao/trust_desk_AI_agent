package com.trustdesk.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustdesk.AbstractIntegrationTest;
import com.trustdesk.common.Dates;
import com.trustdesk.domain.EvalCase;
import com.trustdesk.domain.KnowledgeDocument;
import com.trustdesk.domain.Order;
import com.trustdesk.domain.Ticket;
import com.trustdesk.domain.ToolCatalogEntry;
import com.trustdesk.repository.CustomerRepository;
import com.trustdesk.repository.EvalCaseRepository;
import com.trustdesk.repository.KnowledgeDocumentRepository;
import com.trustdesk.repository.OrderRepository;
import com.trustdesk.repository.TicketRepository;
import com.trustdesk.repository.ToolCatalogEntryRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SeedDataLoadingTest extends AbstractIntegrationTest {

    private static final Set<String> EXPECTED_DOC_IDS = Set.of("KB-ACCOUNT-001", "KB-ADVERSARIAL-001",
            "KB-BILLING-001", "KB-COUPON-001", "KB-REFUND-001", "KB-SECURITY-001", "KB-SHIPPING-001",
            "KB-WARRANTY-001");

    @Autowired SeedDataService seedDataService;
    @Autowired CustomerRepository customers;
    @Autowired OrderRepository orders;
    @Autowired TicketRepository tickets;
    @Autowired KnowledgeDocumentRepository documents;
    @Autowired ToolCatalogEntryRepository toolCatalog;
    @Autowired EvalCaseRepository evalCases;

    @Test
    void loadsAllProvidedRecords() {
        assertThat(customers.count()).isEqualTo(6);
        assertThat(orders.count()).isEqualTo(6);
        assertThat(tickets.count()).isEqualTo(8);
        assertThat(documents.count()).isEqualTo(8);
        assertThat(toolCatalog.count()).isEqualTo(6);
        assertThat(evalCases.count()).isEqualTo(8);
    }

    @Test
    void preservesKnowledgeBaseDocumentIdsExactly() {
        Set<String> ids = documents.findAll().stream().map(KnowledgeDocument::getDocId).collect(Collectors.toSet());
        assertThat(ids).isEqualTo(EXPECTED_DOC_IDS);
    }

    @Test
    void storesDocumentContentAndMetadataUnchanged() throws IOException {
        for (KnowledgeDocument doc : documents.findAll()) {
            String fileContent = Files.readString(Path.of(doc.getSourcePath()));
            assertThat(doc.getContent()).as(doc.getDocId()).isEqualTo(fileContent);
            assertThat(fileContent).contains("Doc ID: " + doc.getDocId());
            assertThat(doc.getChecksum()).hasSize(64);
            assertThat(doc.getIngestedAt()).isNotNull();
        }
        KnowledgeDocument refund = documents.findById("KB-REFUND-001").orElseThrow();
        assertThat(refund.getTitle()).isEqualTo("Refund and Return Policy");
        assertThat(refund.getVersion()).isEqualTo("2026.07");
        assertThat(refund.getAudience()).isEqualTo("Customer support agents");
        assertThat(refund.getSourcePath()).isEqualTo("data/knowledge_base/refund_policy.md");
        assertThat(documents.findById("KB-ADVERSARIAL-001").orElseThrow().getAudience())
                .isEqualTo("Imported vendor documentation");
    }

    @Test
    void ticketsMatchSeedFileIncludingOriginalOffsetAndBody() throws IOException {
        JsonNode seed = new ObjectMapper().readTree(Path.of("data/tickets.json").toFile());
        assertThat(seed).hasSize(8);
        for (JsonNode node : seed) {
            Ticket t = tickets.findById(node.get("ticket_id").asText()).orElseThrow();
            assertThat(t.getSubject()).isEqualTo(node.get("subject").asText());
            assertThat(t.getBody()).isEqualTo(node.get("body").asText());
            assertThat(Dates.format(t.getCreatedAt())).isEqualTo(node.get("created_at").asText());
            assertThat(t.getStatus()).isEqualTo(node.get("status").asText());
        }
        assertThat(Dates.format(tickets.findById("tkt_9001").orElseThrow().getCreatedAt()))
                .isEqualTo("2026-06-28T10:15:00+05:30");
    }

    @Test
    void ordersKeepItemsDatesAndNulls() {
        Order o1 = orders.findById("ord_5001").orElseThrow();
        assertThat(o1.getTotal()).isEqualTo(8999);
        assertThat(o1.getEligibleReturnUntil()).hasToString("2026-07-01");
        assertThat(o1.getItems()).hasSize(1);
        assertThat(o1.getItems().get(0).sku()).isEqualTo("BG-AIRPODS-01");
        assertThat(o1.getItems().get(0).finalSale()).isFalse();

        assertThat(orders.findById("ord_5004").orElseThrow().getItems().get(0).finalSale()).isTrue();

        Order inTransit = orders.findById("ord_5002").orElseThrow();
        assertThat(inTransit.getDeliveredAt()).isNull();
        assertThat(inTransit.getEligibleReturnUntil()).isNull();
    }

    @Test
    void toolCatalogCapturesApprovalRiskAndLimits() {
        ToolCatalogEntry refund = toolCatalog.findById("start_refund_review").orElseThrow();
        assertThat(refund.isRequiresHumanApproval()).isTrue();
        assertThat(refund.getRiskLevel()).isEqualTo("medium");
        assertThat(refund.getAllowedCategories()).containsExactly("refund", "billing");
        assertThat(refund.getRequiredFields()).contains("order_id", "reason", "amount", "idempotency_key");
        assertThat(toolCatalog.findById("issue_coupon").orElseThrow().getMaxAmountInr()).isEqualTo(1000);
        assertThat(toolCatalog.findById("open_carrier_investigation").orElseThrow().isRequiresHumanApproval())
                .isFalse();
    }

    @Test
    void evalCasesAreStoredSeparatelyFromTickets() {
        EvalCase c = evalCases.findById("eval_006").orElseThrow();
        assertThat(c.getExpected().get("should_escalate")).isEqualTo(true);
        assertThat(stringList(c.getExpected().get("must_cite_doc_ids"))).containsExactly("KB-SECURITY-001");
        assertThat(stringList(c.getExpected().get("disallowed_actions"))).containsExactly("issue_coupon");
    }

    /** Converts a JSON-decoded value to a typed List<String>, failing the test if it is not a list of strings. */
    private static List<String> stringList(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return ((List<?>) value).stream()
                .map(element -> {
                    assertThat(element).isInstanceOf(String.class);
                    return (String) element;
                })
                .toList();
    }

    @Test
    void reloadIsIdempotent() {
        SeedSummary again = seedDataService.load();
        assertThat(again.ticketsInserted()).isZero();
        assertThat(again.documentsWritten()).isZero();
        assertThat(again.customers()).isEqualTo(6);
        assertThat(customers.count()).isEqualTo(6);
        assertThat(orders.count()).isEqualTo(6);
        assertThat(tickets.count()).isEqualTo(8);
        assertThat(documents.count()).isEqualTo(8);
        assertThat(toolCatalog.count()).isEqualTo(6);
        assertThat(evalCases.count()).isEqualTo(8);
    }
}
