package com.trustdesk.repository;

import com.trustdesk.domain.DraftReply;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DraftReplyRepository extends JpaRepository<DraftReply, String> {
    List<DraftReply> findByTicket_TicketId(String ticketId);
}
