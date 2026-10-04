package com.trustdesk.domain;

import com.trustdesk.domain.converter.InstantConverter;
import com.trustdesk.domain.converter.MapListConverter;
import com.trustdesk.domain.converter.ObjectMapConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Stored evaluation summary. Schema only in Milestone 1 (runner arrives in Milestone 5). */
@Entity
@Table(name = "eval_runs")
public class EvalRun {
    @Id @Column(length = 64)
    private String evalRunId;
    @Convert(converter = InstantConverter.class) @Column(nullable = false, length = 40)
    private Instant startedAt;
    @Convert(converter = InstantConverter.class) @Column(length = 40)
    private Instant completedAt;
    @Column(nullable = false) private int totalCases;
    @Convert(converter = ObjectMapConverter.class) @Column(columnDefinition = "TEXT")
    private Map<String, Object> metrics;
    @Convert(converter = MapListConverter.class) @Column(columnDefinition = "TEXT")
    private List<Map<String, Object>> caseResults;

    protected EvalRun() {}

    public EvalRun(String evalRunId, Instant startedAt, Instant completedAt, int totalCases,
                   Map<String, Object> metrics, List<Map<String, Object>> caseResults) {
        this.evalRunId = evalRunId;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.totalCases = totalCases;
        this.metrics = metrics;
        this.caseResults = caseResults;
    }

    public String getEvalRunId() { return evalRunId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public int getTotalCases() { return totalCases; }
    public Map<String, Object> getMetrics() { return metrics; }
    public List<Map<String, Object>> getCaseResults() { return caseResults; }
}
