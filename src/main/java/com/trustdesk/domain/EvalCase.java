package com.trustdesk.domain;

import com.trustdesk.domain.converter.ObjectMapConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Map;

/** One line of data/eval_cases.jsonl. Used only by the evaluation runner, never by AI generation. */
@Entity
@Table(name = "eval_cases")
public class EvalCase {
    @Id @Column(length = 64)
    private String caseId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;
    @Column(nullable = false, columnDefinition = "TEXT") private String input;
    @Convert(converter = ObjectMapConverter.class) @Column(nullable = false, columnDefinition = "TEXT")
    private Map<String, Object> expected;

    protected EvalCase() {}

    public EvalCase(String caseId, Ticket ticket, String input, Map<String, Object> expected) {
        this.caseId = caseId;
        this.ticket = ticket;
        this.input = input;
        this.expected = expected;
    }

    public String getCaseId() { return caseId; }
    public Ticket getTicket() { return ticket; }
    public String getInput() { return input; }
    public Map<String, Object> getExpected() { return expected; }
}
