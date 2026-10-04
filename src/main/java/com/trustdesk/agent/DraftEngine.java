package com.trustdesk.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustdesk.ai.AiProvider;
import com.trustdesk.guardrails.EvidencePolicy;
import com.trustdesk.guardrails.GuardFlag;
import com.trustdesk.guardrails.InputGuard;
import com.trustdesk.guardrails.OutputGuard;
import com.trustdesk.retrieval.KnowledgeRetriever;
import com.trustdesk.retrieval.RetrievedDocument;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ticket -> retrieve -> exclude untrusted sources -> check evidence sufficiency -> model draft -> validate
 * citations -> scan output -> recommend actions. The model only writes text; every safety decision (evidence,
 * citations, output scan, actions, escalation) is made here by the application and can only make things safer.
 */
public class DraftEngine {

    private static final Map<TicketCategory, String> CATEGORY_TERMS = Map.of(
            TicketCategory.SHIPPING, "shipping delivery tracking carrier investigation",
            TicketCategory.REFUND, "refund return replacement damaged policy",
            TicketCategory.WARRANTY, "warranty safety battery replacement",
            TicketCategory.BILLING, "billing duplicate charge refund review payment",
            TicketCategory.ACCOUNT_SECURITY, "account identity verification security",
            TicketCategory.GENERAL, "");

    private final AiProvider provider;
    private final KnowledgeRetriever retriever;
    private final ObjectMapper mapper;
    private final EngineSettings settings;
    private final PromptBuilder prompts;
    private final ActionRecommender recommender = new ActionRecommender();

    public DraftEngine(AiProvider provider, KnowledgeRetriever retriever, ObjectMapper mapper, EngineSettings settings) {
        this.provider = provider;
        this.retriever = retriever;
        this.mapper = mapper;
        this.settings = settings;
        this.prompts = new PromptBuilder(mapper);
    }

