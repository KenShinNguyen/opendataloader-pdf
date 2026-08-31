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

import org.opendataloader.pdf.entities.MarginAnnotation;
import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MarginAnnotationProcessorTest {

    private static SemanticParagraph paragraphAt(String text, double left, double bottom, double right, double top) {
        SemanticParagraph paragraph = new SemanticParagraph();
        paragraph.add(new TextLine(new TextChunk(new BoundingBox(0, left, bottom, right, top),
            text, "Font1", 9.0, 400, 0, 9.0, new double[]{0.0}, null, 0)));
        return paragraph;
    }

    /**
     * Mirrors the real geometry found on page 26 of "Read, Reason, Write": a body
     * paragraph spanning the usual text column, and a short callout sitting well to
     * its right ("Attention-getting opening.") in the margin. The callout must come
     * out as a {@link MarginAnnotation} tagged for the right margin, not stay a plain
     * paragraph a downstream ListProcessor/HeadingProcessor pass could still misread.
     */
    @Test
    public void rightMarginCalloutIsExtracted() {
        SemanticParagraph body = paragraphAt("\"Globalization\"-broadly defined, means the increasing interconnectedness of nations.",
            84.003, 72.111, 483.372, 129.497);
        SemanticParagraph callout = paragraphAt("Attention-getting opening.", 500.995, 107.27, 554.988, 127.867);
        List<IObject> contents = new ArrayList<>(List.of(body, callout));

        MarginAnnotationProcessor.Extraction extraction = MarginAnnotationProcessor.extractMarginAnnotations(contents);

        assertThat(extraction.remaining).containsExactly(body);
        assertThat(extraction.annotations).hasSize(1);
        MarginAnnotation annotation = (MarginAnnotation) extraction.annotations.get(0);
        assertThat(annotation.getPosition()).isEqualTo(MarginAnnotation.Position.RIGHT);
    }

    /**
     * The mirror-image left-margin case, e.g. "1st point of refutation: Globalization
     * has been mischaracterized..." from page 27, positioned left of a body column
     * that starts around x=128.
     */
    @Test
    public void leftMarginCalloutIsExtracted() {
        SemanticParagraph body = paragraphAt("Holtz-Eakin, Douglas. \"'Globalization' Shouldn't Be...",
            128.758, 80.839, 490.987, 104.44);
        SemanticParagraph callout = paragraphAt("1st point of refutation: Globalization has been mischaracterized.",
            50.162, 610.909, 106.066, 659.101);
        List<IObject> contents = new ArrayList<>(List.of(body, callout));

        MarginAnnotationProcessor.Extraction extraction = MarginAnnotationProcessor.extractMarginAnnotations(contents);

        assertThat(extraction.remaining).containsExactly(body);
        assertThat(extraction.annotations).hasSize(1);
        MarginAnnotation annotation = (MarginAnnotation) extraction.annotations.get(0);
        assertThat(annotation.getPosition()).isEqualTo(MarginAnnotation.Position.LEFT);
    }

    /**
     * The bare leader-line digit ("1") connecting a highlighted body passage to its
     * margin callout is only ~3.3pt wide - real output has 17 of these across one
     * document, each previously surfacing as an orphan single-character list item
     * with no content of its own. It must be extracted just like the callout text is.
     */
    @Test
    public void bareLeaderLineDigitIsExtracted() {
        SemanticParagraph body = paragraphAt("Body text continues across the full column width here.",
            84.003, 72.111, 483.372, 129.497);
        SemanticParagraph marker = paragraphAt("1", 489.601, 114.166, 492.901, 125.784);
        List<IObject> contents = new ArrayList<>(List.of(body, marker));

        MarginAnnotationProcessor.Extraction extraction = MarginAnnotationProcessor.extractMarginAnnotations(contents);

        assertThat(extraction.remaining).containsExactly(body);
        assertThat(extraction.annotations).hasSize(1);
    }

    /**
     * A photo credit printed sideways along an image's edge ("Drazen Zigic/Shutterstock")
     * sits in the same margin zone as a real callout but is much taller than it is wide -
     * the opposite shape of a callout box. It must be left alone, not relabeled "annotation".
     */
    @Test
    public void rotatedImageCreditIsNotExtracted() {
        SemanticParagraph body = paragraphAt("Body text continues across the full column width here.",
            84.003, 72.111, 483.372, 129.497);
        SemanticParagraph credit = paragraphAt("Drazen Zigic/Shutterstock", 529.051, 197.046, 540.325, 287.808);
        List<IObject> contents = new ArrayList<>(List.of(body, credit));

        MarginAnnotationProcessor.Extraction extraction = MarginAnnotationProcessor.extractMarginAnnotations(contents);

        assertThat(extraction.remaining).containsExactly(body, credit);
        assertThat(extraction.annotations).isEmpty();
    }

    /**
     * A genuine two-column academic layout must never be gutted: both columns are far
     * wider than any real margin annotation, so neither qualifies as a narrow-enough
     * candidate even though each sits well outside the other's horizontal extent.
     */
    @Test
    public void twoColumnBodyLayoutIsUntouched() {
        SemanticParagraph leftColumn = paragraphAt("Left column body text spanning a wide measure.",
            72.0, 72.0, 300.0, 700.0);
        SemanticParagraph rightColumn = paragraphAt("Right column body text spanning a wide measure.",
            320.0, 72.0, 550.0, 700.0);
        List<IObject> contents = new ArrayList<>(List.of(leftColumn, rightColumn));

        MarginAnnotationProcessor.Extraction extraction = MarginAnnotationProcessor.extractMarginAnnotations(contents);

        assertThat(extraction.remaining).containsExactly(leftColumn, rightColumn);
        assertThat(extraction.annotations).isEmpty();
    }

    /**
     * A page with no wide paragraph at all (e.g. an image-only page) has no reliable
     * body column to compare against, so nothing is reclassified - better to miss a
     * real annotation than to misclassify against a baseline computed from nothing.
     */
    @Test
    public void pageWithNoBodyCandidatesIsUntouched() {
        SemanticParagraph shortLine = paragraphAt("Fig. 1", 300.0, 400.0, 330.0, 412.0);
        List<IObject> contents = new ArrayList<>(List.of(shortLine));

        MarginAnnotationProcessor.Extraction extraction = MarginAnnotationProcessor.extractMarginAnnotations(contents);

        assertThat(extraction.remaining).containsExactly(shortLine);
        assertThat(extraction.annotations).isEmpty();
    }
}
