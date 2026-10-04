package com.trustdesk.repository;

import com.trustdesk.domain.AgentRun;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunRepository extends JpaRepository<AgentRun, String> {
    List<AgentRun> findByTicket_TicketId(String ticketId);
}
