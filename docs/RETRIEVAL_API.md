# Knowledge Retrieval

Deterministic keyword search over the stored knowledge-base documents. This is retrieval only: there is no AI,
triage, drafting, citation generation, or tool use behind it.

## Endpoint

`GET /documents/search?q=<query>&topK=<number>` (requires `Authorization: Bearer <demo token>`)

| Parameter | Required | Rules                                                                                                                 |
| --------- | -------- | --------------------------------------------------------------------------------------------------------------------- |
| `q`       | yes      | not blank, at most 500 characters                                                                                     |
| `topK`    | no       | integer >= 1 and <= `trustdesk.retrieval.max-top-k` (default max 10); default `trustdesk.retrieval.default-top-k` (3) |

Invalid input returns `400` with the standard error body (`code: VALIDATION_FAILED`, `details[]` naming `q` or `topK`).
Missing/invalid token returns `401`. Any other method returns `405`. A query that matches nothing is a normal
`200` with an empty `results` array.

### Response

```json
{
  "query": "refund policy for damaged items",
  "top_k": 3,
  "result_count": 3,
  "content_trust": "untrusted_data",
  "results": [
    {
      "doc_id": "KB-REFUND-001",
      "title": "Refund and Return Policy",
      "version": "2026.07",
      "audience": "Customer support agents",
      "source_path": "data/knowledge_base/refund_policy.md",
      "score": 4.100688,
      "matched_terms": ["refund", "policy", "damaged", "items"],
      "snippet": "For damaged or defective items reported within the return window, support may offer either a replacement or a refund review. The customer may be asked to provide a photo or short description of the damage before the action is approved.",
      "content": "<full document text>"
    }
  ]
}
```

Field names are snake_case like the rest of the API; `doc_id`, `title`, `snippet` and `score` follow
`docs/API_CONTRACT.md` (section 2). `checksum` and ingestion timestamps are not exposed.
`matched_terms` are the words from the query that matched (not internal stems).

## Ranking

Pure and deterministic: no clock, randomness, or database row order is involved.

1. Query and documents are lower-cased, split on non-alphanumerics, stripped of stop words and one-character
   tokens, and lightly stemmed (`damaged`/`damage`, `charged`/`charges`, `shipping`/`ship` compare equal).
2. For each distinct query term `q` and document `d`:
   `idf(q) * ( 3 * [q appears in title or doc id] + tf / (tf + 1) )`, where `tf` is the count of `q` in the document
   text and `idf(q) = ln(1 + (N - df + 0.5) / (df + 0.5))` (rarer terms count more).
3. A document's score is the sum over distinct query terms, rounded to 6 decimals. Repeating a word in the query
   does not raise scores. Documents with score 0 are not returned.
4. Order: score descending, then document id ascending (so ties are stable).
5. `topK` keeps the first K of that order. Asking for more than exist returns what matches.

Any shared non-stop-word term counts as a match, so weak matches can appear at the tail of a result list with low
scores; consumers should use `topK` and `score`.

## Trust boundary

Knowledge-base text is **untrusted data, never instructions**. Retrieval only tokenises and counts words; it has no
code path that interprets, executes, or obeys document text, and it depends only on the document repository (no AI
provider, tool, approval, or ticket services). Every response carries `content_trust: "untrusted_data"` as a
reminder to consumers.

`KB-ADVERSARIAL-001` is intentionally malicious test content. It stays stored unchanged and is retrievable like any
other document (for example by searching its id, or by a query that overlaps its words). There is no special-case
rule for it in retrieval: deciding whether retrieved text may be used or cited is the job of later guardrail/AI
layers, which must treat all returned `content` and `snippet` values as data.

## Design for replacement

Callers use the `KnowledgeRetriever` interface (`retrieve(query, topK)`). `LexicalKnowledgeRetriever` implements it
with `LexicalScorer`/`TextAnalyzer`; a hybrid or embedding retriever can replace it without changing the endpoint.
