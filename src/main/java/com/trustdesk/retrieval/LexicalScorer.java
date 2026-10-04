package com.trustdesk.retrieval;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Transparent lexical ranking over a small corpus. Pure and deterministic: no I/O, clock, randomness or hash-order
 * dependence. Document text is treated strictly as data: it is tokenised and counted, never interpreted.
 *
 * <pre>
 * for each distinct query term q and document d:
 *   idf(q)        = ln(1 + (N - df(q) + 0.5) / (df(q) + 0.5))        N = documents, df = documents containing q
 *   contribution  = idf(q) * ( HEADING_BOOST * [q in title or doc id] + tf / (tf + 1) )   tf = count of q in body
 * score(d) = sum of contributions, rounded to 6 decimals. Documents scoring 0 are not returned.
 * order: score descending, then document id ascending.
 * </pre>
 */
public final class LexicalScorer {

    public static final double HEADING_BOOST = 3.0;
    static final int SNIPPET_MAX_CHARS = 240;

    private final TextAnalyzer analyzer = new TextAnalyzer();

    public record Candidate(String docId, String title, String content) {}

    public record Scored(String docId, double score, List<String> matchedTerms, String snippet) {}

    private record Index(Candidate candidate, Set<String> headingTerms, Map<String, Integer> bodyCounts) {
        boolean contains(String term) {
            return headingTerms.contains(term) || bodyCounts.containsKey(term);
        }
    }

    /** Returns every candidate with a positive score, best first. Never null. */
    public List<Scored> rank(String query, Collection<Candidate> candidates) {
        Map<String, String> queryTerms = analyzer.queryTerms(query);
        if (queryTerms.isEmpty() || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<Candidate> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparing(Candidate::docId));
        List<Index> indexes = new ArrayList<>();
        for (Candidate c : ordered) {
            indexes.add(index(c));
        }

        int n = indexes.size();
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (String q : queryTerms.keySet()) {
            int df = 0;
            for (Index i : indexes) {
                if (i.contains(q)) {
                    df++;
                }
            }
            documentFrequency.put(q, df);
        }

        List<Scored> scored = new ArrayList<>();
        for (Index idx : indexes) {
            double score = 0;
            List<String> matched = new ArrayList<>();
            for (Map.Entry<String, String> term : queryTerms.entrySet()) {
                String q = term.getKey();
                int tf = idx.bodyCounts().getOrDefault(q, 0);
                boolean inHeading = idx.headingTerms().contains(q);
                if (tf == 0 && !inHeading) {
                    continue;
                }
                int df = documentFrequency.get(q);
                double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
                score += idf * ((inHeading ? HEADING_BOOST : 0) + tf / (tf + 1.0));
                matched.add(term.getValue());
            }
            score = Math.round(score * 1_000_000d) / 1_000_000d;
            if (score > 0) {
                scored.add(new Scored(idx.candidate().docId(), score, List.copyOf(matched),
                        snippet(idx.candidate().content(), queryTerms.keySet())));
            }
        }
        scored.sort((a, b) -> {
            int byScore = Double.compare(b.score(), a.score());
            return byScore != 0 ? byScore : a.docId().compareTo(b.docId());
        });
        return List.copyOf(scored);
    }

    private Index index(Candidate c) {
        Set<String> heading = new HashSet<>(analyzer.terms(nullToEmpty(c.title()) + " " + nullToEmpty(c.docId())));
        Map<String, Integer> counts = new HashMap<>();
        for (String term : analyzer.terms(c.content())) {
            counts.merge(term, 1, Integer::sum);
        }
        return new Index(c, heading, counts);
    }

    /** The body line sharing the most distinct query terms (earliest wins ties); first body line if none match. */
    String snippet(String content, Set<String> queryStems) {
        if (content == null) {
            return "";
        }
        String best = null;
        int bestHits = -1;
        for (String line : content.split("\\R")) {
            String text = line.strip();
            if (text.isEmpty() || text.startsWith("#")) {
                continue;
            }
            Set<String> lineTerms = new HashSet<>(analyzer.terms(text));
            int hits = 0;
            for (String q : queryStems) {
                if (lineTerms.contains(q)) {
                    hits++;
                }
            }
            if (hits > bestHits) {
                best = text;
                bestHits = hits;
            }
        }
        if (best == null) {
            return "";
        }
        return best.length() <= SNIPPET_MAX_CHARS ? best : best.substring(0, SNIPPET_MAX_CHARS - 3) + "...";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
