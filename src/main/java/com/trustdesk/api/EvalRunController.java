package com.trustdesk.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustdesk.api.dto.EvalRunRequest;
import com.trustdesk.api.dto.WorkflowViews.EvalRunView;
import com.trustdesk.common.ApiException;
import com.trustdesk.service.EvaluationService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/eval-runs")
public class EvalRunController {

    private final EvaluationService service;
    private final ObjectMapper mapper;

    public EvalRunController(EvaluationService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /**
     * Runs synchronously: the 8 seeded cases are deterministic and fast with the default mock provider. With a slow
     * real LLM this should move to a background job (documented limitation).
     *
     * The optional body is read as raw text and parsed here so that clients which attach a default Content-Type to
     * body-less POSTs (for example PowerShell's form-urlencoded) still work. A non-empty body must be JSON.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EvalRunView run(@RequestBody(required = false) String rawBody) {
        return service.run(parseCaseIds(rawBody));
    }

    @GetMapping
    public List<EvalRunView> list() {
        return service.list();
    }

    @GetMapping("/{evalRunId}")
    public EvalRunView get(@PathVariable String evalRunId) {
        return service.get(evalRunId);
    }

    private List<String> parseCaseIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(raw, EvalRunRequest.class).caseIds();
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                    "Request body must be empty or JSON like {\"case_ids\":[\"eval_006\"]}");
        }
    }
}
