package com.trustdesk.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/** Default provider: deterministic, offline, free. See {@link MockModel}. */
public class MockAiProvider implements AiProvider {

    private final ObjectMapper mapper;

    public MockAiProvider(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "mock-rule-based";
    }

    @Override
    public AiResponse complete(AiRequest request) {
        try {
            Map<String, Object> in = mapper.readValue(request.userContent(), new TypeReference<Map<String, Object>>() {});
            Object task = in.get("task");
            Map<String, Object> out;
            if ("triage".equals(task)) {
                out = MockModel.triage(in);
            } else if ("draft".equals(task)) {
                out = MockModel.draft(in);
            } else {
                throw new AiProviderException("mock provider does not support task: " + task);
            }
            return new AiResponse(mapper.writeValueAsString(out));
        } catch (JsonProcessingException e) {
            throw new AiProviderException("mock provider could not read the prompt", e);
        }
    }
}
