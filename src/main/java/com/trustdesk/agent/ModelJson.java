package com.trustdesk.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Strict parsing helpers for model replies. Model text is untrusted: only validated, typed fields are used. */
final class ModelJson {
    private ModelJson() {}

    /** Accepts a bare JSON object or one wrapped in a markdown code fence / surrounding prose. */
    static JsonNode parseObject(ObjectMapper mapper, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ModelOutputException("empty model output");
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new ModelOutputException("no JSON object in model output");
        }
        try {
            JsonNode node = mapper.readTree(raw.substring(start, end + 1));
            if (node == null || !node.isObject()) {
                throw new ModelOutputException("model output is not a JSON object");
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new ModelOutputException("model output is not valid JSON");
        }
    }

    static String requiredText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || !v.isTextual() || v.asText().isBlank()) {
            throw new ModelOutputException("missing or non-text field: " + field);
        }
        return v.asText();
    }

    static boolean requiredBoolean(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || !v.isBoolean()) {
            throw new ModelOutputException("missing or non-boolean field: " + field);
        }
        return v.asBoolean();
    }

    /** Strips control characters and caps length so model text cannot smuggle odd content into storage/UI. */
    static String clean(String s, int max) {
        String t = s.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").strip();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
