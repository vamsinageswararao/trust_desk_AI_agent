package com.trustdesk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustdesk.agent.DraftEngine;
import com.trustdesk.agent.TriageEngine;
import com.trustdesk.ai.AiProvider;
import com.trustdesk.ai.MockAiProvider;
import com.trustdesk.ai.OpenAiCompatibleAiProvider;
import com.trustdesk.ai.UnconfiguredAiProvider;
import com.trustdesk.retrieval.KnowledgeRetriever;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Selects the AI provider by configuration and wires the (framework-free) engines to it. */
@Configuration
public class AiConfig {

    @Bean
    @ConditionalOnProperty(prefix = "trustdesk.ai", name = "provider", havingValue = "mock", matchIfMissing = true)
    AiProvider mockAiProvider(ObjectMapper mapper) {
        return new MockAiProvider(mapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "trustdesk.ai", name = "provider", havingValue = "openai-compatible")
    AiProvider openAiCompatibleProvider(AiProperties p, ObjectMapper mapper) {
        return new OpenAiCompatibleAiProvider(p.baseUrl(), p.apiKey(), p.model(), p.timeoutSeconds(), mapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "trustdesk.ai", name = "provider", havingValue = "unconfigured")
    AiProvider unconfiguredAiProvider() {
        return new UnconfiguredAiProvider();
    }

    @Bean
    TriageEngine triageEngine(AiProvider provider, ObjectMapper mapper) {
        return new TriageEngine(provider, mapper);
    }

    @Bean
    DraftEngine draftEngine(AiProvider provider, KnowledgeRetriever retriever, ObjectMapper mapper,
                            GuardrailProperties guardrails) {
        return new DraftEngine(provider, retriever, mapper, guardrails.toSettings());
    }
}
