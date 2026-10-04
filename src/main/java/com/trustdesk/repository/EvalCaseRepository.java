package com.trustdesk.repository;

import com.trustdesk.domain.EvalCase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalCaseRepository extends JpaRepository<EvalCase, String> {
}
