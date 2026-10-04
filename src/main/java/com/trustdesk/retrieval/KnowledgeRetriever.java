package com.trustdesk.retrieval;

import java.util.List;

/**
 * Application-level retrieval port: "find the knowledge documents most relevant to this query".
 * The REST layer and future AI workflow depend only on this interface, so the lexical implementation can be
 * replaced (hybrid/embedding search) without changing callers.
 *
 * <p>Contract: deterministic for identical input and database state; ordered best first with ties broken by
 * document id ascending; never returns more than {@code topK}; blank/unmatched queries yield an empty list;
 * performs no side effects and never interprets document text.
 */
public interface KnowledgeRetriever {

    /** @throws IllegalArgumentException if {@code topK < 1} */
    List<RetrievedDocument> retrieve(String query, int topK);
}
