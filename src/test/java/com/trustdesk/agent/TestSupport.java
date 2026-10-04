package com.trustdesk.agent;

import com.trustdesk.ai.AiProvider;
import com.trustdesk.ai.AiProviderException;
import com.trustdesk.ai.AiRequest;
import com.trustdesk.ai.AiResponse;
import com.trustdesk.retrieval.KnowledgeRetriever;
import com.trustdesk.retrieval.RetrievedDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Test doubles: scripted AI providers, a stub retriever, a standard tool catalog and engine settings. */
final class TestSupport {
    static final Set<String> TRUSTED = Set.of("customer support agents", "engineering and support administrators");
    static final EngineSettings SETTINGS = new EngineSettings(1.0, 5, TRUSTED);

    private TestSupport() {}

    /** Returns a fixed reply and remembers every request it received. */
    static final class Scripted implements AiProvider {
        final String reply;
        final List<AiRequest> requests = new ArrayList<>();

        Scripted(String reply) { this.reply = reply; }

        @Override public String name() { return "scripted"; }

        @Override public AiResponse complete(AiRequest request) {
            requests.add(request);
            return new AiResponse(reply);
        }
    }

    static AiProvider throwing() {
        return new AiProvider() {
            @Override public String name() { return "throwing"; }

            @Override public AiResponse complete(AiRequest request) {
                throw new AiProviderException("boom");
            }
        };
    }

    static RetrievedDocument doc(String id, String audience, double score, String content) {
        return new RetrievedDocument(id, "Title of " + id, "2026.07", audience, "data/knowledge_base/" + id + ".md",
                score, List.of(), "snippet", content);
    }

    static RetrievedDocument trusted(String id, double score) {
        return doc(id, "Customer support agents", score, "policy text of " + id);
    }

    static KnowledgeRetriever retriever(RetrievedDocument... docs) {
        return (q, k) -> List.of(docs).stream().limit(k).toList();
    }

    static Map<String, ToolSpec> catalog() {
        List<String> all = List.of("shipping", "refund", "warranty", "billing", "account_security", "general");
        return Map.of(
                "create_replacement_order", new ToolSpec("create_replacement_order", "medium", true, List.of("refund", "warranty"),
                        List.of("order_id", "sku", "reason", "idempotency_key"), null),
                "start_refund_review", new ToolSpec("start_refund_review", "medium", true, List.of("refund", "billing"),
                        List.of("order_id", "reason", "amount", "idempotency_key"), null),
                "issue_coupon", new ToolSpec("issue_coupon", "medium", true, List.of("shipping", "general"),
                        List.of("customer_id", "amount", "reason", "idempotency_key"), 1000),
                "open_carrier_investigation", new ToolSpec("open_carrier_investigation", "low", false, List.of("shipping"),
                        List.of("order_id", "tracking_number", "reason", "idempotency_key"), null),
                "escalate_to_human", new ToolSpec("escalate_to_human", "low", false, all,
                        List.of("ticket_id", "reason", "queue", "idempotency_key"), null),
                "lock_account", new ToolSpec("lock_account", "high", true, List.of("account_security"),
                        List.of("customer_id", "reason", "idempotency_key"), null));
    }

    static TriageDecision triage(TicketCategory c, TicketPriority p, boolean escalate) {
        return new TriageDecision(c, p, "neutral", escalate, "test", "model", List.of());
    }
}
