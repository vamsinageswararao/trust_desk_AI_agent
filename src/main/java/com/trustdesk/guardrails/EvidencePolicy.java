package com.trustdesk.guardrails;

import com.trustdesk.retrieval.RetrievedDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides which retrieved documents may be used as policy EVIDENCE, by provenance (the document's audience /
 * source), not by looking at what the text says. Only documents from configured internal audiences are trusted;
 * everything else (for example imported third-party vendor notes) is excluded from prompts and citations, while
 * staying stored and searchable. Excluded documents are reported so the trace shows what was set aside and why.
 */
public final class EvidencePolicy {

    public record Excluded(String docId, String reason) {}

    public record Partition(List<RetrievedDocument> trusted, List<Excluded> excluded) {}

    private EvidencePolicy() {}

    /** @param trustedAudiences lower-case audience names that count as internal policy sources */
    public static Partition partition(List<RetrievedDocument> hits, Set<String> trustedAudiences) {
        List<RetrievedDocument> trusted = new ArrayList<>();
        List<Excluded> excluded = new ArrayList<>();
        for (RetrievedDocument hit : hits) {
            String audience = hit.audience() == null ? "" : hit.audience().strip().toLowerCase(Locale.ROOT);
            if (trustedAudiences.contains(audience)) {
                trusted.add(hit);
            } else {
                excluded.add(new Excluded(hit.docId(),
                        "untrusted_source: audience='" + (hit.audience() == null ? "" : hit.audience()) + "'"));
            }
        }
        return new Partition(trusted, excluded);
    }
}
