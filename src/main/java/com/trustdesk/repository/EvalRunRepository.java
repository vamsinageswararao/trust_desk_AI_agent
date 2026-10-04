package com.trustdesk.repository;

import com.trustdesk.domain.EvalRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalRunRepository extends JpaRepository<EvalRun, String> {
}
