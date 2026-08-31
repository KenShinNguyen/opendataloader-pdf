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
package org.opendataloader.pdf.json.serializers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.entities.MarginAnnotation;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationSerializerTest {

    private static MarginAnnotation createAnnotation(String content, MarginAnnotation.Position position) {
        SemanticParagraph paragraph = new SemanticParagraph();
        paragraph.add(new TextLine(new TextChunk(new BoundingBox(0, 500.995, 107.27, 554.988, 127.867),
            content, "Font1", 9.0, 400, 0, 9.0, new double[]{0.0}, null, 0)));
        return new MarginAnnotation(paragraph, position);
    }

    private static ObjectMapper newObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(MarginAnnotation.class, new AnnotationSerializer(MarginAnnotation.class));
        objectMapper.registerModule(module);
        return objectMapper;
    }

    /**
     * The field the whole feature exists to add: "type": "annotation" instead of
     * "paragraph", so a margin callout is unambiguously not part of the body flow.
     */
    @Test
    void serializeWritesAnnotationType() throws JsonProcessingException {
        String json = newObjectMapper().writeValueAsString(
            createAnnotation("Attention-getting opening.", MarginAnnotation.Position.RIGHT));

        assertTrue(json.contains("\"type\":\"annotation\""));
        assertFalse(json.contains("\"type\":\"paragraph\""));
    }

    @Test
    void serializeWritesPositionField() throws JsonProcessingException {
        String rightJson = newObjectMapper().writeValueAsString(
            createAnnotation("Attention-getting opening.", MarginAnnotation.Position.RIGHT));
        String leftJson = newObjectMapper().writeValueAsString(
            createAnnotation("1st point of refutation.", MarginAnnotation.Position.LEFT));

        assertTrue(rightJson.contains("\"position\":\"right\""));
        assertTrue(leftJson.contains("\"position\":\"left\""));
    }

    /**
     * Bounding box and content still ride along untouched - the user's own point
     * that JSON already preserves position, only the "type" needed to change.
     */
    @Test
    void serializePreservesBoundingBoxAndContent() throws JsonProcessingException {
        String json = newObjectMapper().writeValueAsString(
            createAnnotation("Attention-getting opening.", MarginAnnotation.Position.RIGHT));

        assertTrue(json.contains("\"content\":\"Attention-getting opening.\""));
        assertTrue(json.contains("\"bounding box\":[500.995,107.27,554.988,127.867]"));
    }
}
