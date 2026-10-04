package com.trustdesk.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Validated query parameters for GET /tickets. */
public class TicketListQuery {
    public static final int DEFAULT_SIZE = 20;

    @Min(value = 0, message = "must be >= 0")
    private Integer page;

    @Min(value = 1, message = "must be >= 1")
    @Max(value = 100, message = "must be <= 100")
    private Integer size;

    @Size(max = 32, message = "must be at most 32 characters")
    @Pattern(regexp = "^[a-z_]*$", message = "must contain only lowercase letters and underscores")
    private String status;

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int pageOrDefault() { return page == null ? 0 : page; }
    public int sizeOrDefault() { return size == null ? DEFAULT_SIZE : size; }
    public String statusOrNull() { return status == null || status.isBlank() ? null : status; }
}
