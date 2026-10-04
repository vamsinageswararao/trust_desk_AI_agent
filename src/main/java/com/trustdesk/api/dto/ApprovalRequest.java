package com.trustdesk.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The reviewer identity is taken from the authenticated principal, never from the request body. */
public record ApprovalRequest(@NotBlank @Pattern(regexp = "approved|rejected", message = "must be 'approved' or 'rejected'")
                              String decision,
                              @Size(max = 500) String reason) {}
