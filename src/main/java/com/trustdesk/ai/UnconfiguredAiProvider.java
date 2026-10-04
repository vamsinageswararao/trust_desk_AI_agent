package com.trustdesk.ai;

/** Placeholder selected with trustdesk.ai.provider=unconfigured. Fails loudly if anything calls it before a real provider exists. */
public class UnconfiguredAiProvider implements AiProvider {

    @Override
    public String name() {
        return "unconfigured";
    }

    @Override
    public AiResponse complete(AiRequest request) {
        throw new AiProviderException("No AI provider is configured yet (planned for Milestone 2)");
    }
}
