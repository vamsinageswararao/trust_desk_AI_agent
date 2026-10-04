package com.trustdesk.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Optional adapter for any OpenAI-compatible chat-completions endpoint (Groq, Ollama, OpenAI, ...). Provider specifics
 * stay here; the rest of the app only sees {@link AiProvider}. Its output is untrusted and always validated by the engines.
 * NOTE: not exercised by automated tests (needs a live endpoint/key); the deterministic mock is the tested default.
 */
public class OpenAiCompatibleAiProvider implements AiProvider {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public OpenAiCompatibleAiProvider(String baseUrl, String apiKey, String model, int timeoutSeconds, ObjectMapper mapper) {
        if (baseUrl == null || baseUrl.isBlank() || model == null || model.isBlank()) {
            throw new IllegalArgumentException("trustdesk.ai.base-url and trustdesk.ai.model are required for openai-compatible");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public String name() {
        return "openai-compatible:" + model;
    }

    @Override
    public AiResponse complete(AiRequest request) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0);
        ArrayNode messages = body.putArray("messages");
        if (request.systemInstruction() != null) {
            messages.addObject().put("role", "system").put("content", request.systemInstruction());
        }
        messages.addObject().put("role", "user").put("content", request.userContent());
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            if (apiKey != null && !apiKey.isBlank()) {
                req.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new AiProviderException("AI endpoint returned HTTP " + res.statusCode()); // never echo the body
            }
            JsonNode content = mapper.readTree(res.body()).path("choices").path(0).path("message").path("content");
            if (!content.isTextual()) {
                throw new AiProviderException("AI endpoint reply had no message content");
            }
            return new AiResponse(content.asText());
        } catch (IOException e) {
            throw new AiProviderException("AI endpoint call failed: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("AI endpoint call interrupted", e);
        }
    }
}
