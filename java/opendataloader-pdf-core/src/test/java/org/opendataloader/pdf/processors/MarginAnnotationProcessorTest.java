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
        paragraph.add(textLineAt(text, left, bottom, right, top));
        return paragraph;
    }

    private static TextLine textLineAt(String text, double left, double bottom, double right, double top) {
        return new TextLine(new TextChunk(new BoundingBox(0, left, bottom, right, top),
            text, "Font1", 9.0, 400, 0, 9.0, new double[]{0.0}, null, 0));
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
     * A narrow sidebar or note box full of running prose is a different thing from a
     * one-to-three-line callout, even though it sits in the exact same margin position
     * a real callout would. Something 300pt tall - several paragraphs' worth - must be
     * left as ordinary body content, not relabelled a callout-shaped "annotation".
     */
    @Test
    public void aTallNarrowSidebarIsNotExtracted() {
        SemanticParagraph body = paragraphAt("Body text continues across the full column width here.",
            84.003, 72.111, 483.372, 129.497);
        SemanticParagraph sidebar = paragraphAt("A long sidebar full of running prose spanning many lines of real content.",
            500.995, 107.27, 554.988, 407.27);
        List<IObject> contents = new ArrayList<>(List.of(body, sidebar));

        MarginAnnotationProcessor.Extraction extraction = MarginAnnotationProcessor.extractMarginAnnotations(contents);

        assertThat(extraction.remaining).containsExactly(body, sidebar);
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

    /**
     * Mirrors real geometry from page 10 of "Read, Reason, Write": a column of bare
     * footnote/endnote reference-marker digits ("1".."5") sitting just past the
     * body column's right edge. {@code ListProcessor.processLists} - a cross-page
     * pass over raw {@link TextLine}s that runs before paragraphs even exist - reads
     * this exact shape (a run of increasing digits) as a numbered list and groups
     * it, leaving every item's own content empty since there is no body text for a
     * bare reference marker to hold; the fake list then surfaces as a run of empty
     * "1." / "2." / ... items spliced into the middle of a body sentence. Catching
     * these as {@link TextLine}s, before that pass ever runs, is what
     * {@link MarginAnnotationProcessor#extractMarginAnnotationsFromTextLines} is for.
     */
    @Test
    public void bareFootnoteMarkerColumnIsExtractedFromTextLinesBeforeListDetection() {
        TextLine body = textLineAt("H.R. 51 has both the facts and the Constitution on its side.",
            84.003, 85.669, 483.314, 115.572);
        TextLine marker1 = textLineAt("1", 489.601, 444.287, 492.901, 455.906);
        TextLine marker2 = textLineAt("2", 489.601, 403.009, 494.419, 414.628);
        TextLine marker3 = textLineAt("3", 489.601, 361.723, 494.314, 373.341);
        TextLine marker4 = textLineAt("4", 489.601, 196.49, 494.395, 208.109);
        TextLine marker5 = textLineAt("5", 489.601, 100.125, 494.483, 111.743);
        List<IObject> contents = new ArrayList<>(List.of(body, marker1, marker2, marker3, marker4, marker5));

        MarginAnnotationProcessor.Extraction extraction =
                MarginAnnotationProcessor.extractMarginAnnotationsFromTextLines(contents);

        assertThat(extraction.remaining).containsExactly(body);
        assertThat(extraction.annotations).hasSize(5);
    }

    /**
     * Not every footnote-marker column is spaced like page 10's - some sit close
     * enough together (real output: "Nicholas Haslam" essay) that the plain
     * vertical-gap check above would read them as one multi-line callout and
     * merge them into a single nonsensical annotation ("1 2" instead of two
     * separate ones). A bare digit is always its own standalone marker, no matter
     * how close its neighbor sits - this must produce two annotations, not one.
     */
    @Test
    public void tightlySpacedBareMarkersAreNotMergedIntoOne() {
        TextLine body = textLineAt("PREREADING QUESTIONS How do you use the word trauma?",
            84.003, 85.669, 483.314, 104.44);
        TextLine marker1 = textLineAt("1", 489.601, 444.287, 492.901, 455.906);
        TextLine marker2 = textLineAt("2", 489.601, 432.287, 492.901, 443.906);
        List<IObject> contents = new ArrayList<>(List.of(body, marker1, marker2));

        MarginAnnotationProcessor.Extraction extraction =
                MarginAnnotationProcessor.extractMarginAnnotationsFromTextLines(contents);

        assertThat(extraction.remaining).containsExactly(body);
        assertThat(extraction.annotations).hasSize(2);
    }

    /**
     * The same guard has to hold when the tight neighbor isn't another bare
     * marker but a real callout's own text: a leader-line connector digit sitting
     * just above "1st point of refutation..." must not fuse onto it ("2 1st
     * point of refutation..."), losing the digit's own identity as a marker.
     */
    @Test
    public void bareMarkerDoesNotFuseOntoAnAdjacentCalloutsText() {
        TextLine body = textLineAt("Body text continues across the full column width here.",
            128.758, 80.839, 490.987, 104.44);
        TextLine marker = textLineAt("2", 50.162, 432.287, 53.462, 443.906);
        TextLine calloutText = textLineAt("1st point of refutation: Globalization has been mischaracterized.",
            50.162, 420.287, 106.066, 431.906);
        List<IObject> contents = new ArrayList<>(List.of(body, marker, calloutText));

        MarginAnnotationProcessor.Extraction extraction =
                MarginAnnotationProcessor.extractMarginAnnotationsFromTextLines(contents);

        assertThat(extraction.remaining).containsExactly(body);
        assertThat(extraction.annotations).hasSize(2);
    }

    /**
     * A genuine multi-line margin callout ("Introduction connects ambivalence in
     * American character to conflict over gun control.", real 3-line geometry from
     * page 37, body column matching the left-margin-annotation page style used
     * elsewhere in this file) is still several separate {@link TextLine}s at this
     * pipeline stage - paragraph grouping hasn't happened yet. Classifying each
     * line independently would fragment one real annotation into three one-line
     * ones; {@link MarginAnnotationProcessor#extractMarginAnnotationsFromTextLines}
     * must instead produce exactly one {@link MarginAnnotation} holding all three.
     */
    @Test
    public void multiLineCalloutStaysOneAnnotationNotOnePerLine() {
        TextLine body = textLineAt("Body text continues across the full column width here.",
            128.758, 80.839, 490.987, 104.44);
        TextLine calloutLine1 = textLineAt("Introduction connects ambivalence in",
            50.162, 265.828, 106.13, 284.828);
        TextLine calloutLine2 = textLineAt("American character to conflict over",
            50.162, 245.828, 104.9, 264.828);
        TextLine calloutLine3 = textLineAt("gun control.",
            50.162, 227.397, 88.4, 245.828);
        List<IObject> contents = new ArrayList<>(List.of(body, calloutLine1, calloutLine2, calloutLine3));

        MarginAnnotationProcessor.Extraction extraction =
                MarginAnnotationProcessor.extractMarginAnnotationsFromTextLines(contents);

        assertThat(extraction.remaining).containsExactly(body);
        assertThat(extraction.annotations).hasSize(1);
    }

    /**
     * The other side of the same coin as the multi-line test above: two distinct
     * one-line callouts stacked in the same margin column (real page 7 geometry,
     * ~80pt apart - far more than one line's worth of gap) must stay two separate
     * {@link MarginAnnotation}s, not get merged into one because they share a
     * column the way a real multi-line callout's own lines do.
     */
    @Test
    public void distinctSingleLineCalloutsInTheSameColumnAreNotMerged() {
        TextLine body = textLineAt("Body text continues across the full column width here.",
            128.758, 80.839, 490.987, 104.44);
        TextLine callout1 = textLineAt("Attention-getting introduction.", 50.162, 278.387, 104.155, 298.983);
        TextLine callout2 = textLineAt("Clever extended metaphor.", 50.162, 203.751, 101.505, 224.463);
        List<IObject> contents = new ArrayList<>(List.of(body, callout1, callout2));

        MarginAnnotationProcessor.Extraction extraction =
                MarginAnnotationProcessor.extractMarginAnnotationsFromTextLines(contents);

        assertThat(extraction.remaining).containsExactly(body);
        assertThat(extraction.annotations).hasSize(2);
    }

    /**
     * A genuine two-column layout at the TextLine level must be just as safe as it
     * is at the paragraph level - both columns are far wider than any real margin
     * marker, so neither line qualifies as a narrow-enough candidate.
     */
    @Test
    public void twoColumnBodyLayoutIsUntouchedAtTextLineLevel() {
        TextLine leftColumn = textLineAt("Left column body text spanning a wide measure.", 72.0, 680.0, 300.0, 700.0);
        TextLine rightColumn = textLineAt("Right column body text spanning a wide measure.", 320.0, 680.0, 550.0, 700.0);
        List<IObject> contents = new ArrayList<>(List.of(leftColumn, rightColumn));

        MarginAnnotationProcessor.Extraction extraction =
                MarginAnnotationProcessor.extractMarginAnnotationsFromTextLines(contents);

        assertThat(extraction.remaining).containsExactly(leftColumn, rightColumn);
        assertThat(extraction.annotations).isEmpty();
    }
}
