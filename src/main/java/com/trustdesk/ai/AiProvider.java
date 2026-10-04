package com.trustdesk.ai;

/**
 * Port for any LLM provider (Gemini, Groq, Ollama, LangChain4j, a test double...). Business logic depends only on
 * this interface, so providers can be swapped or mocked without touching triage/draft code.
 * Milestone 1 defines the contract only; no real implementation exists yet.
 */
public interface AiProvider {

    String name();

    AiResponse complete(AiRequest request) throws AiProviderException;
}
