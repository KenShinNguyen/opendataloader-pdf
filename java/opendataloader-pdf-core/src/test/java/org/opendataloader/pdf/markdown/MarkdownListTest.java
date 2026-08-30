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
