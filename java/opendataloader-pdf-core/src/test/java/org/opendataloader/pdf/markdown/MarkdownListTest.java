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
package org.opendataloader.pdf.markdown;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.listLabelsDetection.NumberingStyleNames;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How a list item opens in Markdown. Every list used to open with "-", which left an
 * ordered list carrying both a bullet and the label still sitting in the item's text.
 */
class MarkdownListTest {

    /**
     * Renders one item the way {@code writeList} does, so the marker and the label
     * stripping are exercised together.
     */
    private static String renderItem(String numberingStyle, String itemText, int labelLength) {
        String marker = MarkdownGenerator.listItemMarker(numberingStyle, itemText, labelLength);
        String body = MarkdownGenerator.markerReplacesLabel(numberingStyle, marker)
            ? MarkdownGenerator.stripListLabel(itemText, labelLength)
            : itemText;
        return marker + body;
    }

    @Test
    void decimalNumberingBecomesANativeMarkdownMarker() {
        assertThat(renderItem(NumberingStyleNames.ARABIC_NUMBERS, "1. Desire to belong", 2))
            .isEqualTo("1. Desire to belong");
        assertThat(renderItem(NumberingStyleNames.ARABIC_NUMBERS, "1) First", 2))
            .isEqualTo("1) First");
    }

    /**
     * Reusing the document's own label rather than renumbering keeps a list split across
     * a page break running on from where it left off.
     */
    @Test
    void decimalNumberingKeepsTheDocumentsOwnNumber() {
        assertThat(renderItem(NumberingStyleNames.ARABIC_NUMBERS, "5. Various scams", 2))
            .isEqualTo("5. Various scams");
    }

    @Test
    void aDecimalLabelWithoutADelimiterGetsOne() {
        assertThat(renderItem(NumberingStyleNames.ARABIC_NUMBERS, "3 No delimiter", 1))
            .isEqualTo("3. No delimiter");
    }

    /**
     * Markdown cannot express roman or lettered numbering, and rendering it as decimals
     * would renumber the document, so the label stays in the text where it can be read.
     */
    @Test
    void romanAndLetteredNumberingKeepTheirLabelInTheText() {
        assertThat(renderItem(NumberingStyleNames.ROMAN_NUMBERS, "I. Understanding our inner psychology", 2))
            .isEqualTo("- I. Understanding our inner psychology");
        assertThat(renderItem(NumberingStyleNames.ENGLISH_LETTERS, "A. Laws of physics", 2))
            .isEqualTo("- A. Laws of physics");
    }

    @Test
    void aBulletReplacesTheLabel() {
        assertThat(renderItem(NumberingStyleNames.UNORDERED, "* Cholesterol Treatment Trialists.", 1))
            .isEqualTo("- Cholesterol Treatment Trialists.");
    }

    @Test
    void aLabelTooOddToReuseFallsBackToABullet() {
        assertThat(renderItem(NumberingStyleNames.ARABIC_NUMBERS, "12345678901. Overlong", 12))
            .isEqualTo("- 12345678901. Overlong");
        assertThat(renderItem(NumberingStyleNames.ARABIC_NUMBERS, "Step #1. Go to the source", 0))
            .isEqualTo("- Step #1. Go to the source");
    }

    /**
     * A nested list has to start at or past the column where its parent item's text
     * starts, or Markdown reads it as a sibling instead of a child.
     */
    /**
     * ListProcessor detects a list by geometry, and a paragraph that opens with a
     * decimal number reads the same way a one-item list does: digits, then a delimiter.
     * "0.24% of all crypto transactions..." was detected this way with a reported label
     * of "0." - a real span of the text, but the start of "0.24", not a list marker.
     * Reusing it split the number itself: "0." became the marker, ".24%" was left as the
     * item's text. A genuine label is never immediately followed by another digit, so
     * that alone tells a real marker from the front of a longer numeral.
     */
    @Test
    void aDecimalNumberOpeningAParagraphIsNotMistakenForALabel() {
        assertThat(renderItem(NumberingStyleNames.ARABIC_NUMBERS,
            "0.24% of all crypto transactions were linked to illicit activities.", 2))
            .isEqualTo("- 0.24% of all crypto transactions were linked to illicit activities.");
    }

