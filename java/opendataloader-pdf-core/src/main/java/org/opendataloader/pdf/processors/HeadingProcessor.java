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
import org.opendataloader.pdf.utils.BulletedParagraphUtils;
import org.opendataloader.pdf.utils.GeneratorUtils;
import org.opendataloader.pdf.utils.HeadingHeuristics;
import org.opendataloader.pdf.utils.TextNodeStatistics;
import org.opendataloader.pdf.utils.TextNodeUtils;
import org.verapdf.wcag.algorithms.entities.INode;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.TextBlock;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.text.TextStyle;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;

import java.util.*;

/**
 * Processor for detecting and classifying headings in PDF content.
 * Uses font size, weight, and position to identify potential headings.
 */
public class HeadingProcessor {
    private static final double HEADING_PROBABILITY = 0.75;
    private static final double BULLETED_HEADING_PROBABILITY = 0.1;

    /**
     * Font size and weight are floating point, and a heading set one notch larger than
     * the body is still clearly larger, so the comparison against the dominant style
     * only needs to absorb rounding noise.
     */
    private static final double FONT_SIZE_TOLERANCE = 0.05;
    private static final double FONT_WEIGHT_TOLERANCE = 1.0;

    /**
     * Fewest font-size samples a page needs before its dominant size is trusted for the
     * "styled like body text" comparison. Below this, the page's own statistics are as
     * likely to be a handful of footnotes as they are the body, so the comparison is
     * skipped and the sentence-shape check alone decides.
     */
    private static final long MIN_RELIABLE_FONT_SIZE_SAMPLES = 5;

    /**
     * Processes content to identify and mark headings.
     *
     * @param contents the list of content objects to process
     * @param isTableCell whether the content is inside a table cell
     */
    public static void processHeadings(List<IObject> contents, boolean isTableCell) {
        TextNodeStatistics textNodeStatistics = new TextNodeStatistics();
        List<SemanticTextNode> textNodes = new LinkedList<>();
        Map<SemanticTextNode, PDFList> textNodeToListMap = new HashMap<>();
        for (IObject content : contents) {
            processContent(textNodes, content, textNodeStatistics, textNodeToListMap);
        }

        int textNodesCount = textNodes.size();
        if (isTableCell && textNodesCount < 2) {
            return;
        }
        for (int index = 0; index < textNodesCount; index++) {
            SemanticTextNode textNode = textNodes.get(index);
            if (textNode.getSemanticType() == SemanticType.HEADING) {
                continue;
            }
            SemanticTextNode prevNode = index != 0 ? textNodes.get(index - 1) : null;
            SemanticTextNode nextNode = index + 1 < textNodesCount ? textNodes.get(index + 1) : null;
            double probability = NodeUtils.headingProbability(textNode, prevNode, nextNode, textNode);

            probability += textNodeStatistics.fontSizeRarityBoost(textNode);
            probability += textNodeStatistics.fontWeightRarityBoost(textNode);

            if (BulletedParagraphUtils.isBulletedParagraph(textNode)) {
                probability += BULLETED_HEADING_PROBABILITY;
            }
            if (probability > HEADING_PROBABILITY && textNode.getSemanticType() != SemanticType.LIST
                && !isBodyText(textNode, textNodeStatistics) && !isDropCap(textNode, nextNode)) {
                textNode.setSemanticType(SemanticType.HEADING);
            }
            if (textNode.getSemanticType() == SemanticType.HEADING && textNode.getInitialSemanticType() == SemanticType.LIST) {
                PDFList list = textNodeToListMap.get(textNode);
                if (isNotHeadings(list)) {
                    continue;
                }
                int listIndex = contents.indexOf(list);
                contents.remove(listIndex);
                contents.addAll(listIndex, disassemblePDFList(list));
            }
        }
        setHeadings(contents);
    }

    /**
     * Whether a node scored as a heading is really a paragraph.
     *
     * <p>The veto needs both halves to agree. Text styled no larger and no bolder than
     * the body is not visually set apart on the page, and text shaped like a sentence is
     * not a label; either on its own is common in a real heading - a run-in heading is
     * body sized, and a heading can end in a full stop - but together they describe body
     * text that the score happened to like. A heading that is genuinely larger or bolder
     * than the body is never touched, whatever its wording.
     */
    static boolean isBodyText(SemanticTextNode textNode, TextNodeStatistics statistics) {
        if (statistics.getFontSizeSampleCount() < MIN_RELIABLE_FONT_SIZE_SAMPLES) {
            // Too few samples on this page to know what "styled like body text" even
            // means here; let the sentence-shape check decide on its own instead of
            // comparing against a size that is likely just a few footnotes.
            return HeadingHeuristics.looksLikeSentence(textNode.getValue());
        }
        double dominantFontSize = statistics.getDominantFontSize();
        if (dominantFontSize <= 0.0) {
            // Nothing to compare against: leave the score alone.
            return false;
        }
        if (textNode.getFontSize() > dominantFontSize + FONT_SIZE_TOLERANCE) {
            return false;
        }
        double dominantFontWeight = statistics.getDominantFontWeight();
        if (dominantFontWeight > 0.0 && textNode.getFontWeight() > dominantFontWeight + FONT_WEIGHT_TOLERANCE) {
            return false;
        }
        return HeadingHeuristics.looksLikeSentence(textNode.getValue());
    }

