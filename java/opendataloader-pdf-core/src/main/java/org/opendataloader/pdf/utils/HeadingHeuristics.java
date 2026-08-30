/*
 * Copyright 2025-2026 Hancom Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opendataloader.pdf.utils;

/**
 * Shape tests that tell running prose apart from a heading, used as a veto on
 * heading promotion.
 *
 * <p>Scoring alone cannot see that a run of text is a sentence. An untagged PDF gives
 * heading detection nothing but geometry and font metrics, so a paragraph that happens
 * to sit alone above whitespace - the tail of a paragraph broken across a page, a
 * question the author sets off on its own line - can outscore the threshold and reach
 * the output as a heading. Every such false heading opens a section that swallows the
 * real content below it, which is why the tests here are deliberately narrow: they only
 * ever say "this is prose", and only for text that is already styled like body text.
 */
public final class HeadingHeuristics {

    /**
     * Longest text still allowed to be a heading. Headings are labels; something longer
     * than this is a paragraph whatever else it looks like.
     */
    static final int MAX_HEADING_LENGTH = 150;

    /**
     * Terminal punctuation only counts as a sentence ending once there are enough words
     * for the text to be a sentence. It keeps short numeric labels ("1.", "Step 2.")
     * out of the veto.
     */
    static final int MIN_WORDS_FOR_SENTENCE_END = 3;

    private static final String TRAILING_QUOTES = "\"'’”»)]}";

    private HeadingHeuristics() {
    }

    /**
     * Whether the text reads as running prose rather than a heading.
     *
     * @param text the text of the candidate node
     * @return {@code true} when the text should not be promoted to a heading
     */
    public static boolean looksLikeSentence(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.length() > MAX_HEADING_LENGTH) {
            return true;
        }
        // A page break cuts a paragraph mid-sentence, so the tail starts lower case.
        if (Character.isLowerCase(trimmed.codePointAt(0))) {
            return true;
        }
        return endsSentence(trimmed);
    }

    private static boolean endsSentence(String trimmed) {
        int end = trimmed.length();
        while (end > 0 && TRAILING_QUOTES.indexOf(trimmed.charAt(end - 1)) >= 0) {
            end--;
        }
        if (end == 0) {
            return false;
        }
        char last = trimmed.charAt(end - 1);
        if (last != '.' && last != '?' && last != '!') {
            return false;
        }
        return countWords(trimmed) >= MIN_WORDS_FOR_SENTENCE_END;
    }

    private static int countWords(String text) {
        int words = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                inWord = false;
            } else if (!inWord) {
                inWord = true;
                words++;
            }
        }
        return words;
    }
}
