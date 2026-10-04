package com.trustdesk.service;

import com.trustdesk.agent.ActionPolicy;
import com.trustdesk.agent.TicketCategory;
import com.trustdesk.agent.TicketContext;
import com.trustdesk.agent.ToolSpec;
import com.trustdesk.api.dto.ApprovalRequest;
import com.trustdesk.api.dto.CreateToolActionRequest;
import com.trustdesk.api.dto.WorkflowViews.ApprovalView;
import com.trustdesk.api.dto.WorkflowViews.ToolActionView;
import com.trustdesk.common.ApiException;
import com.trustdesk.common.GuardrailBlockedException;
import com.trustdesk.common.Ids;
import com.trustdesk.common.NotFoundException;
import com.trustdesk.domain.AgentRunType;
import com.trustdesk.domain.Approval;
import com.trustdesk.domain.ApprovalDecision;
import com.trustdesk.domain.Ticket;
import com.trustdesk.domain.ToolAction;
import com.trustdesk.domain.ToolActionStatus;
import com.trustdesk.domain.ToolCatalogEntry;
import com.trustdesk.guardrails.GuardFlag;
import com.trustdesk.guardrails.InputGuard;
import com.trustdesk.repository.ApprovalRepository;
import com.trustdesk.repository.TicketRepository;
import com.trustdesk.repository.ToolActionRepository;
import com.trustdesk.repository.ToolCatalogEntryRepository;
import com.trustdesk.service.tools.ToolExecutor;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * request -> (policy check) -> approval required -> human approves -> execute -> result recorded.
 * The application owns every gate: AI/customer/KB text never reaches this class, only validated requests do.
 * Idempotency: one idempotency_key = one action (DB UNIQUE); re-POSTing returns the original, and re-executing an
 * executed action returns the stored result without running the tool again.
 */
@Service
public class ToolActionService {

    public record Created(ToolActionView view, boolean replay) {}

    private static final int MAX_KEY_LENGTH = 200;

    private final TicketRepository tickets;
    private final ToolActionRepository actions;
    private final ApprovalRepository approvals;
    private final ToolCatalogEntryRepository catalog;
    private final AgentContextFactory factory;
    private final AgentRunService runs;
    private final ActionPolicy policy = new ActionPolicy();
    private final Map<String, ToolExecutor> executors = new HashMap<>();

    public ToolActionService(TicketRepository tickets, ToolActionRepository actions, ApprovalRepository approvals,
                             ToolCatalogEntryRepository catalog, AgentContextFactory factory, AgentRunService runs,
                             List<ToolExecutor> executorBeans) {
        this.tickets = tickets;
        this.actions = actions;
        this.approvals = approvals;
        this.catalog = catalog;
        this.factory = factory;
        this.runs = runs;
        executorBeans.forEach(e -> executors.put(e.toolName(), e));
    }

