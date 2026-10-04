package com.trustdesk.api;

import com.trustdesk.api.dto.WorkflowViews.AgentRunView;
import com.trustdesk.service.AgentRunService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agent-runs")
public class AgentRunController {

    private final AgentRunService service;

    public AgentRunController(AgentRunService service) {
        this.service = service;
    }

    @GetMapping("/{runId}")
    public AgentRunView get(@PathVariable String runId) {
        return service.get(runId);
    }
}
