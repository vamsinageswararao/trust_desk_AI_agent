package com.trustdesk.service;

import com.trustdesk.api.dto.TicketListQuery;
import com.trustdesk.api.dto.TicketViews.PageResponse;
import com.trustdesk.api.dto.TicketViews.TicketDetail;
import com.trustdesk.api.dto.TicketViews.TicketSummary;
import com.trustdesk.common.NotFoundException;
import com.trustdesk.domain.Ticket;
import com.trustdesk.repository.TicketRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository tickets;

    public TicketService(TicketRepository tickets) {
        this.tickets = tickets;
    }

    /** Queue order: oldest ticket first (FIFO), ticket id as tie-breaker. */
    public PageResponse<TicketSummary> list(TicketListQuery query) {
        Pageable pageable = PageRequest.of(query.pageOrDefault(), query.sizeOrDefault(),
                Sort.by("createdAt").ascending().and(Sort.by("ticketId").ascending()));
        String status = query.statusOrNull();
        Page<Ticket> page = status == null ? tickets.findAll(pageable) : tickets.findByStatus(status, pageable);
        return new PageResponse<>(page.getContent().stream().map(TicketMapper::toSummary).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public TicketDetail get(String ticketId) {
        return tickets.findDetailedByTicketId(ticketId)
                .map(TicketMapper::toDetail)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + ticketId));
    }
}
