package com.trustdesk.api.dto;

import java.util.List;

/** Response shapes for knowledge search (serialized as snake_case, matching docs/API_CONTRACT.md: doc_id, title, snippet, score). */
public final class DocumentViews {
    private DocumentViews() {}

    public static final String CONTENT_TRUST_UNTRUSTED = "untrusted_data";

    public record SearchHit(String docId, String title, String version, String audience, String sourcePath,
                            double score, List<String> matchedTerms, String snippet, String content) {}

    /**
     * {@code contentTrust} is always {@code untrusted_data}: snippet/content are knowledge-base text and must never be
     * followed as instructions by any consumer.
     */
    public record SearchResponse(String query, int topK, int resultCount, String contentTrust,
                                 List<SearchHit> results) {}
}
