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
package org.opendataloader.pdf.processors;

import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.utils.TextNodeStatistics;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class HeadingProcessorTest {

    @Test
    public void testProcessHeadings() {
        StaticContainers.setIsDataLoader(true);
        StaticLayoutContainers.setHeadings(new ArrayList<>());
        List<IObject> contents = new ArrayList<>();
        SemanticParagraph paragraph1 = new SemanticParagraph();
        contents.add(paragraph1);
        paragraph1.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "HEADING", "Font1", 20, 700, 0, 30.0, new double[]{0.0},
            null, 0)));
        SemanticParagraph paragraph2 = new SemanticParagraph();
        contents.add(paragraph2);
        paragraph2.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0),
            "Paragraph", "Font1", 10, 700, 0, 20.0, new double[]{0.5},
            null, 0)));
        HeadingProcessor.processHeadings(contents, false);
        Assertions.assertEquals(2, contents.size());
        Assertions.assertTrue(contents.get(0) instanceof SemanticHeading);
    }

    /**
     * A drop cap scores exactly like a real heading on font size and weight alone - that
     * is the entire point of a drop cap - so isBodyText's size comparison cannot veto it.
     * "“D" (a paragraph opening with quoted dialogue, its drop-capped first letter
     * split into its own node) at heading-sized font, followed by "efine your terms!" at
     * body size, must stay a plain paragraph: concatenating the two spells "define", a
     * real word, which is exactly what tells this apart from an actual heading. Found in
     * real output from "Read, Reason, Write" as a spurious "# “D" heading.
     */
    @Test
    public void aDropCapIsNotMistakenForAHeading() {
        StaticContainers.setIsDataLoader(true);
        StaticLayoutContainers.setHeadings(new ArrayList<>());
        List<IObject> contents = new ArrayList<>();
        SemanticParagraph dropCap = new SemanticParagraph();
        contents.add(dropCap);
        dropCap.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "“D", "Font1", 20, 700, 0, 30.0, new double[]{0.0},
            null, 0)));
        SemanticParagraph rest = new SemanticParagraph();
        contents.add(rest);
        rest.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0),
            "efine your terms!", "Font1", 10, 700, 0, 20.0, new double[]{0.5},
            null, 0)));

        HeadingProcessor.processHeadings(contents, false);

        assertThat(contents.get(0)).isNotInstanceOf(SemanticHeading.class);
    }

    /**
     * A drop-capped letter that is already a complete one-letter word on its own - "I" -
     * is the same bug in a different shape: the next node starts an unrelated word
     * ("really"), so "I" + "really" concatenating into "ireally" is never going to be a
     * real word, unlike "D" + "efine". Checking whether the bare letter is already a word
     * catches this case too. Found in real output as a spurious "# “I" heading opening a
     * paragraph of quoted dialogue ("I really love Spencer's Camaro...").
     */
    @Test
    public void aDropCapThatIsAlreadyAOneLetterWordOnItsOwnIsNotMistakenForAHeading() {
        StaticContainers.setIsDataLoader(true);
        StaticLayoutContainers.setHeadings(new ArrayList<>());
        List<IObject> contents = new ArrayList<>();
        SemanticParagraph dropCap = new SemanticParagraph();
        contents.add(dropCap);
        dropCap.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "“I", "Font1", 20, 700, 0, 30.0, new double[]{0.0},
            null, 0)));
        SemanticParagraph rest = new SemanticParagraph();
        contents.add(rest);
        rest.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0),
            "really love Spencer’s Camaro", "Font1", 10, 700, 0, 20.0, new double[]{0.5},
            null, 0)));

        HeadingProcessor.processHeadings(contents, false);

        assertThat(contents.get(0)).isNotInstanceOf(SemanticHeading.class);
    }

    @Test
    public void testDetectHeadingsLevels() {
        StaticContainers.setIsDataLoader(true);
        List<SemanticHeading> headings = new ArrayList<>();
        StaticLayoutContainers.setHeadings(headings);
        SemanticHeading heading1 = new SemanticHeading();
        headings.add(heading1);
        heading1.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "HEADING", "Font1", 20, 700, 0, 30.0, new double[]{0.0},
            null, 0)));
        SemanticHeading heading2 = new SemanticHeading();
        headings.add(heading2);
        heading2.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0),
            "Paragraph", "Font1", 10, 700, 0, 20.0, new double[]{0.5},
            null, 0)));
        HeadingProcessor.detectHeadingsLevels();
        Assertions.assertEquals(2, headings.size());
        Assertions.assertEquals(1, headings.get(0).getHeadingLevel());
        Assertions.assertEquals(2, headings.get(1).getHeadingLevel());
    }

    private static SemanticParagraph textNodeOfSize(String text, double fontSize) {
        SemanticParagraph node = new SemanticParagraph();
        node.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            text, "Font1", fontSize, 400, 0, fontSize, new double[]{0.0}, null, 0)));
        return node;
    }

    /**
     * Reproduces a page whose only text nodes are the tail of a paragraph broken across
     * a page boundary ("personally could be.") and three footnotes: too few samples for
     * the page's own font-size statistics to mean anything, and the footnotes happen to
     * be smaller, which used to make the candidate look "larger than body" and skip the
     * sentence-shape veto entirely.
     */
    @Test
    public void isBodyTextFallsBackToSentenceShapeWhenThePageHasTooFewSamples() {
        TextNodeStatistics statistics = new TextNodeStatistics();
        statistics.addTextNode(textNodeOfSize("personally could be.", 15.007));
        statistics.addTextNode(textNodeOfSize("footnote one", 11.255));
        statistics.addTextNode(textNodeOfSize("footnote two", 11.255));
        statistics.addTextNode(textNodeOfSize("footnote three", 11.255));

        SemanticTextNode candidate = textNodeOfSize("personally could be.", 15.007);

        assertThat(HeadingProcessor.isBodyText(candidate, statistics)).isTrue();
    }

    /**
     * The same fallback must not fire for a genuine heading just because its page happens
     * to carry few samples - it only ever demotes a candidate the sentence-shape check
     * already flags as prose.
     */
    @Test
    public void isBodyTextStillKeepsARealHeadingOnASparsePage() {
        TextNodeStatistics statistics = new TextNodeStatistics();
        statistics.addTextNode(textNodeOfSize("footnote one", 11.255));

        SemanticTextNode candidate = textNodeOfSize("CONCLUSION", 25.009);

        assertThat(HeadingProcessor.isBodyText(candidate, statistics)).isFalse();
    }
}