    /**
     * The geometry layer doesn't always report the delimiter as part of the label - a
     * span of just "0" (labelLength 1) reads the same way as "0." (labelLength 2) once
     * {@code readLabel} trims it, but the digit-immediately-after check alone doesn't
     * reach it: the character right after a length-1 label is the delimiter itself, not
     * a digit, so that check alone passed it through. "0." became the marker and ".24%"
     * was left dangling as the item's text - "0. .24% of all crypto...". Regression test
     * for that gap: readLabel now also looks at what follows the label's own leading
     * digit run, not just the reported span, and catches this case too.
     */
    @Test
    void aDecimalNumberOpeningAParagraphIsNotMistakenForALabelEvenWithoutTheDelimiterInSpan() {
        assertThat(renderItem(NumberingStyleNames.ARABIC_NUMBERS,
            "0.24% of all crypto transactions were linked to illicit activities.", 1))
            .isEqualTo("- 0.24% of all crypto transactions were linked to illicit activities.");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "3.14 is pi|- 3.14 is pi",
        "1.5 million people|- 1.5 million people",
        "2.0 out of 5 stars|- 2.0 out of 5 stars"
    })
    void otherDecimalNumbersAreNotMistakenForALabelEither(String itemText, String expected) {
        assertThat(renderItem(NumberingStyleNames.ARABIC_NUMBERS, itemText, 2)).isEqualTo(expected);
    }

    /** Same set, but with the delimiter left out of the reported span (labelLength 1). */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "3.14 is pi|- 3.14 is pi",
        "1.5 million people|- 1.5 million people",
        "2.0 out of 5 stars|- 2.0 out of 5 stars"
    })
    void otherDecimalNumbersAreNotMistakenForALabelEitherWithoutTheDelimiterInSpan(String itemText, String expected) {
        assertThat(renderItem(NumberingStyleNames.ARABIC_NUMBERS, itemText, 1)).isEqualTo(expected);
    }

    /**
     * An ordinal number opening a paragraph reads the same way as a decimal one: digits,
     * then what looks like it could be a delimiter. "1st point of refutation: Globalization
     * has been mischaracterized." was detected this way with a reported label of "1" - a
     * real span of the text, but the start of "1st", not a list marker. Reusing it split
     * the word itself: "1" became the marker and "st point of refutation..." was left as
     * the item's text - "1. st point of refutation...". Found in real output from "Read,
     * Reason, Write". A genuine label is never immediately followed by an ordinal suffix
     * either, so that tells a real marker from the front of an ordinal number too.
     */
    @Test
    void anOrdinalNumberOpeningAParagraphIsNotMistakenForALabel() {
        assertThat(renderItem(NumberingStyleNames.ARABIC_NUMBERS,
            "1st point of refutation: Globalization has been mischaracterized.", 1))
            .isEqualTo("- 1st point of refutation: Globalization has been mischaracterized.");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "2nd point: Isolationism will hurt the U.S. economically.|- 2nd point: Isolationism will hurt the U.S. economically.",
        "3rd time's the charm.|- 3rd time's the charm.",
        "4th of July plans are set.|- 4th of July plans are set.",
        "21st century skills matter.|- 21st century skills matter."
    })
    void otherOrdinalNumbersAreNotMistakenForALabelEither(String itemText, String expected) {
        assertThat(renderItem(NumberingStyleNames.ARABIC_NUMBERS, itemText, 1)).isEqualTo(expected);
    }

    /**
     * A digit run immediately followed by letters that merely start with an
     * ordinal-suffix-looking pair - not an ordinal number, since the suffix does not end
     * there - is not mistaken for one: continuesAsOrdinalSuffix also checks that nothing
     * but a non-letter follows "st"/"nd"/"rd"/"th", so "1sting" (an invented word,
     * exercising the boundary, not a real ordinal) does not fool it into rejecting a label
     * that is otherwise perfectly ordinary.
     */
    @Test
    void aDigitFollowedByAWordThatMerelyStartsWithAnOrdinalSuffixIsStillALabel() {
        assertThat(renderItem(NumberingStyleNames.ARABIC_NUMBERS, "1sting operation", 1))
            .isEqualTo("1. sting operation");
    }

    @Test
    void aNestedListIsIndentedUnderItsParentItem() {
        String parent = "I. Understanding our inner psychology";
        String parentMarker = MarkdownGenerator.listItemMarker(NumberingStyleNames.ROMAN_NUMBERS, parent, 2);
        StringBuilder rendered = new StringBuilder(parentMarker).append(parent).append('\n');

        String indent = " ".repeat(parentMarker.length());
        for (String child : new String[] {"1. Desire to belong", "2. Desire to be right"}) {
            rendered.append(indent).append(renderItem(NumberingStyleNames.ARABIC_NUMBERS, child, 2)).append('\n');
        }

        assertThat(rendered.toString()).isEqualTo(
            "- I. Understanding our inner psychology\n"
                + "  1. Desire to belong\n"
                + "  2. Desire to be right\n");
    }
}
