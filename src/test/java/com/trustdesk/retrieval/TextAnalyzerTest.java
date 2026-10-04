package com.trustdesk.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextAnalyzerTest {

    private final TextAnalyzer analyzer = new TextAnalyzer();

    @Test
    void lowercasesSplitsOnPunctuationAndDropsStopwordsAndSingleCharacters() {
        assertThat(analyzer.terms("The Refund, of DAMAGED items! A b 7"))
                .containsExactly("refund", "damag", "item");
    }

    @Test
    void keepsMultiDigitNumbersButNotSingleDigits() {
        assertThat(analyzer.terms("10 business days, 6 days")).containsExactly("10", "business", "day", "day");
    }

    @Test
    void nullAndBlankProduceNoTerms() {
        assertThat(analyzer.terms(null)).isEmpty();
        assertThat(analyzer.terms("   ")).isEmpty();
        assertThat(analyzer.terms("the of and ???")).isEmpty();
        assertThat(analyzer.queryTerms(null)).isEmpty();
    }

    @Test
    void wordFormsOfTheSameWordShareAStem() {
        assertSameStem("damaged", "damage", "damaging", "damages");
        assertSameStem("charges", "charged", "charge");
        assertSameStem("shipping", "shipped", "ship");
        assertSameStem("refunds", "refunded", "refund");
        assertSameStem("policies", "policy");
        assertSameStem("verify", "verified");
        assertSameStem("battery", "batteries");
        assertSameStem("swelling", "swelled", "swells", "swell");
        assertSameStem("coupons", "coupon");
    }

    @Test
    void unrelatedWordsKeepDistinctStems() {
        assertThat(TextAnalyzer.stem("business")).isNotEqualTo(TextAnalyzer.stem("busy"));
        assertThat(TextAnalyzer.stem("refund")).isNotEqualTo(TextAnalyzer.stem("return"));
        assertThat(TextAnalyzer.stem("status")).isEqualTo("status");
        assertThat(TextAnalyzer.stem("access")).isEqualTo("access");
    }

    @Test
    void queryTermsAreDistinctOrderedAndRememberTheOriginalWord() {
        var terms = analyzer.queryTerms("Refund refunds REFUNDED damaged");
        assertThat(terms).containsOnlyKeys("refund", "damag");
        assertThat(terms.keySet()).containsExactly("refund", "damag");
        assertThat(terms.get("refund")).isEqualTo("refund");
        assertThat(terms.get("damag")).isEqualTo("damaged");
    }

    private void assertSameStem(String first, String... others) {
        for (String other : others) {
            assertThat(TextAnalyzer.stem(other)).as(first + " vs " + other).isEqualTo(TextAnalyzer.stem(first));
        }
    }
}
