package com.trustdesk.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Validated query parameters for GET /documents/search. The upper bound of topK is enforced from configuration. */
public class DocumentSearchQuery {
    public static final int MAX_QUERY_LENGTH = 500;

    @NotBlank(message = "must not be blank")
    @Size(max = MAX_QUERY_LENGTH, message = "must be at most 500 characters")
    private String q;

    @Min(value = 1, message = "must be >= 1")
    private Integer topK;

    public String getQ() { return q; }
    public void setQ(String q) { this.q = q; }
    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }
}
