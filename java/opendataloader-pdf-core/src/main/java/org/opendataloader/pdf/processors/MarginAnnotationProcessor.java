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
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextLine;

import java.util.ArrayList;
import java.util.List;

/**
 * Pulls short, narrow content positioned at or past the outer edge of a
 * page's dominant body-text column - a margin callout ("1st point of
 * refutation..."), the tiny leader-line digit that connects one to its
 * highlighted body passage, or a column of bare footnote/endnote reference
 * markers - out of the normal reading flow, before {@link ListProcessor} or
 * {@link HeadingProcessor} get a chance to treat it as an ordinary list item
 * or paragraph. Two entry points exist because list detection itself happens
 * at two different pipeline stages: {@link #extractMarginAnnotationsFromTextLines}
 * runs on raw {@link TextLine}s ahead of {@link ListProcessor#processLists}
 * (the cross-page pass that groups list-shaped text before paragraphs even
 * exist), and {@link #extractMarginAnnotations} runs on {@link SemanticParagraph}s
 * ahead of {@link ListProcessor#processListsFromTextNodes} (the later,
 * per-page pass). Both share the same geometry (see {@link #classify}).
 *
 * <p>Without this, a margin callout gets spliced into the body paragraph/list
 * flow wherever its bounding box happens to fall in reading order (mid
 * sentence, in the worst case), and a lone connector digit surfaces as an
 * orphan single-character list item ("1", "2.") with no content of its own.
 *
 * <p>Detection is geometric only, which is a deliberate, narrow trade-off, not
 * a proof that a candidate is actually pedagogical marginalia: a candidate's
 * near edge only has to clear the body column's edge by
 * {@link #COLUMN_EDGE_TOLERANCE}, not clear it entirely, and nothing here
 * confirms the candidate is connected to a highlighted body passage the way a
 * real callout is. Two bounds keep the false-positive surface small: a
 * candidate must be narrower than a real second body column could plausibly
 * be ({@link #MAX_ANNOTATION_WIDTH}, so a genuine two-column layout is never
 * mistaken for margin content - both of its columns are well over that
 * width), and shorter than a real sidebar or note box full of running prose
 * would be ({@link #MAX_ANNOTATION_HEIGHT}). A rotated image credit printed
 * sideways along an image edge is excluded separately by its tall/narrow
 * aspect ratio. A false positive here is a stray page-edge scrap (a folio
 * HeaderFooterProcessor's own repeat-detection missed, say) getting labelled
 * "annotation" instead of "paragraph" - still pulled out of the body flow
 * either way, and never anything from the body column itself, but the label
 * itself is not guaranteed accurate.
 */
public class MarginAnnotationProcessor {

    /** Minimum width (pt) for a paragraph to count towards the page's body column. */
    private static final double BODY_CANDIDATE_MIN_WIDTH = 200.0;

    /** Horizontal tolerance (pt) for "starts at, or past, the body column's edge". */
    private static final double COLUMN_EDGE_TOLERANCE = 5.0;

    /**
     * Upper bound (pt) on a margin-annotation candidate's own width. A real second
     * column in a multi-column layout is far wider than this; keeping the cap well
     * under a plausible column width is what stops this from ever gutting a genuine
     * two-column page.
     */
    private static final double MAX_ANNOTATION_WIDTH = 150.0;

    /**
     * Upper bound (pt) on a margin-annotation candidate's own height. The tallest
     * real callout found across the corpus this was built from was ~57pt (a
     * three-line introduction annotation); 100pt leaves headroom for a longer one
     * while still excluding a genuine sidebar/note/quote box's worth of running
     * prose, which a reader would expect to stay in the body flow, not be
     * relabelled a one-line-callout-shaped "annotation".
     */
    private static final double MAX_ANNOTATION_HEIGHT = 100.0;

    /**
     * A rotated image credit/caption (printed sideways along an image's edge) is
     * much taller than it is wide; a real margin callout is the opposite - wide
     * and short. {@link #ROTATED_CAPTION_MIN_WIDTH} keeps this from also catching
     * a bare leader-line digit, which is narrow enough to look "tall" too.
     */
    private static final double ROTATED_CAPTION_ASPECT_RATIO = 3.0;
    private static final double ROTATED_CAPTION_MIN_WIDTH = 8.0;

