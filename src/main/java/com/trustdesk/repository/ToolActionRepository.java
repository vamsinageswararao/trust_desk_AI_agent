package com.trustdesk.repository;

import com.trustdesk.domain.ToolAction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolActionRepository extends JpaRepository<ToolAction, String> {
    Optional<ToolAction> findByIdempotencyKey(String idempotencyKey);

    List<ToolAction> findByTicket_TicketId(String ticketId);
}
