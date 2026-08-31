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

import java.util.ArrayList;
import java.util.List;

/**
 * Pulls short, narrow paragraphs whose bounding box sits entirely outside a
 * page's dominant body-text column - a margin callout ("1st point of
 * refutation..."), or the tiny leader-line digit that connects one to its
 * highlighted body passage - out of the normal reading flow, before
 * {@link ListProcessor} and {@link HeadingProcessor} get a chance to treat
 * them as ordinary list items or paragraphs.
 *
 * <p>Without this, a margin callout gets spliced into the body paragraph/list
 * flow wherever its bounding box happens to fall in reading order (mid
 * sentence, in the worst case), and a lone connector digit surfaces as an
 * orphan single-character list item ("1", "2.") with no content of its own.
 *
 * <p>Detection is geometric only: a candidate must be narrower than a real
 * second body column could plausibly be ({@link #MAX_ANNOTATION_WIDTH}), so a
 * genuine two-column layout is never mistaken for margin content (both of its
 * columns are well over that width). A rotated image credit printed sideways
 * along an image edge is excluded separately by its tall/narrow aspect ratio.
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
     * A rotated image credit/caption (printed sideways along an image's edge) is
     * much taller than it is wide; a real margin callout is the opposite - wide
     * and short. {@link #ROTATED_CAPTION_MIN_WIDTH} keeps this from also catching
     * a bare leader-line digit, which is narrow enough to look "tall" too.
     */
    private static final double ROTATED_CAPTION_ASPECT_RATIO = 3.0;
    private static final double ROTATED_CAPTION_MIN_WIDTH = 8.0;

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
        List<SemanticParagraph> bodyCandidates = new ArrayList<>();
        for (IObject content : pageContents) {
            if (content instanceof SemanticParagraph && content.getWidth() > BODY_CANDIDATE_MIN_WIDTH) {
                bodyCandidates.add((SemanticParagraph) content);
            }
        }
        if (bodyCandidates.isEmpty()) {
            // No reliable body column to compare against on this page (e.g. an
            // image-only page, or a page with too little text) - leave everything
            // as is rather than risk misclassifying against a meaningless baseline.
            return new Extraction(pageContents, new ArrayList<>());
        }
        double bodyLeft = median(bodyCandidates, true);
        double bodyRight = median(bodyCandidates, false);

        List<IObject> remaining = new ArrayList<>(pageContents.size());
        List<IObject> annotations = new ArrayList<>();
        for (IObject content : pageContents) {
            MarginAnnotation.Position position = (content instanceof SemanticParagraph)
                    ? classify((SemanticParagraph) content, bodyLeft, bodyRight) : null;
            if (position == null) {
                remaining.add(content);
            } else {
                annotations.add(new MarginAnnotation((SemanticParagraph) content, position));
            }
        }
        return new Extraction(remaining, annotations);
    }

    private static MarginAnnotation.Position classify(SemanticParagraph paragraph, double bodyLeft, double bodyRight) {
        double left = paragraph.getLeftX();
        double right = paragraph.getRightX();
        double width = paragraph.getWidth();
        double height = paragraph.getHeight();

        boolean isRightMargin = left > bodyRight - COLUMN_EDGE_TOLERANCE && width < MAX_ANNOTATION_WIDTH;
        boolean isLeftMargin = !isRightMargin && right < bodyLeft + COLUMN_EDGE_TOLERANCE && width < MAX_ANNOTATION_WIDTH;
        if (!isRightMargin && !isLeftMargin) {
            return null;
        }
        if (height > ROTATED_CAPTION_ASPECT_RATIO * width && width >= ROTATED_CAPTION_MIN_WIDTH) {
            // Rotated image credit, not a callout - leave it where it was found.
            return null;
        }
        return isRightMargin ? MarginAnnotation.Position.RIGHT : MarginAnnotation.Position.LEFT;
    }

    private static double median(List<SemanticParagraph> nodes, boolean leftEdge) {
        double[] values = new double[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            values[i] = leftEdge ? nodes.get(i).getLeftX() : nodes.get(i).getRightX();
        }
        java.util.Arrays.sort(values);
        return values[values.length / 2];
    }
}
