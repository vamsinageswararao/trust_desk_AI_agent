package com.trustdesk.retrieval;

import com.trustdesk.domain.KnowledgeDocument;
import com.trustdesk.repository.KnowledgeDocumentRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lexical (keyword) implementation of {@link KnowledgeRetriever} over the stored knowledge documents.
 * Depends only on the document repository: no AI provider, no tool/approval services. Retrieved text is returned
 * as data; whether it is safe to use is a decision for later guardrail/AI layers, not for retrieval.
 */
@Service
@Transactional(readOnly = true)
public class LexicalKnowledgeRetriever implements KnowledgeRetriever {

    private final KnowledgeDocumentRepository documents;
    private final LexicalScorer scorer = new LexicalScorer();

    public LexicalKnowledgeRetriever(KnowledgeDocumentRepository documents) {
        this.documents = documents;
    }

    @Override
    public List<RetrievedDocument> retrieve(String query, int topK) {
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be >= 1");
        }
        if (query == null || query.isBlank()) {
            return List.of();
        }
        // Row order is irrelevant: the scorer sorts candidates by id and results by (score desc, id asc).
        List<KnowledgeDocument> all = documents.findAll();
        Map<String, KnowledgeDocument> byId = new HashMap<>();
        List<LexicalScorer.Candidate> candidates = all.stream()
                .peek(d -> byId.put(d.getDocId(), d))
                .map(d -> new LexicalScorer.Candidate(d.getDocId(), d.getTitle(), d.getContent()))
                .toList();

        return scorer.rank(query, candidates).stream()
                .limit(topK)
                .map(s -> {
                    KnowledgeDocument d = byId.get(s.docId());
                    return new RetrievedDocument(d.getDocId(), d.getTitle(), d.getVersion(), d.getAudience(),
                            d.getSourcePath(), s.score(), s.matchedTerms(), s.snippet(), d.getContent());
                })
                .toList();
    }
}
