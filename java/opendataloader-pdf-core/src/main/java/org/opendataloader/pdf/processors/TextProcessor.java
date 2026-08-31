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

import org.opendataloader.pdf.utils.GeneratorUtils;
import org.verapdf.gf.model.factory.chunks.ChunkParser;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.ChunksMergeUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.TextChunkUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TextProcessor {

    private static final double MIN_TEXT_INTERSECTION_PERCENT = 0.5;
    private static final double MAX_TOP_DECORATION_IMAGE_EPSILON = 0.3;
    private static final double MAX_BOTTOM_DECORATION_IMAGE_EPSILON = 0.1;
    private static final double MAX_LEFT_DECORATION_IMAGE_EPSILON = 0.1;
    private static final double MAX_RIGHT_DECORATION_IMAGE_EPSILON = 1.5;
    private static final double NEIGHBORS_TEXT_CHUNKS_EPSILON = 0.1;
    private static final double TEXT_MIN_HEIGHT = 1;

    /**
     * The double-f ligatures observed to reach {@link #replaceUndefinedCharacters} as an
     * unmapped glyph in practice: a subsetted font (seen from a "Microsoft: Print To PDF"
     * file) whose {@code /ToUnicode} CMap maps the single-letter-adjacent ligatures "fi"
     * (U+FB01) and "fl" (U+FB02) correctly but never defines an entry at all - not a wrong
     * one, an absent one - for the glyph code used to render "ff", "ffi", or "ffl". Each is
     * one glyph, so one unmapped code becomes exactly one {@link
     * ChunkParser#REPLACEMENT_CHARACTER_STRING} placeholder standing in for two or three
     * letters at once ("su" + [glyph] + "cient" -> "sufficient", the "ffi" ligature).
     * Checked longest first only where it matters for a correct join; see {@link
     * #resolveLigaturePlaceholder}, which picks by dictionary match rather than order, so
     * this list is really just the closed, evidence-based set of candidates - narrow on
     * purpose, the same as {@link GeneratorUtils}' own dictionary-resolved hyphen join: add
     * a ligature here only once it is actually found dropped this way, not preemptively.
     */
    private static final String[] DROPPED_LIGATURE_CANDIDATES = {"ff", "ffi", "ffl"};

    public static void replaceUndefinedCharacters(List<IObject> contents, String replacementCharacterString) {
        for (IObject object : contents) {
            if (object instanceof TextChunk) {
                TextChunk textChunk = ((TextChunk) object);
                String value = textChunk.getValue();
                if (!value.contains(ChunkParser.REPLACEMENT_CHARACTER_STRING)) {
                    continue;
                }
                value = resolveLigaturePlaceholders(value);
                if (!ChunkParser.REPLACEMENT_CHARACTER_STRING.equals(replacementCharacterString)
                        && value.contains(ChunkParser.REPLACEMENT_CHARACTER_STRING)) {
                    value = value.replace(ChunkParser.REPLACEMENT_CHARACTER_STRING, replacementCharacterString);
                }
                textChunk.setValue(value);
            }
        }
    }

    /**
     * Recovers whichever {@link #DROPPED_LIGATURE_CANDIDATES} the context supports, for
     * every {@link ChunkParser#REPLACEMENT_CHARACTER_STRING} placeholder in {@code value}.
     * The placeholder itself carries no information about which glyph it replaced, so this
     * works the same way {@link GeneratorUtils#isEnglishWord} already resolves a
     * wrap-hyphenated line break: by whether the letters around it spell a real word once a
     * candidate is substituted in. A placeholder with no letter immediately before it is
     * never touched - no English word starts with a double-f ligature, and that shape is
     * also exactly how a genuine, already-correctly-decoded question mark reaches here
     * (nothing but a real "?" character ever produces the placeholder in the first place,
     * so the only risk this guards against is an unmapped glyph that starts a chunk). A
     * missing letter after it is fine - "staff", "stuff", and "off" all end right there.
     */
    private static String resolveLigaturePlaceholders(String value) {
        String placeholder = ChunkParser.REPLACEMENT_CHARACTER_STRING;
        int index = value.indexOf(placeholder);
        if (index < 0) {
            return value;
        }
        StringBuilder result = new StringBuilder(value.length() + DROPPED_LIGATURE_CANDIDATES[DROPPED_LIGATURE_CANDIDATES.length - 1].length());
        int copiedUpTo = 0;
        while (index >= 0) {
            result.append(value, copiedUpTo, index);
            result.append(resolveLigaturePlaceholder(value, index, placeholder.length()));
            copiedUpTo = index + placeholder.length();
            index = value.indexOf(placeholder, copiedUpTo);
        }
        result.append(value, copiedUpTo, value.length());
        return result.toString();
    }

    /**
     * The replacement for one placeholder at {@code placeholderIndex}: the first of {@link
     * #DROPPED_LIGATURE_CANDIDATES} that, substituted between the letters immediately
     * surrounding the placeholder, spells a real English word - or the placeholder itself,
     * unchanged, if none does.
     */
    private static String resolveLigaturePlaceholder(String value, int placeholderIndex, int placeholderLength) {
        String left = trailingLetters(value, placeholderIndex);
        if (left.isEmpty()) {
            return value.substring(placeholderIndex, placeholderIndex + placeholderLength);
        }
        String right = leadingLetters(value, placeholderIndex + placeholderLength);
        for (String candidate : DROPPED_LIGATURE_CANDIDATES) {
            if (GeneratorUtils.isEnglishWord(left + candidate + right)) {
                return candidate;
            }
        }
        return value.substring(placeholderIndex, placeholderIndex + placeholderLength);
    }

    /** The run of letters in {@code text} immediately before index {@code beforeIndex}. */
    private static String trailingLetters(String text, int beforeIndex) {
        int start = beforeIndex;
        while (start > 0 && Character.isLetter(text.charAt(start - 1))) {
            start--;
        }
        return text.substring(start, beforeIndex);
    }

    /** The run of letters in {@code text} starting at index {@code fromIndex}. */
    private static String leadingLetters(String text, int fromIndex) {
        int end = fromIndex;
        while (end < text.length() && Character.isLetter(text.charAt(end))) {
            end++;
        }
        return text.substring(fromIndex, end);
    }

    public static double measureReplacementCharRatio(List<IObject> contents) {
        char replacementChar = ChunkParser.REPLACEMENT_CHARACTER_STRING.charAt(0);
        int totalChars = 0;
        int replacementChars = 0;
        for (IObject object : contents) {
            if (object instanceof TextChunk) {
                String value = ((TextChunk) object).getValue();
                totalChars += value.length();
                for (int i = 0; i < value.length(); i++) {
                    if (value.charAt(i) == replacementChar) {
                        replacementChars++;
                    }
                }
            }
        }
        if (totalChars == 0) {
            return 0.0;
        }
        return (double) replacementChars / totalChars;
    }

    public static void filterTinyText(List<IObject> contents) {
        for (int i = 0; i < contents.size(); i++) {
            IObject object = contents.get(i);
            if (object instanceof TextChunk) {
                TextChunk textChunk = ((TextChunk) object);
                if (textChunk.getBoundingBox().getHeight() <= TEXT_MIN_HEIGHT) {
                    contents.set(i, null);
                }
            }
        }
    }

    public static void trimTextChunksWhiteSpaces(List<IObject> contents) {
        for (int i = 0; i < contents.size(); i++) {
            IObject object = contents.get(i);
            if (object instanceof TextChunk) {
                contents.set(i, ChunksMergeUtils.getTrimTextChunk((TextChunk) object));
            }
        }
    }

    public static void mergeCloseTextChunks(List<IObject> contents) {
        for (int i = 0; i < contents.size() - 1; i++) {
            IObject object = contents.get(i);
            IObject nextObject = contents.get(i + 1);
            if (object instanceof TextChunk && nextObject instanceof TextChunk) {
                TextChunk textChunk = (TextChunk) object;
                TextChunk nextTextChunk = (TextChunk) nextObject;
                if (TextChunkUtils.areTextChunksHaveSameStyle(textChunk, nextTextChunk) &&
                    TextChunkUtils.areTextChunksHaveSameBaseLine(textChunk, nextTextChunk) &&
                    areNeighborsTextChunks(textChunk, nextTextChunk)) {
                    contents.set(i, null);
                    contents.set(i + 1, TextChunkUtils.unionTextChunks(textChunk, nextTextChunk));
                }
            }
        }
    }

    public static void removeSameTextChunks(List<IObject> contents) {
        DocumentProcessor.setIndexesForContentsList(contents);
        List<IObject> sortedTextChunks = contents.stream().filter(c -> c instanceof TextChunk).sorted(
                Comparator.comparing(x -> ((TextChunk) x).getValue())).collect(Collectors.toList());
        TextChunk lastTextChunk = null;
        for (IObject object : sortedTextChunks) {
            if (object instanceof TextChunk) {
                TextChunk currentTextChunk = (TextChunk) object;
                if (lastTextChunk != null && areSameTextChunks(lastTextChunk, currentTextChunk)) {
                    contents.set(lastTextChunk.getIndex(), null);
                }
                lastTextChunk = currentTextChunk;
            }
        }
    }

    public static boolean areSameTextChunks(TextChunk firstTextChunk, TextChunk secondTextChunk) {
        return Objects.equals(firstTextChunk.getValue(), secondTextChunk.getValue()) &&
                NodeUtils.areCloseNumbers(firstTextChunk.getWidth(), secondTextChunk.getWidth()) &&
                NodeUtils.areCloseNumbers(firstTextChunk.getHeight(), secondTextChunk.getHeight()) &&
                firstTextChunk.getBoundingBox().getIntersectionPercent(secondTextChunk.getBoundingBox()) > MIN_TEXT_INTERSECTION_PERCENT;
    }

    public static void removeTextDecorationImages(List<IObject> contents) {
        TextChunk lastTextChunk = null;
        for (int index = 0; index < contents.size(); index++) {
            IObject object = contents.get(index);
            if (object instanceof TextChunk) {
                lastTextChunk = (TextChunk) object;
            } else if (object instanceof ImageChunk && lastTextChunk != null &&
                    isTextChunkDecorationImage((ImageChunk) object, lastTextChunk)) {
                contents.set(index, null);
            }
        }
    }

    public static boolean isTextChunkDecorationImage(ImageChunk imageChunk, TextChunk textChunk) {
        return NodeUtils.areCloseNumbers(imageChunk.getTopY(), textChunk.getTopY(), MAX_TOP_DECORATION_IMAGE_EPSILON * textChunk.getHeight()) &&
                NodeUtils.areCloseNumbers(imageChunk.getBottomY(), textChunk.getBottomY(), MAX_BOTTOM_DECORATION_IMAGE_EPSILON * textChunk.getHeight()) &&
                (NodeUtils.areCloseNumbers(imageChunk.getLeftX(), textChunk.getLeftX(), MAX_LEFT_DECORATION_IMAGE_EPSILON * textChunk.getHeight()) || imageChunk.getLeftX() > textChunk.getLeftX()) &&
                (NodeUtils.areCloseNumbers(imageChunk.getRightX(), textChunk.getRightX(), MAX_RIGHT_DECORATION_IMAGE_EPSILON * textChunk.getHeight()) || imageChunk.getRightX() < textChunk.getRightX());
    }

    private static boolean areNeighborsTextChunks(TextChunk firstTextChunk, TextChunk secondTextChunk) {
        return NodeUtils.areCloseNumbers(firstTextChunk.getTextEnd(), secondTextChunk.getTextStart(),
            NEIGHBORS_TEXT_CHUNKS_EPSILON * firstTextChunk.getBoundingBox().getHeight());
    }
}
