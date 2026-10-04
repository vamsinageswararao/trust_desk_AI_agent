package com.trustdesk.api;

import com.trustdesk.api.dto.TicketListQuery;
import com.trustdesk.api.dto.TicketViews.PageResponse;
import com.trustdesk.api.dto.TicketViews.TicketDetail;
import com.trustdesk.api.dto.TicketViews.TicketSummary;
import com.trustdesk.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tickets")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<TicketSummary> list(@Valid TicketListQuery query) {
        return service.list(query);
    }

    @GetMapping("/{ticketId}")
    public TicketDetail get(@PathVariable String ticketId) {
        return service.get(ticketId);
    }
}