    /**
     * How large a vertical gap between two same-column {@link TextLine}s, relative
     * to the line height, still counts as "the same callout" rather than two
     * separate ones, in {@link #groupIntoParagraphs}. 1.5x comfortably covers
     * ordinary single-line-spacing gaps (including the ~1pt inter-line leading
     * this codebase's own synthetic test fixtures use) while staying far under the
     * dozens-of-points gaps between separate footnote/endnote markers or separate
     * one-line callouts stacked in the same margin column.
     */
    private static final double LINE_GROUPING_MAX_GAP_RATIO = 1.5;

    /**
     * Result of {@link #extractMarginAnnotations}: the page's contents with margin
     * annotations removed, and the annotations that were pulled out (in their
     * original relative order), still carrying their own page number and bounding
     * box so no position information is lost.
     */
    public static class Extraction {
        public final List<IObject> remaining;
        public final List<IObject> annotations;

        private Extraction(List<IObject> remaining, List<IObject> annotations) {
            this.remaining = remaining;
            this.annotations = annotations;
        }
    }

    public static Extraction extractMarginAnnotations(List<IObject> pageContents) {
        List<IObject> bodyCandidates = bodyCandidates(pageContents, SemanticParagraph.class);
        if (bodyCandidates.isEmpty()) {
            return new Extraction(pageContents, new ArrayList<>());
        }
        double bodyLeft = median(bodyCandidates, true);
        double bodyRight = median(bodyCandidates, false);

        List<IObject> remaining = new ArrayList<>(pageContents.size());
        List<IObject> annotations = new ArrayList<>();
        for (IObject content : pageContents) {
            MarginAnnotation.Position position = content instanceof SemanticParagraph
                    ? classify(content, bodyLeft, bodyRight) : null;
            if (position == null) {
                remaining.add(content);
            } else {
                annotations.add(new MarginAnnotation((SemanticParagraph) content, position));
            }
        }
        return new Extraction(remaining, annotations);
    }

    /**
     * The {@link SemanticParagraph}-level pass above runs too late to catch every
     * case: {@link ListProcessor#processLists} - a cross-page pass over raw
     * {@link TextLine}s that runs before {@link org.verapdf.wcag.algorithms.entities.IObject}s
     * are ever grouped into paragraphs - keys off text shape alone (a run of
     * digits reading like list labels) and happily groups a column of bare
     * footnote/endnote reference markers into a fake list first: every item's
     * own content is just its label, with no body text at all, since the real
     * digit-to-citation link lives outside the reading-order text this tool
     * extracts. Found in real output as a dozen empty "N." list items spliced
     * into the middle of a body sentence ("Congress generally has" / (12 empty
     * items) / "considered a prospective state's population...").
     *
     * <p>Call this per page between {@link HeaderFooterProcessor#processHeadersAndFooters}
     * and {@link ListProcessor#processLists} - after header/footer detection has
     * had its own, more reliable, repeats-across-pages chance at any running-head
     * page number, but before the list pass can absorb what's left. Extracted lines
     * are grouped by {@link #groupIntoParagraphs} before being wrapped: a genuine
     * multi-line callout ("Introduction connects ambivalence...") is still several
     * {@link TextLine}s at this pipeline stage, and classifying each one on its own
     * instead would fragment it into one one-line {@link MarginAnnotation} per line.
     */
    public static Extraction extractMarginAnnotationsFromTextLines(List<IObject> pageContents) {
        List<IObject> bodyCandidates = bodyCandidates(pageContents, TextLine.class);
        if (bodyCandidates.isEmpty()) {
            return new Extraction(pageContents, new ArrayList<>());
        }
        double bodyLeft = median(bodyCandidates, true);
        double bodyRight = median(bodyCandidates, false);

        List<IObject> remaining = new ArrayList<>(pageContents.size());
        List<TextLine> leftLines = new ArrayList<>();
        List<TextLine> rightLines = new ArrayList<>();
        for (IObject content : pageContents) {
            MarginAnnotation.Position position = content instanceof TextLine
                    ? classify(content, bodyLeft, bodyRight) : null;
            if (position == MarginAnnotation.Position.LEFT) {
                leftLines.add((TextLine) content);
            } else if (position == MarginAnnotation.Position.RIGHT) {
                rightLines.add((TextLine) content);
            } else {
                remaining.add(content);
            }
        }
        if (leftLines.isEmpty() && rightLines.isEmpty()) {
            return new Extraction(pageContents, new ArrayList<>());
        }

        List<IObject> annotations = new ArrayList<>();
        for (SemanticParagraph paragraph : groupIntoParagraphs(leftLines)) {
            annotations.add(new MarginAnnotation(paragraph, MarginAnnotation.Position.LEFT));
        }
        for (SemanticParagraph paragraph : groupIntoParagraphs(rightLines)) {
            annotations.add(new MarginAnnotation(paragraph, MarginAnnotation.Position.RIGHT));
        }
        return new Extraction(remaining, annotations);
    }

