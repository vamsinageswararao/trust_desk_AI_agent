package com.trustdesk.repository;

import com.trustdesk.domain.Ticket;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, String> {

    @Override
    @EntityGraph(attributePaths = {"customer", "order"})
    Page<Ticket> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "order"})
    Page<Ticket> findByStatus(String status, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "order"})
    Optional<Ticket> findDetailedByTicketId(String ticketId);
}
