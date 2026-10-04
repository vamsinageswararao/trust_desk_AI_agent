package com.trustdesk.repository;

import com.trustdesk.domain.Approval;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRepository extends JpaRepository<Approval, String> {
    List<Approval> findByAction_ActionId(String actionId);
}