    /**
     * Merges same-column lines into one paragraph wherever they read top-to-bottom
     * (allowing for pageContents not already being sorted that way - a left-margin
     * and a right-margin run can interleave in reading order without either being
     * internally out of order) with a small enough gap between them to be the same
     * callout rather than two separate ones. A run of footnote/endnote markers
     * (page 51: "6", "7", "8" ... each aligned with the *start* of a different body
     * paragraph, dozens of points apart) has gaps far too large to merge; a real
     * multi-line callout's own lines sit at ordinary single-line-spacing.
     */
    private static List<SemanticParagraph> groupIntoParagraphs(List<TextLine> lines) {
        List<TextLine> sorted = new ArrayList<>(lines);
        sorted.sort((a, b) -> Double.compare(b.getTopY(), a.getTopY()));

        List<SemanticParagraph> paragraphs = new ArrayList<>();
        SemanticParagraph current = null;
        TextLine previous = null;
        for (TextLine line : sorted) {
            boolean sameParagraphAsPrevious = previous != null
                    && previous.getBottomY() - line.getTopY() <= LINE_GROUPING_MAX_GAP_RATIO * previous.getHeight();
            if (!sameParagraphAsPrevious) {
                current = new SemanticParagraph();
                paragraphs.add(current);
            }
            current.add(line);
            previous = line;
        }
        return paragraphs;
    }

    private static List<IObject> bodyCandidates(List<IObject> pageContents, Class<? extends IObject> candidateType) {
        List<IObject> bodyCandidates = new ArrayList<>();
        for (IObject content : pageContents) {
            if (candidateType.isInstance(content) && content.getWidth() > BODY_CANDIDATE_MIN_WIDTH) {
                bodyCandidates.add(content);
            }
        }
        // No reliable body column to compare against on this page (e.g. an
        // image-only page, or a page with too little text) - leave everything as
        // is rather than risk misclassifying against a meaningless baseline.
        return bodyCandidates;
    }

    private static MarginAnnotation.Position classify(IObject candidate, double bodyLeft, double bodyRight) {
        double left = candidate.getLeftX();
        double right = candidate.getRightX();
        double width = candidate.getWidth();
        double height = candidate.getHeight();

        if (width >= MAX_ANNOTATION_WIDTH || height >= MAX_ANNOTATION_HEIGHT) {
            return null;
        }
        boolean isRightMargin = left > bodyRight - COLUMN_EDGE_TOLERANCE;
        boolean isLeftMargin = !isRightMargin && right < bodyLeft + COLUMN_EDGE_TOLERANCE;
        if (!isRightMargin && !isLeftMargin) {
            return null;
        }
        if (height > ROTATED_CAPTION_ASPECT_RATIO * width && width >= ROTATED_CAPTION_MIN_WIDTH) {
            // Rotated image credit, not a callout - leave it where it was found.
            return null;
        }
        return isRightMargin ? MarginAnnotation.Position.RIGHT : MarginAnnotation.Position.LEFT;
    }

    private static double median(List<IObject> nodes, boolean leftEdge) {
        double[] values = new double[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            values[i] = leftEdge ? nodes.get(i).getLeftX() : nodes.get(i).getRightX();
        }
        java.util.Arrays.sort(values);
        return values[values.length / 2];
    }
}