    public DraftOutcome draft(TicketContext ctx, TriageDecision triage, Set<GuardFlag> flags,
                              Map<String, ToolSpec> catalog) {
        PolicyFacts facts = PolicyFacts.of(ctx, flags);
        List<String> why = new ArrayList<>();
        why.add("triage: category=" + triage.category().wire() + ", priority=" + triage.priority().wire()
                + ", should_escalate=" + triage.shouldEscalate() + ", source=" + triage.source());
        if (ctx.hasOrder()) {
            why.add("policy facts from ticket created_at " + ctx.createdAt() + ": return_window_open="
                    + facts.returnWindowOpen() + " (eligible_return_until=" + ctx.eligibleReturnUntil()
                    + "), non_returnable=" + facts.allItemsNonReturnable() + ", warranty_covered=" + facts.warrantyCovered());
        }
        if (!flags.isEmpty()) {
            why.add("input guard flags: " + flags);
        }

        // ---- retrieval and evidence selection (provenance based) ----
        List<RetrievedDocument> hits = retriever.retrieve(query(ctx, triage, flags), settings.retrievalTopK());
        EvidencePolicy.Partition part = EvidencePolicy.partition(hits, settings.trustedAudiences());
        List<RetrievedDocument> evidence = part.trusted().stream()
                .filter(h -> h.score() >= settings.minEvidenceScore()).toList();
        List<String> retrievedIds = hits.stream().map(RetrievedDocument::docId).toList();
        why.add("retrieved: " + retrievedIds);
        for (EvidencePolicy.Excluded e : part.excluded()) {
            why.add("excluded from evidence: " + e.docId() + " (" + e.reason() + ") - kept as data, never used as instructions or citations");
        }
        why.add("evidence used: " + evidence.stream().map(RetrievedDocument::docId).toList());

        List<String> violations = new ArrayList<>();
        String status;
        String body = null;
        List<DraftOutcome.Citation> citations = List.of();
        boolean modelNeedsHuman = false;

        if (evidence.isEmpty()) {
            status = "insufficient_evidence";
            why.add("no trusted evidence above score " + settings.minEvidenceScore() + "; not answering, escalating");
        } else {
            status = "grounded";
            try {
                String raw = provider.complete(prompts.draft(ctx, triage, facts, evidence)).content();
                JsonNode n = ModelJson.parseObject(mapper, raw);
                body = ModelJson.clean(ModelJson.requiredText(n, "body"), 4000);
                JsonNode cites = n.get("citations");
                if (cites == null || !cites.isArray()) {
                    throw new ModelOutputException("citations must be an array");
                }
                Map<String, RetrievedDocument> byId = evidence.stream()
                        .collect(Collectors.toMap(RetrievedDocument::docId, d -> d, (a, b) -> a, LinkedHashMap::new));
                Set<String> valid = new LinkedHashSet<>();
                for (JsonNode c : cites) {
                    if (!c.isTextual()) {
                        throw new ModelOutputException("citation is not a string");
                    }
                    if (byId.containsKey(c.asText())) {
                        valid.add(c.asText());
                    } else {
                        violations.add("UNKNOWN_CITATION:" + ModelJson.clean(c.asText(), 40));
                    }
                }
                citations = valid.stream().map(id -> toCitation(byId.get(id))).toList();
                modelNeedsHuman = n.has("needs_human") && n.get("needs_human").asBoolean(false);
                if (citations.isEmpty()) {
                    status = "ungrounded_fallback";
                    body = null;
                    why.add("model produced no valid citation to trusted evidence; discarding draft");
                }
            } catch (ModelOutputException e) {
                status = "model_error_fallback";
                body = null;
                citations = List.of();
                violations.add("MODEL_OUTPUT_INVALID: " + e.getMessage());
            } catch (RuntimeException e) {
                status = "model_error_fallback";
                body = null;
                citations = List.of();
                violations.add("AI_PROVIDER_ERROR: " + e.getClass().getSimpleName());
            }
        }

        if (body != null) {
            List<String> outputViolations = OutputGuard.scan(body, ctx.otherCustomerEmails());
            if (!outputViolations.isEmpty()) {
                violations.addAll(outputViolations);
                status = "output_blocked";
                body = null;
                citations = List.of();
                why.add("output guard blocked the generated reply: " + outputViolations);
            }
        }
        if (body == null) {
            body = safeFallback(ctx);
        }

        boolean requiresHuman = modelNeedsHuman || triage.shouldEscalate() || InputGuard.isAdversarial(flags)
                || !"grounded".equals(status);
        TriageDecision effective = requiresHuman && !triage.shouldEscalate()
                ? new TriageDecision(triage.category(), triage.priority(), triage.sentiment(), true,
                        "Draft requires human handling (" + status + ")", triage.source(), triage.warnings())
                : triage;
        ActionRecommender.Result rec = recommender.recommend(ctx, effective, flags, catalog);
        why.add("recommended actions: " + rec.actions().stream().map(RecommendedAction::toolName).toList()
                + (rec.blocked().isEmpty() ? "" : ", blocked by policy: " + rec.blocked()));

        String decision = "output_blocked".equals(status) ? "blocked" : (requiresHuman ? "escalated" : "passed");
        List<String> notes = new ArrayList<>();
        notes.add("status=" + status);
        notes.add("customer and KB text treated as untrusted data; actions come from application policy, not model text");
        GuardrailReport report = new GuardrailReport(decision, flags.stream().map(Enum::name).sorted().toList(),
                violations, notes);
        return new DraftOutcome(body, citations, retrievedIds, part.excluded(), report, rec.actions(), rec.blocked(),
                status, requiresHuman, why);
    }

    private static DraftOutcome.Citation toCitation(RetrievedDocument d) {
        return new DraftOutcome.Citation(d.docId(), d.title(), d.version(), d.sourcePath());
    }

    /** Retrieval plan: the customer's words plus category/flag terms. Words only; nothing here is executed. */
    static String query(TicketContext ctx, TriageDecision triage, Set<GuardFlag> flags) {
        StringBuilder q = new StringBuilder(ctx.text()).append(' ').append(CATEGORY_TERMS.get(triage.category()));
        if (InputGuard.isAdversarial(flags)) {
            q.append(" security untrusted prompt injection secrets playbook");
        }
        if (flags.contains(GuardFlag.COUPON_REQUEST)) {
            q.append(" coupon goodwill");
        }
        return q.toString();
    }

    static String safeFallback(TicketContext ctx) {
        String greeting = ctx.firstName() == null ? "Hello," : "Hi " + ctx.firstName() + ",";
        return greeting + "\n\nThank you for contacting us. I don't have enough verified policy information to answer "
                + "this reliably, so I've passed your request to a human support specialist who will follow up.\n\n"
                + "Kind regards,\nSupport Team";
    }
}
