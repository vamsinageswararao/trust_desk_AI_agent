package com.trustdesk.api;

import com.trustdesk.api.dto.WorkflowViews.AgentRunView;
import com.trustdesk.api.dto.WorkflowViews.DraftView;
import com.trustdesk.api.dto.WorkflowViews.ToolActionView;
import com.trustdesk.api.dto.WorkflowViews.TriageView;
import com.trustdesk.service.AgentRunService;
import com.trustdesk.service.DraftReplyService;
import com.trustdesk.service.ToolActionService;
import com.trustdesk.service.TriageService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Per-ticket AI workflow: triage, grounded draft, and the read models the UI needs. */
@RestController
@RequestMapping("/tickets/{ticketId}")
public class TicketWorkflowController {

    private final TriageService triage;
    private final DraftReplyService drafts;
    private final ToolActionService actions;
    private final AgentRunService runs;

    public TicketWorkflowController(TriageService triage, DraftReplyService drafts, ToolActionService actions,
                                    AgentRunService runs) {
        this.triage = triage;
        this.drafts = drafts;
        this.actions = actions;
        this.runs = runs;
    }

    @PostMapping("/triage")
    public TriageView triage(@PathVariable String ticketId) {
        return triage.triage(ticketId);
    }

    @PostMapping("/draft-reply")
    @ResponseStatus(HttpStatus.CREATED)
    public DraftView draftReply(@PathVariable String ticketId) {
        return drafts.generate(ticketId);
    }

    @GetMapping("/draft-replies")
    public List<DraftView> draftReplies(@PathVariable String ticketId) {
        return drafts.forTicket(ticketId);
    }

    @GetMapping("/tool-actions")
    public List<ToolActionView> toolActions(@PathVariable String ticketId) {
        return actions.forTicket(ticketId);
    }

    @GetMapping("/agent-runs")
    public List<AgentRunView> agentRuns(@PathVariable String ticketId) {
        return runs.forTicket(ticketId);
    }
}
