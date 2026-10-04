package com.trustdesk.service;

import com.trustdesk.agent.TicketContext;
import com.trustdesk.agent.ToolSpec;
import com.trustdesk.domain.Customer;
import com.trustdesk.domain.Order;
import com.trustdesk.domain.Ticket;
import com.trustdesk.domain.ToolCatalogEntry;
import com.trustdesk.repository.CustomerRepository;
import com.trustdesk.repository.ToolCatalogEntryRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Maps entities to the framework-free engine inputs. Call inside a transaction (lazy associations). */
@Component
public class AgentContextFactory {

    private final CustomerRepository customers;
    private final ToolCatalogEntryRepository toolCatalog;

    public AgentContextFactory(CustomerRepository customers, ToolCatalogEntryRepository toolCatalog) {
        this.customers = customers;
        this.toolCatalog = toolCatalog;
    }

    public TicketContext context(Ticket t) {
        Customer c = t.getCustomer();
        Order o = t.getOrder();
        Set<String> others = customers.findByCustomerIdNot(c.getCustomerId()).stream()
                .map(Customer::getEmail).collect(Collectors.toSet());
        return new TicketContext(t.getTicketId(), t.getChannel(), t.getSubject(), t.getBody(), t.getCreatedAt(),
                c.getCustomerId(), c.getName(), c.getEmail(), c.getTier(), c.isVerified(),
                o == null ? null : o.getOrderId(), o == null ? null : o.getStatus(),
                o == null ? null : o.getDeliveredAt(), o == null ? null : o.getEligibleReturnUntil(),
                o == null ? 0 : o.getTotal(), o == null ? null : o.getCurrency(),
                o == null ? null : o.getTrackingNumber(), o == null ? List.of() : o.getItems(), others);
    }

    public Map<String, ToolSpec> catalog() {
        Map<String, ToolSpec> map = new LinkedHashMap<>();
        for (ToolCatalogEntry e : toolCatalog.findAll()) {
            map.put(e.getToolName(), spec(e));
        }
        return map;
    }

    public ToolSpec spec(ToolCatalogEntry e) {
        return new ToolSpec(e.getToolName(), e.getRiskLevel(), e.isRequiresHumanApproval(),
                e.getAllowedCategories(), e.getRequiredFields(), e.getMaxAmountInr());
    }
}
