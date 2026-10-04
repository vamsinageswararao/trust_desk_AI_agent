package com.trustdesk.api;

import com.trustdesk.api.dto.ApprovalRequest;
import com.trustdesk.api.dto.CreateToolActionRequest;
import com.trustdesk.api.dto.WorkflowViews.ToolActionView;
import com.trustdesk.service.ToolActionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tool-actions")
public class ToolActionController {

    private final ToolActionService service;

    public ToolActionController(ToolActionService service) {
        this.service = service;
    }

    /** 201 for a new request, 200 (idempotent_replay=true) if the idempotency_key was already used. */
    @PostMapping
    public ResponseEntity<ToolActionView> create(@Valid @RequestBody CreateToolActionRequest request, Authentication auth) {
        ToolActionService.Created created = service.create(request, auth.getName());
        return ResponseEntity.status(created.replay() ? HttpStatus.OK : HttpStatus.CREATED).body(created.view());
    }

    @GetMapping("/{actionId}")
    public ToolActionView get(@PathVariable String actionId) {
        return service.get(actionId);
    }

    /** Requires the manager role (enforced in SecurityConfig). The reviewer is the authenticated principal. */
    @PostMapping("/{actionId}/approve")
    public ToolActionView approve(@PathVariable String actionId, @Valid @RequestBody ApprovalRequest request,
                                  Authentication auth) {
        return service.decide(actionId, request, auth.getName());
    }

    @PostMapping("/{actionId}/execute")
    public ToolActionView execute(@PathVariable String actionId, Authentication auth) {
        return service.execute(actionId, auth.getName());
    }
}
