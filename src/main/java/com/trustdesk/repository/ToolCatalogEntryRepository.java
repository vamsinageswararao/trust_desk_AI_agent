package com.trustdesk.repository;

import com.trustdesk.domain.ToolCatalogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolCatalogEntryRepository extends JpaRepository<ToolCatalogEntry, String> {
}
