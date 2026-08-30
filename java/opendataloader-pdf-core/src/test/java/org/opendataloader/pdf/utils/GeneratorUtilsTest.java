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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link GeneratorUtils}'s line-join logic, used by every {@link OutputType}
 * (JSON's {@code content} field included, since {@code SerializerUtil} now builds it
 * through here instead of the semantic layer's own {@code SemanticTextNode.getValue()}).
 * {@code OutputType.JSON} exercises the plain-text path with no Markdown or HTML markup
 * wrapped around it, so it doubles as a check of the underlying join rules themselves.
 */
class GeneratorUtilsTest {

    /**
     * appendLineJoin reads StaticContainers.isKeepLineBreaks(), a ThreadLocal that is
     * null until initialized - other test classes in the suite normally reach it first
     * via their own setup, but this class should not depend on run order to do that.
     * updateContainers defaults it to true; production sets it from Config, which
     * defaults to false (DocumentProcessor.processDocument mirrors that explicitly),
     * so these tests do the same to exercise the join rules these tests are actually
     * about rather than the keep-line-breaks branch.
     */
    @BeforeAll
    static void initStaticContainers() {
        StaticContainers.updateContainers(null);
        StaticContainers.setKeepLineBreaks(false);
    }

    @Test
    void testGetTextFromLines_ordinaryLineBreakBecomesSpace() {
        TextLine line1 = new TextLine(new TextChunk("hello"));
        TextLine line2 = new TextLine(new TextChunk("world"));

        String text = GeneratorUtils.getTextFromLines(List.of(line1, line2), OutputType.JSON);

        assertEquals("hello world", text);
    }

    /**
     * A hyphen-minus ending a line is a word split by the wrap - "congru-" + "ence" is
     * "congruence", not "congru- ence" or "congru ence".
     */
    @Test
    void testGetTextFromLines_elidesHyphenMinusAtLineBreak() {
        TextLine line1 = new TextLine(new TextChunk("congru-"));
        TextLine line2 = new TextLine(new TextChunk("ence is important"));

        String text = GeneratorUtils.getTextFromLines(List.of(line1, line2), OutputType.JSON);

        assertEquals("congruence is important", text);
    }

    /** A soft hyphen (U+00AD) is elided the same way a hyphen-minus is. */
    @Test
    void testGetTextFromLines_elidesSoftHyphenAtLineBreak() {
        TextLine line1 = new TextLine(new TextChunk("congru\u00AD"));
        TextLine line2 = new TextLine(new TextChunk("ence is important"));

        String text = GeneratorUtils.getTextFromLines(List.of(line1, line2), OutputType.JSON);

        assertEquals("congruence is important", text);
    }

    /**
     * An em dash ending a line is punctuation, not a hyphenated word - eliding it the way
     * a hyphen-minus is elided drops both the dash and the word boundary, turning
     * "the story—" + "so it goes" into "the storyso it goes". The dash is kept, set flush
     * against the text on both sides, matching normal em-dash typography.
     */
    @Test
    void testGetTextFromLines_keepsEmDashAtLineBreak() {
        TextLine line1 = new TextLine(new TextChunk("the story\u2014"));
        TextLine line2 = new TextLine(new TextChunk("so it goes"));

        String text = GeneratorUtils.getTextFromLines(List.of(line1, line2), OutputType.JSON);

        assertEquals("the story\u2014so it goes", text);
    }

    /**
     * A line split into two chunks - e.g. by a font or color change mid-line - must not
     * be glued together: "0.24%" and "of all crypto transactions" is
     * "0.24% of all crypto transactions", matching the space the semantic layer's own
     * join (SemanticTextNode.getValue()) inserts between every chunk on a line.
     */
    @Test
    void testGetTextFromLines_insertsSpaceBetweenChunksOnSameLine() {
        TextLine line = new TextLine();
        line.add(new TextChunk("0.24%"));
        line.add(new TextChunk("of all crypto transactions"));

        String text = GeneratorUtils.getTextFromLines(List.of(line), OutputType.JSON);

        assertEquals("0.24% of all crypto transactions", text);
    }

    /** {@code OutputType.TXT} shares the same markup-free join as {@code OutputType.JSON}. */
    @Test
    void testGetTextFromLines_txtOutputTypeMatchesJson() {
        TextLine line1 = new TextLine(new TextChunk("hello"));
        TextLine line2 = new TextLine(new TextChunk("world"));

        String text = GeneratorUtils.getTextFromLines(List.of(line1, line2), OutputType.TXT);

        assertEquals("hello world", text);
    }

    /**
     * A chunk boundary that already carries its own space - here, on the trailing edge of
     * the first chunk ("2) ") - must not get a second one inserted next to it. A
     * numbering prefix split from its body text this way ("2) " + "Variazione...") doubled
     * to "2)  Variazione..." (two spaces): invisible to a whitespace-collapsing comparison,
     * but not to an exact one, e.g. a Markdown table row matched against literal text.
     */
    @Test
    void testGetTextFromLines_doesNotDoubleASpaceAlreadyTrailingOnAChunk() {
        TextLine line = new TextLine();
        line.add(new TextChunk("2) "));
        line.add(new TextChunk("Variazione rimanenze"));

        String text = GeneratorUtils.getTextFromLines(List.of(line), OutputType.JSON);

        assertEquals("2) Variazione rimanenze", text);
    }

    /** Same as above, but the space is baked onto the leading edge of the second chunk instead. */
    @Test
    void testGetTextFromLines_doesNotDoubleASpaceAlreadyLeadingOnAChunk() {
        TextLine line = new TextLine();
        line.add(new TextChunk("2)"));
        line.add(new TextChunk(" Variazione rimanenze"));

        String text = GeneratorUtils.getTextFromLines(List.of(line), OutputType.JSON);

        assertEquals("2) Variazione rimanenze", text);
    }

    @Test
    void testNeedsChunkSeparator_trueOnlyWhenNeitherSideHasWhitespace() {
        assertEquals(true, GeneratorUtils.needsChunkSeparator(new StringBuilder("0.24%"), "of all"));
        assertEquals(false, GeneratorUtils.needsChunkSeparator(new StringBuilder("2) "), "Variazione"));
        assertEquals(false, GeneratorUtils.needsChunkSeparator(new StringBuilder("2)"), " Variazione"));
        assertEquals(false, GeneratorUtils.needsChunkSeparator(new StringBuilder(), "Variazione"));
        assertEquals(false, GeneratorUtils.needsChunkSeparator(new StringBuilder("2)"), ""));
    }
}
