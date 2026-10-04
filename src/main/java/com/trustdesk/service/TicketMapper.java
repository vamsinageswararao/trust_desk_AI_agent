package com.trustdesk.service;

import com.trustdesk.api.dto.TicketViews;
import com.trustdesk.api.dto.TicketViews.CustomerView;
import com.trustdesk.api.dto.TicketViews.OrderView;
import com.trustdesk.api.dto.TicketViews.TicketDetail;
import com.trustdesk.api.dto.TicketViews.TicketSummary;
import com.trustdesk.common.Dates;
import com.trustdesk.domain.Customer;
import com.trustdesk.domain.Order;
import com.trustdesk.domain.Ticket;

/** Entity -> API view mapping. Only explicitly listed fields are exposed. */
final class TicketMapper {
    private TicketMapper() {}

    static TicketSummary toSummary(Ticket t) {
        return new TicketSummary(t.getTicketId(), t.getCustomer().getCustomerId(),
                t.getOrder() == null ? null : t.getOrder().getOrderId(), t.getChannel(), t.getSubject(),
                t.getStatus(), Dates.format(t.getCreatedAt()),
                t.getTriageCategory(), t.getTriagePriority(), t.getTriageShouldEscalate());
    }

    static TicketDetail toDetail(Ticket t) {
        return new TicketDetail(t.getTicketId(), t.getChannel(), t.getSubject(), t.getBody(), t.getStatus(),
                Dates.format(t.getCreatedAt()), toCustomer(t.getCustomer()),
                t.getOrder() == null ? null : toOrder(t.getOrder()), toTriage(t));
    }

    private static TicketViews.TriageSummary toTriage(Ticket t) {
        if (t.getTriageCategory() == null) {
            return null;
        }
        return new TicketViews.TriageSummary(t.getTriageCategory(), t.getTriagePriority(), t.getTriageSentiment(),
                Boolean.TRUE.equals(t.getTriageShouldEscalate()), t.getTriageReason(), t.getTriageRunId(),
                t.getTriagedAt() == null ? null : t.getTriagedAt().toString());
    }

    private static CustomerView toCustomer(Customer c) {
        return new CustomerView(c.getCustomerId(), c.getName(), c.getEmail(), c.getTier(), c.getCountry(),
                Dates.format(c.getCreatedAt()), c.isVerified(), c.getTags());
    }

    private static OrderView toOrder(Order o) {
        return new OrderView(o.getOrderId(), o.getCustomer().getCustomerId(), o.getStatus(),
                Dates.format(o.getPlacedAt()), Dates.format(o.getDeliveredAt()),
                Dates.format(o.getEligibleReturnUntil()), o.getTotal(), o.getCurrency(), o.getPaymentStatus(),
                o.getTrackingNumber(), o.getItems());
    }
}