    /**
     * Whether {@code textNode} is really a drop cap - a single oversized letter opening a
     * paragraph - rather than a genuine heading the score above mistook it for.
     *
     * <p>{@link #isBodyText} cannot catch this case: it vetoes a heading score by
     * comparing font size against the page's dominant size, but a drop cap is
     * *supposed* to be dramatically larger than body text - that is the entire point of
     * one - so it reads exactly like a real heading on size and weight alone, the two
     * signals that veto relies on.
     *
     * <p>What actually tells them apart is what a drop cap is a letter *of*: it is
     * literally the paragraph's own first letter, rendered oversized and separated into
     * its own node by the layout. Most of the time the rest of that same word continues
     * in the very next node at ordinary size ("D" then "efine your terms!" - the
     * paragraph reads "Define your terms!", split across the two), and concatenating the
     * two spells a real word purely because that is what actually happened on the page; a
     * genuine heading's text does not coincidentally continue into the next paragraph's
     * opening letters this way. But a drop-capped letter can also already be a complete
     * one-letter word on its own - English has exactly two, "I" and "a" - with the next
     * node starting an unrelated word of its own ("I" then "really love Spencer's
     * Camaro...", not "I" + "really" concatenating into anything): that case is checked
     * for on its own too, since concatenation would wrongly fail it. Restricted to a
     * single-letter candidate (a real heading is essentially never reducible to one bare
     * letter) so this never second-guesses an ordinary short heading like "FAQ" or
     * "Part I".
     */
    private static boolean isDropCap(SemanticTextNode textNode, SemanticTextNode nextNode) {
        String letter = onlyLetters(textNode.getValue());
        if (letter.length() != 1) {
            return false;
        }
        if (GeneratorUtils.isEnglishWord(letter)) {
            return true;
        }
        if (nextNode == null) {
            return false;
        }
        String nextLetters = leadingLetters(nextNode.getValue());
        if (nextLetters.isEmpty()) {
            return false;
        }
        return GeneratorUtils.isEnglishWord(letter + nextLetters);
    }