    // ---------------------------------------------------------------- create
    @Transactional(noRollbackFor = GuardrailBlockedException.class)
    public Created create(CreateToolActionRequest req, String actor) {
        Ticket t = loadTicket(req.ticketId());
        ToolCatalogEntry tool = catalog.findById(req.toolName())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_TOOL", "Unknown tool: " + req.toolName()));
        Map<String, Object> payload = new LinkedHashMap<>(req.payload());
        String key = payload.get("idempotency_key") == null ? "" : payload.get("idempotency_key").toString();
        if (key.length() > MAX_KEY_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "idempotency_key is too long");
        }
        if (!key.isBlank()) {
            Optional<ToolAction> existing = actions.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                ToolAction e = existing.get();
                if (!e.getTicket().getTicketId().equals(t.getTicketId()) || !e.getTool().getToolName().equals(tool.getToolName())) {
                    throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                            "idempotency_key was already used for a different ticket or tool");
                }
                return new Created(view(e, true), true);
            }
        }
        TicketContext ctx = factory.context(t);
        Set<GuardFlag> flags = InputGuard.analyze(ctx.text());
        TicketCategory category = TicketCategory.parse(t.getTriageCategory()).orElse(null);
        ToolSpec spec = factory.spec(tool);
        ActionPolicy.Decision decision = policy.evaluate(spec, ctx, category, flags, payload);
        if (!decision.allowed()) {
            runs.record(t, AgentRunType.TOOL_RECOMMENDATION, "blocked", List.of(),
                    List.of(call(tool.getToolName(), null, "blocked", decision.code(), decision.reason())),
                    guard("blocked", flags, List.of(decision.code()),
                            "request refused by action policy: " + decision.reason()));
            throw new GuardrailBlockedException(decision.code(), decision.reason());
        }
        ToolActionStatus status = tool.isRequiresHumanApproval() ? ToolActionStatus.APPROVAL_REQUIRED : ToolActionStatus.REQUESTED;
        ToolAction a = actions.save(new ToolAction(Ids.next("act"), t, tool, payload, status, key));
        runs.record(t, AgentRunType.TOOL_RECOMMENDATION, status.name().toLowerCase(Locale.ROOT), List.of(),
                List.of(call(tool.getToolName(), a.getActionId(), status.name().toLowerCase(Locale.ROOT), null, "requested by " + actor)),
                guard("passed", flags, List.of(), tool.isRequiresHumanApproval()
                        ? "policy passed; human approval is required before execution" : "policy passed; no approval required for this low-risk tool"));
        return new Created(view(a, false), false);
    }

    // ---------------------------------------------------------------- approve / reject
    @Transactional
    public ToolActionView decide(String actionId, ApprovalRequest req, String reviewer) {
        ToolAction a = load(actionId);
        boolean approve = "approved".equals(req.decision());
        if (!a.isRequiresHumanApproval()) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "This tool does not require approval");
        }
        ToolActionStatus s = a.getStatus();
        if (s == ToolActionStatus.APPROVED && approve || s == ToolActionStatus.REJECTED && !approve) {
            return view(a, true); // same decision repeated: idempotent, no second approval record
        }
        if (s != ToolActionStatus.APPROVAL_REQUIRED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Action is " + s.name().toLowerCase(Locale.ROOT) + " and cannot be " + req.decision());
        }
        if (approve) {
            a.markApproved();
        } else {
            a.markRejected();
        }
        approvals.save(new Approval(Ids.next("apr"), a, null, reviewer,
                approve ? ApprovalDecision.APPROVED : ApprovalDecision.REJECTED, req.reason()));
        Ticket t = a.getTicket();
        runs.record(t, AgentRunType.TOOL_RECOMMENDATION, approve ? "approved" : "rejected", List.of(),
                List.of(call(a.getTool().getToolName(), a.getActionId(), approve ? "approved" : "rejected", null,
                        "decision by " + reviewer)),
                guard("passed", Set.of(), List.of(), "human decision recorded; reviewer taken from authenticated principal"));
        return view(a, false);
    }

    // ---------------------------------------------------------------- execute
    @Transactional(noRollbackFor = GuardrailBlockedException.class)
    public ToolActionView execute(String actionId, String actor) {
        ToolAction a = load(actionId);
        ToolActionStatus s = a.getStatus();
        if (s == ToolActionStatus.EXECUTED) {
            return view(a, true); // retry: return the stored result, never run the tool twice
        }
        boolean ready = s == ToolActionStatus.APPROVED || (s == ToolActionStatus.REQUESTED && !a.isRequiresHumanApproval());
        if (!ready) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Action is " + s.name().toLowerCase(Locale.ROOT) + "; it must be approved before execution");
        }
        ToolExecutor executor = executors.get(a.getTool().getToolName());
        if (executor == null) {
            throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "TOOL_NOT_IMPLEMENTED",
                    "Execution is not implemented for " + a.getTool().getToolName());
        }
        Ticket t = loadTicket(a.getTicket().getTicketId());
        TicketContext ctx = factory.context(t);
        Set<GuardFlag> flags = InputGuard.analyze(ctx.text());
        ActionPolicy.Decision recheck = policy.evaluate(factory.spec(a.getTool()), ctx,
                TicketCategory.parse(t.getTriageCategory()).orElse(null), flags, a.getPayload());
        if (!recheck.allowed()) { // defence in depth: policy is re-applied at execution time
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("error", recheck.code());
            failure.put("reason", recheck.reason());
            a.markFailed(failure);
            runs.record(t, AgentRunType.TOOL_RECOMMENDATION, "failed", List.of(),
                    List.of(call(a.getTool().getToolName(), a.getActionId(), "failed", recheck.code(), recheck.reason())),
                    guard("blocked", flags, List.of(recheck.code()), "policy re-check failed at execution time"));
            throw new GuardrailBlockedException(recheck.code(), recheck.reason());
        }
        a.markExecuted(executor.execute(a));
        runs.record(t, AgentRunType.TOOL_RECOMMENDATION, "executed", List.of(),
                List.of(call(a.getTool().getToolName(), a.getActionId(), "executed", null, "executed by " + actor)),
                guard("passed", flags, List.of(), "approved action executed once; result stored on the action"));
        return view(a, false);
    }

    // ---------------------------------------------------------------- reads
    @Transactional(readOnly = true)
    public ToolActionView get(String actionId) {
        return view(load(actionId), false);
    }

    @Transactional(readOnly = true)
    public List<ToolActionView> forTicket(String ticketId) {
        loadTicket(ticketId);
        return actions.findByTicket_TicketId(ticketId).stream()
                .sorted(Comparator.comparing(ToolAction::getCreatedAt).thenComparing(ToolAction::getActionId))
                .map(a -> view(a, false)).toList();
    }

    // ---------------------------------------------------------------- helpers
    private ToolAction load(String id) {
        return actions.findById(id).orElseThrow(() -> new NotFoundException("Tool action not found: " + id));
    }

    private Ticket loadTicket(String id) {
        return tickets.findDetailedByTicketId(id).orElseThrow(() -> new NotFoundException("Ticket not found: " + id));
    }

    private ToolActionView view(ToolAction a, boolean replay) {
        ApprovalView approval = approvals.findByAction_ActionId(a.getActionId()).stream()
                .min(Comparator.comparing(Approval::getCreatedAt))
                .map(p -> new ApprovalView(p.getApprovalId(), p.getDecision().name().toLowerCase(Locale.ROOT),
                        p.getReviewerId(), p.getReason(), p.getCreatedAt().toString()))
                .orElse(null);
        return new ToolActionView(a.getActionId(), a.getTicket().getTicketId(), a.getTool().getToolName(),
                a.getStatus().name().toLowerCase(Locale.ROOT), a.isRequiresHumanApproval(), a.getRiskLevel(),
                a.getPayload(), a.getIdempotencyKey(), approval, a.getExecutionResult(), replay,
                a.getCreatedAt().toString(), a.getUpdatedAt().toString());
    }

    private static Map<String, Object> call(String tool, String actionId, String status, String code, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tool_name", tool);
        if (actionId != null) m.put("action_id", actionId);
        m.put("status", status);
        if (code != null) m.put("code", code);
        m.put("reason", reason);
        return m;
    }

    private static Map<String, Object> guard(String decision, Set<GuardFlag> flags, List<String> violations, String note) {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("decision", decision);
        g.put("flags", flags.stream().map(Enum::name).sorted().toList());
        g.put("violations", violations);
        g.put("notes", List.of(note));
        g.put("explanation", List.of(note));
        return g;
    }
}
