package com.trustdesk.agent;

import java.util.List;

/** Framework-free view of one tool-catalog row. */
public record ToolSpec(String name, String riskLevel, boolean requiresApproval, List<String> allowedCategories,
                       List<String> requiredFields, Integer maxAmountInr) {}