    /** Every letter in {@code text}, punctuation and whitespace dropped - a drop cap's own quote marks included. */
    private static String onlyLetters(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder letters = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                letters.append(c);
            }
        }
        return letters.toString();
    }

    /** The run of letters starting {@code text}. */
    private static String leadingLetters(String text) {
        if (text == null) {
            return "";
        }
        int end = 0;
        while (end < text.length() && Character.isLetter(text.charAt(end))) {
            end++;
        }
        return text.substring(0, end);
    }

    private static List<IObject> disassemblePDFList(PDFList list) {
        List<IObject> contents = new LinkedList<>();
        for (ListItem item : list.getListItems()) {
            SemanticTextNode node = convertListItemToSemanticTextNode(item);
            node.setSemanticType(SemanticType.HEADING);
            contents.add(node);
            contents.addAll(item.getContents());
        }
        return contents;
    }

    private static SemanticTextNode convertListItemToSemanticTextNode(TextBlock textBlock) {
        SemanticTextNode semanticTextNode = new SemanticTextNode(SemanticType.LIST);
        for (TextLine line : textBlock.getLines()) {
            semanticTextNode.add(line);
        }
        return semanticTextNode;
    }

    private static List<SemanticTextNode> getTextNodesFromContents(List<IObject> contents) {
        List<SemanticTextNode> textNodes = new LinkedList<>();
        for (IObject content : contents) {
            if (content instanceof SemanticTextNode) {
                textNodes.add((SemanticTextNode) content);
            }
        }
        return textNodes;
    }

    private static void processContent(List<SemanticTextNode> textNodes, IObject content, TextNodeStatistics textNodeStatistics,
                                       Map<SemanticTextNode, PDFList> possibleHeadingsInList) {
        if (content instanceof SemanticTextNode) {
            SemanticTextNode textNode = (SemanticTextNode) content;
            if (!textNode.isSpaceNode()) {
                textNodes.add(textNode);
                textNodeStatistics.addTextNode(textNode);
            }
        } else if (content instanceof TableBorder && ((TableBorder) content).isTextBlock()) {
            TableBorder textBlock = (TableBorder) content;
            TableBorderCell cell = textBlock.getCell(0, 0);
            List<SemanticTextNode> cellTextNodes = getTextNodesFromContents(cell.getContents());
            if (cellTextNodes.size() == 1) {
                processContent(textNodes, cellTextNodes.get(0), textNodeStatistics, possibleHeadingsInList);
            }
        } else if (content instanceof PDFList) {
            PDFList list = (PDFList) content;
            ListItem listItem = list.getFirstListItem();
            SemanticTextNode textNode = convertListItemToSemanticTextNode(listItem);
            textNodes.add(textNode);
            textNodeStatistics.addTextNode(textNode);
            possibleHeadingsInList.put(textNode, list);
        }
    }

    private static boolean isNotHeadings(PDFList list) {
        for (int i = 0; i < list.getListItems().size() - 1; i++) {
            boolean onlyLineArtChunks = true;
            List<ListItem> listItems = list.getListItems();
            if (listItems.get(i).getContents().isEmpty()) {
                return true;
            }
            for (IObject item : listItems.get(i).getContents()) {
                if (!(item instanceof LineArtChunk)) {
                    onlyLineArtChunks = false;
                    break;
                }
            }
            if (onlyLineArtChunks) {
                return true;
            }
        }
        return false;
    }

    private static void setHeadings(List<IObject> contents) {
        for (int index = 0; index < contents.size(); index++) {
            IObject content = contents.get(index);
            if (content instanceof SemanticTextNode && ((INode) content).getSemanticType() == SemanticType.HEADING && !(content instanceof SemanticHeading)) {
                SemanticHeading heading = new SemanticHeading((SemanticTextNode) content);
                contents.set(index, heading);
                StaticLayoutContainers.getHeadings().add(heading);
            }
            if (content instanceof TableBorder) {
                TableBorder table = (TableBorder) content;
                if (table.isTextBlock()) {
                    List<IObject> textBlockContents = table.getCell(0, 0).getContents();
                    setHeadings(textBlockContents);
                }
            }
        }
    }

    /**
     * Detects and assigns heading levels based on text style.
     * Groups headings by text style and assigns levels from 1 upwards.
     */
    public static void detectHeadingsLevels() {
        SortedMap<TextStyle, Set<SemanticHeading>> map = new TreeMap<>();
        List<SemanticHeading> headings = StaticLayoutContainers.getHeadings();
        List<SemanticHeading> colorlessHeadings = new ArrayList<>();
        for (SemanticHeading heading : headings) {
            if (TextNodeUtils.getTextColorOrNull(heading) == null) {
                colorlessHeadings.add(heading);
                continue;
            }
            TextStyle textStyle = TextStyle.getTextStyle(heading);
            map.computeIfAbsent(textStyle, k -> new HashSet<>()).add(heading);
        }
        int level = 1;
        TextStyle previousTextStyle = null;
        for (Map.Entry<TextStyle, Set<SemanticHeading>> entry : map.entrySet()) {
            if (previousTextStyle != null && previousTextStyle.compareTo(entry.getKey()) != 0) {
                level++;
            }
            previousTextStyle = entry.getKey();
            for (SemanticHeading heading : entry.getValue()) {
                heading.setHeadingLevel(level);
            }
        }
        // Headings without color info get level based on font size relative to existing levels
        for (SemanticHeading heading : colorlessHeadings) {
            heading.setHeadingLevel(findClosestLevel(heading, map));
        }
    }

    private static int findClosestLevel(SemanticHeading heading, SortedMap<TextStyle, Set<SemanticHeading>> map) {
        if (map.isEmpty()) {
            return 1;
        }
        double fontSize = heading.getFontSize();
        int bestLevel = 1;
        double bestDiff = Double.MAX_VALUE;
        int level = 1;
        TextStyle previousStyle = null;
        for (Map.Entry<TextStyle, Set<SemanticHeading>> entry : map.entrySet()) {
            if (previousStyle != null && previousStyle.compareTo(entry.getKey()) != 0) {
                level++;
            }
            previousStyle = entry.getKey();
            SemanticHeading representative = entry.getValue().iterator().next();
            double diff = Math.abs(representative.getFontSize() - fontSize);
            if (diff < bestDiff) {
                bestDiff = diff;
                bestLevel = level;
            }
        }
        return bestLevel;
    }
}
