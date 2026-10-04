package com.trustdesk.retrieval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic, dependency-free text normalisation used for retrieval: lower-case, split on non-alphanumerics,
 * drop one-character tokens and stop words, then apply a small suffix stemmer so "damaged"/"damage" or
 * "charges"/"charged" compare equal. Stems are internal; they are never shown to API consumers.
 */
public final class TextAnalyzer {

    private static final Pattern SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    private static final Set<String> STOPWORDS = Set.of(
            "am", "an", "and", "are", "as", "at", "be", "been", "being", "but", "by", "can", "could", "do", "does",
            "for", "from", "had", "has", "have", "he", "how", "if", "in", "into", "is", "it", "its", "me", "my",
            "no", "not", "of", "on", "or", "our", "please", "she", "should", "so", "than", "that", "the", "their",
            "them", "then", "there", "they", "this", "to", "was", "we", "were", "what", "when", "which", "who",
            "will", "with", "would", "you", "your", "about", "all", "any", "also");

    /** All index/query terms in order, duplicates kept (term frequency matters for documents). */
    public List<String> terms(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String raw : SPLIT.split(text.toLowerCase(Locale.ROOT))) {
            if (isIgnorable(raw)) {
                continue;
            }
            out.add(stem(raw));
        }
        return out;
    }

    /** Distinct query terms in first-occurrence order: stem -> first original (lower-cased) word that produced it. */
    public LinkedHashMap<String, String> queryTerms(String query) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (query == null) {
            return out;
        }
        for (String raw : SPLIT.split(query.toLowerCase(Locale.ROOT))) {
            if (isIgnorable(raw)) {
                continue;
            }
            out.putIfAbsent(stem(raw), raw);
        }
        return out;
    }

    private static boolean isIgnorable(String token) {
        return token.length() < 2 || STOPWORDS.contains(token);
    }

    static String stem(String token) {
        if (token.length() <= 3 || token.chars().allMatch(Character::isDigit)) {
            return token;
        }
        String s = token;
        if (s.endsWith("ies") && s.length() > 4) {
            s = s.substring(0, s.length() - 3) + "i";
        } else if (s.endsWith("ing") && s.length() > 5) {
            s = undouble(s.substring(0, s.length() - 3));
        } else if (s.endsWith("ed") && s.length() > 4) {
            s = undouble(s.substring(0, s.length() - 2));
        } else if (s.endsWith("s") && !s.endsWith("ss") && !s.endsWith("us")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.endsWith("e") && s.length() > 3) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.endsWith("y") && s.length() > 3 && !isVowel(s.charAt(s.length() - 2))) {
            s = s.substring(0, s.length() - 1) + "i";
        }
        return s;
    }

    /** "shipp" -> "ship" (but keep "swell", "pass", "buzz"). */
    private static String undouble(String s) {
        int n = s.length();
        if (n >= 2 && s.charAt(n - 1) == s.charAt(n - 2) && !isVowel(s.charAt(n - 1))
                && "lsz".indexOf(s.charAt(n - 1)) < 0) {
            return s.substring(0, n - 1);
        }
        return s;
    }

    private static boolean isVowel(char c) {
        return "aeiou".indexOf(c) >= 0;
    }
}
