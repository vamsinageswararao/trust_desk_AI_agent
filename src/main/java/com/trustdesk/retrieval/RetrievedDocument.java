package com.trustdesk.retrieval;

import java.util.List;

/**
 * One retrieval hit. Carries everything a later citation step needs (id, title, version, audience, source path)
 * plus the document text. {@code content} and {@code snippet} are UNTRUSTED DATA: consumers must never treat them
 * as instructions. Internal persistence fields (checksum, ingestion time) are deliberately not exposed.
 */
public record RetrievedDocument(String docId, String title, String version, String audience, String sourcePath,
                                double score, List<String> matchedTerms, String snippet, String content) {}
