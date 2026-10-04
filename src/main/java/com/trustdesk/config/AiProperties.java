package com.trustdesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** provider: mock | openai-compatible | unconfigured. The API key comes from the environment, never from code. */
@ConfigurationProperties(prefix = "trustdesk.ai")
public record AiProperties(@DefaultValue("mock") String provider, String baseUrl, String apiKey, String model,
                           @DefaultValue("30") int timeoutSeconds) {
    @Override
    public String toString() {
        return "AiProperties[provider=" + provider + ", baseUrl=" + baseUrl + ", model=" + model + ", apiKey=****]";
    }
}
