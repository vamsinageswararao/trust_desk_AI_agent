package com.trustdesk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.trustdesk.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the tables reserved for later milestones are mapped correctly (round-trip, relationships, JSON columns).
 * Rolled back after each test; no workflow logic is exercised.
 */
@Transactional
class LaterMilestoneEntitiesTest extends AbstractIntegrationTest {

    @PersistenceContext EntityManager em;

    private Ticket ticket() { return em.find(Ticket.class, "tkt_9001"); }
    private ToolCatalogEntry refundReview() { return em.find(ToolCatalogEntry.class, "start_refund_review"); }

    @Test
    void persistsAndReloadsAgentRunTrace() {
        em.persist(new AgentRun("run_t1", ticket(), AgentRunType.TRIAGE, "completed",
                List.of("KB-REFUND-001"), List.of(Map.of("tool_name", "start_refund_review")),
                Map.of("result", "passed")));
        em.flush();
        em.clear();

        AgentRun run = em.find(AgentRun.class, "run_t1");
        assertThat(run.getRunType()).isEqualTo(AgentRunType.TRIAGE);
        assertThat(run.getRetrievedDocIds()).containsExactly("KB-REFUND-001");
        assertThat(run.getToolCalls()).hasSize(1);
        assertThat(run.getToolCalls().get(0)).containsEntry("tool_name", "start_refund_review");
        assertThat(run.getGuardrailResults()).containsEntry("result", "passed");
        assertThat(run.getTicket().getTicketId()).isEqualTo("tkt_9001");
    }

    @Test
    void persistsDraftActionAndApprovalChain() {
        DraftReply draft = new DraftReply("draft_t1", ticket(), DraftStatus.GENERATED, "Hello", List.of("KB-REFUND-001"), null);
        ToolAction action = new ToolAction("act_t1", ticket(), refundReview(),
                Map.of("order_id", "ord_5001", "amount", 8999), ToolActionStatus.APPROVAL_REQUIRED, "tkt_9001-refund-1");
        em.persist(draft);
        em.persist(action);
        em.persist(new Approval("apr_t1", action, null, "reviewer_1", ApprovalDecision.APPROVED, "ok"));
        em.flush();
        em.clear();

        ToolAction loaded = em.find(ToolAction.class, "act_t1");
        assertThat(loaded.getStatus()).isEqualTo(ToolActionStatus.APPROVAL_REQUIRED);
        assertThat(loaded.isRequiresHumanApproval()).isTrue();
        assertThat(loaded.getRiskLevel()).isEqualTo("medium");
        assertThat(loaded.getTool().getToolName()).isEqualTo("start_refund_review");
        assertThat(loaded.getPayload()).containsEntry("order_id", "ord_5001");
        assertThat(em.find(DraftReply.class, "draft_t1").getCitations()).containsExactly("KB-REFUND-001");
        assertThat(em.find(Approval.class, "apr_t1").getAction().getActionId()).isEqualTo("act_t1");
    }

    @Test
    void persistsEvalRunSummary() {
        em.persist(new EvalRun("eval_run_t1", Instant.parse("2026-07-03T00:00:00Z"), Instant.parse("2026-07-03T00:00:05Z"), 8,
                Map.of("triage_accuracy", 0.875), List.of(Map.of("case_id", "eval_001", "passed", true))));
        em.flush();
        em.clear();

        EvalRun run = em.find(EvalRun.class, "eval_run_t1");
        assertThat(run.getTotalCases()).isEqualTo(8);
        assertThat(run.getMetrics()).containsEntry("triage_accuracy", 0.875);
        assertThat(run.getCaseResults().get(0)).containsEntry("case_id", "eval_001");
        assertThat(run.getStartedAt()).isEqualTo(Instant.parse("2026-07-03T00:00:00Z"));
    }

    @Test
    void databaseRejectsDuplicateIdempotencyKey() {
        em.persist(new ToolAction("act_a", ticket(), refundReview(), Map.of(), ToolActionStatus.REQUESTED, "dup-key"));
        em.flush();
        em.persist(new ToolAction("act_b", ticket(), refundReview(), Map.of(), ToolActionStatus.REQUESTED, "dup-key"));
        assertThatThrownBy(() -> em.flush()).isInstanceOf(PersistenceException.class);
    }
}
