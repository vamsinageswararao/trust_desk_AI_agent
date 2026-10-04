package com.trustdesk.seed;

import com.trustdesk.common.Dates;
import com.trustdesk.config.TrustDeskProperties;
import com.trustdesk.domain.Customer;
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
import com.trustdesk.seed.SeedRecords.SeedBundle;
import com.trustdesk.seed.SeedRecords.SeedDocument;
import com.trustdesk.seed.SeedRecords.SeedTicket;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the provided seed data into the database in one transaction (all-or-nothing) and is safe to re-run.
 *
 * <ul>
 *   <li>Customers, orders, tool catalog, eval cases: upserted by primary key.</li>
 *   <li>Knowledge documents: upserted by {@code doc_id}; rewritten only when the content checksum changes.</li>
 *   <li>Tickets: inserted only if absent, so a restart never overwrites runtime state (status, triage, bodies).</li>
 * </ul>
 */
@Service
public class SeedDataService {

    private final TrustDeskProperties properties;
    private final CustomerRepository customers;
    private final OrderRepository orders;
    private final TicketRepository tickets;
    private final KnowledgeDocumentRepository documents;
    private final ToolCatalogEntryRepository toolCatalog;
    private final EvalCaseRepository evalCases;

    public SeedDataService(TrustDeskProperties properties, CustomerRepository customers, OrderRepository orders,
                           TicketRepository tickets, KnowledgeDocumentRepository documents,
                           ToolCatalogEntryRepository toolCatalog, EvalCaseRepository evalCases) {
        this.properties = properties;
        this.customers = customers;
        this.orders = orders;
        this.tickets = tickets;
        this.documents = documents;
        this.toolCatalog = toolCatalog;
        this.evalCases = evalCases;
    }

    @Transactional
    public SeedSummary load() {
        SeedBundle bundle = new SeedDataReader(Path.of(properties.seed().dataDir())).read();

        customers.saveAll(bundle.customers().stream()
                .map(c -> new Customer(c.customerId(), c.name(), c.email(), c.tier(), c.country(),
                        Dates.parseDate(c.createdAt()), c.verified(), c.tags()))
                .toList());

        toolCatalog.saveAll(bundle.toolActions().stream()
                .map(t -> new ToolCatalogEntry(t.toolName(), t.description(), t.riskLevel(),
                        t.requiresHumanApproval(), t.allowedCategories(), t.requiredFields(), t.maxAmountInr()))
                .toList());

        int documentsWritten = upsertDocuments(bundle.documents());

        orders.saveAll(bundle.orders().stream()
                .map(o -> new Order(o.orderId(), customers.getReferenceById(o.customerId()), o.status(),
                        Dates.parseDate(o.placedAt()), Dates.parseDate(o.deliveredAt()),
                        Dates.parseDate(o.eligibleReturnUntil()), o.total(), o.currency(), o.paymentStatus(),
                        o.trackingNumber(), o.items()))
                .toList());

        int ticketsInserted = insertMissingTickets(bundle.tickets());

        evalCases.saveAll(bundle.evalCases().stream()
                .map(c -> new EvalCase(c.caseId(), tickets.getReferenceById(c.ticketId()), c.input(), c.expected()))
                .toList());

        return new SeedSummary(bundle.customers().size(), bundle.orders().size(), bundle.tickets().size(),
                ticketsInserted, bundle.toolActions().size(), bundle.evalCases().size(),
                bundle.documents().size(), documentsWritten);
    }

    private int upsertDocuments(List<SeedDocument> seedDocuments) {
        int written = 0;
        for (SeedDocument d : seedDocuments) {
            Optional<KnowledgeDocument> existing = documents.findById(d.docId());
            if (existing.isPresent() && existing.get().getChecksum().equals(d.checksum())) {
                continue; // unchanged: keep original ingestion timestamp
            }
            documents.save(new KnowledgeDocument(d.docId(), d.title(), d.sourcePath(), d.version(), d.audience(),
                    d.content(), d.checksum()));
            written++;
        }
        return written;
    }

    private int insertMissingTickets(List<SeedTicket> seedTickets) {
        int inserted = 0;
        for (SeedTicket t : seedTickets) {
            if (tickets.existsById(t.ticketId())) {
                continue;
            }
            Order order = t.orderId() == null ? null : orders.getReferenceById(t.orderId());
            tickets.save(new Ticket(t.ticketId(), customers.getReferenceById(t.customerId()), order, t.channel(),
                    t.subject(), t.body(), Dates.parseOffset(t.createdAt()), t.status()));
            inserted++;
        }
        return inserted;
    }
}
