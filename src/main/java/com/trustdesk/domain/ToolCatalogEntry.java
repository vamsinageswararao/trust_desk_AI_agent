package com.trustdesk.domain;

import com.trustdesk.domain.converter.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;

/** One row of data/tool_actions.json. */
@Entity
@Table(name = "tool_catalog")
public class ToolCatalogEntry {
    @Id @Column(length = 64)
    private String toolName;
    @Column(nullable = false) private String description;
    @Column(nullable = false, length = 16) private String riskLevel;
    @Column(nullable = false) private boolean requiresHumanApproval;
    @Convert(converter = StringListConverter.class) @Column(nullable = false, columnDefinition = "TEXT")
    private List<String> allowedCategories;
    @Convert(converter = StringListConverter.class) @Column(nullable = false, columnDefinition = "TEXT")
    private List<String> requiredFields;
    private Integer maxAmountInr;

    protected ToolCatalogEntry() {}

    public ToolCatalogEntry(String toolName, String description, String riskLevel, boolean requiresHumanApproval,
                            List<String> allowedCategories, List<String> requiredFields, Integer maxAmountInr) {
        this.toolName = toolName;
        this.description = description;
        this.riskLevel = riskLevel;
        this.requiresHumanApproval = requiresHumanApproval;
        this.allowedCategories = allowedCategories;
        this.requiredFields = requiredFields;
        this.maxAmountInr = maxAmountInr;
    }

    public String getToolName() { return toolName; }
    public String getDescription() { return description; }
    public String getRiskLevel() { return riskLevel; }
    public boolean isRequiresHumanApproval() { return requiresHumanApproval; }
    public List<String> getAllowedCategories() { return allowedCategories; }
    public List<String> getRequiredFields() { return requiredFields; }
    public Integer getMaxAmountInr() { return maxAmountInr; }
}
