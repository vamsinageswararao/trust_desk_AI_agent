package com.trustdesk.api;

import com.trustdesk.api.dto.DocumentSearchQuery;
import com.trustdesk.api.dto.DocumentViews;
import com.trustdesk.api.dto.DocumentViews.SearchHit;
import com.trustdesk.api.dto.DocumentViews.SearchResponse;
import com.trustdesk.common.RequestValidationException;
import com.trustdesk.config.TrustDeskProperties;
import com.trustdesk.retrieval.KnowledgeRetriever;
import com.trustdesk.retrieval.RetrievedDocument;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/documents")
public class DocumentSearchController {

    private final KnowledgeRetriever retriever;
    private final TrustDeskProperties.Retrieval limits;

    public DocumentSearchController(KnowledgeRetriever retriever, TrustDeskProperties properties) {
        this.retriever = retriever;
        this.limits = properties.retrieval();
    }

    @GetMapping("/search")
    public SearchResponse search(@Valid DocumentSearchQuery query) {
        int topK = resolveTopK(query.getTopK());
        String q = query.getQ().strip();
        List<SearchHit> hits = retriever.retrieve(q, topK).stream().map(DocumentSearchController::toHit).toList();
        return new SearchResponse(q, topK, hits.size(), DocumentViews.CONTENT_TRUST_UNTRUSTED, hits);
    }

    private int resolveTopK(Integer requested) {
        if (requested == null) {
            return limits.defaultTopK();
        }
        if (requested > limits.maxTopK()) {
            throw new RequestValidationException("topK", "must be <= " + limits.maxTopK());
        }
        return requested;
    }

    private static SearchHit toHit(RetrievedDocument d) {
        return new SearchHit(d.docId(), d.title(), d.version(), d.audience(), d.sourcePath(), d.score(),
                d.matchedTerms(), d.snippet(), d.content());
    }
}
