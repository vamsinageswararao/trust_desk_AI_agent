package com.trustdesk.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** payload must contain the tool's required fields, including idempotency_key (see GET /tickets/{id}/draft-replies). */
public record CreateToolActionRequest(@NotBlank @Size(max = 64) String ticketId,
                                      @NotBlank @Size(max = 64) String toolName,
                                      @NotNull Map<String, Object> payload) {}
