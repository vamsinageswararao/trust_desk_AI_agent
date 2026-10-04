package com.trustdesk.config;

import com.trustdesk.agent.EngineSettings;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "trustdesk.guardrails")
public record GuardrailProperties(@DefaultValue("1.0") double minEvidenceScore,
                                  @DefaultValue("5") int retrievalTopK,
                                  @DefaultValue({"Customer support agents", "Engineering and support administrators"})
                                  List<String> trustedAudiences) {

    public EngineSettings toSettings() {
        return new EngineSettings(minEvidenceScore, Math.max(1, retrievalTopK),
                trustedAudiences.stream().map(a -> a.strip().toLowerCase(Locale.ROOT)).collect(Collectors.toSet()));
    }
}
