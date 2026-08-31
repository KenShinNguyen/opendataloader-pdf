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
package org.opendataloader.pdf.entities;

import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;

/**
 * A short callout/comment box set in a page's margin (e.g. a textbook's
 * "1st point of refutation..." annotation, connected to a highlighted body
 * passage by a leader line) or a similarly positioned scrap of non-body text
 * a page's header/footer detection did not already remove (a running-head
 * page number, a rotated image credit that survived the aspect-ratio guard,
 * etc.). {@link org.opendataloader.pdf.processors.MarginAnnotationProcessor}
 * identifies candidates purely by position - a bounding box that sits
 * entirely outside the page's dominant body-text column - so a stray folio
 * that HeaderFooterProcessor missed can end up tagged "annotation" too; that
 * is an acceptable simplification; both kinds are equally out of place
 * spliced into the body reading flow, which is the defect this exists to fix.
 *
 * <p>Kept as a {@link SemanticParagraph} subclass (mirroring
 * {@link SemanticFootnote}) rather than an unrelated type so every writer
 * that does not know about it yet - HTML, tagged PDF, text - still renders
 * it sensibly via its existing SemanticParagraph/SemanticTextNode handling.
 * JSON and Markdown are updated to recognize it explicitly so it no longer
 * interleaves into the middle of body prose.
 */
public class MarginAnnotation extends SemanticParagraph {

    /** Which margin the annotation sits in, relative to the page's body column. */
    public enum Position {
        LEFT, RIGHT;

        public String toJsonValue() {
            return name().toLowerCase();
        }
    }

    private final Position position;

    public MarginAnnotation(SemanticParagraph node, Position position) {
        super(node);
        this.position = position;
        // ANNOT is the closest existing SemanticType to "margin callout" the
        // library offers; nothing downstream currently branches on it, so this
        // is purely informational (JSON's own "type" comes from a dedicated
        // serializer, not from this enum - see AnnotationSerializer).
        this.setSemanticType(SemanticType.ANNOT);
    }

    public Position getPosition() {
        return position;
    }
}
