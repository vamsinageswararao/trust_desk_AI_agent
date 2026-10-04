package com.trustdesk.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Typed, validated application settings (prefix {@code trustdesk}). */
@Validated
@ConfigurationProperties(prefix = "trustdesk")
public record TrustDeskProperties(@Valid @NotNull Auth auth, @Valid @NotNull Seed seed,
                                  @Valid @NotNull Retrieval retrieval) {

    /** {@code managerToken} is optional: if blank, the single demo token also carries the manager role (demo mode). */
    public record Auth(@NotBlank(message = "trustdesk.auth.token must be set (env TRUSTDESK_DEMO_TOKEN)") String token,
                       String managerToken) {
        @Override
        public String toString() {
            return "Auth[token=****, managerToken=****]"; // never log the secrets
        }
    }

    public record Seed(boolean enabled, @NotBlank String dataDir) {}

    /** Knowledge search limits: {@code topK} defaults to defaultTopK and may not exceed maxTopK. */
    public record Retrieval(@Min(1) int defaultTopK, @Min(1) int maxTopK) {
        public Retrieval {
            if (defaultTopK > maxTopK) {
                throw new IllegalArgumentException("trustdesk.retrieval.default-top-k must be <= max-top-k");
            }
        }
    }
}
