package org.opendataloader.pdf.utils;

import org.opendataloader.pdf.html.HtmlGenerator;
import org.opendataloader.pdf.markdown.MarkdownGenerator;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextBlock;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.List;

/**
 * Assembles the text of a semantic node once, in a single canonical join, so JSON,
 * Markdown and HTML read the same words in the same order with the same spacing - only
 * the markup wrapped around them differs per {@link OutputType}. Before this, JSON took
 * its content straight from the semantic layer's own {@code SemanticTextNode.getValue()}
 * while Markdown and HTML rebuilt the line here from its {@link TextChunk}s, and the two
 * assemblies disagreed on two points: the rebuild here dropped the space the semantic
 * layer inserts between every chunk on a line (gluing adjacent spans together whenever a
 * line held more than one, e.g. a run split by a font or color change), and both
 * assemblies leaned on the semantic layer's {@code TextChunkUtils.formatLineEnd}, which
 * elides a hyphen-minus, a soft hyphen, *and* an em dash alike at a line break - correct
 * for a genuinely hyphenated word ("congru-" + "ence" -> "congruence"), wrong for an em
 * dash, which is punctuation rather than a broken word and so was disappearing along
 * with the space around it. {@link #getTextFromLineForPlainText} and
 * {@link #appendLineJoin} fix both for every {@link OutputType}, JSON's
 * {@link OutputType#JSON} included, which is what makes this the one canonical builder.
 */
public class GeneratorUtils {

    /** A hyphen genuinely splitting a word across the line break; elided, no space left behind. */
    private static final char HYPHEN_MINUS = '-';
    /** Same as {@link #HYPHEN_MINUS} but invisible except at a break; elided the same way. */
    private static final char SOFT_HYPHEN = '\u00AD';
    /** Punctuation, not a broken word - kept, and set flush against the text on both sides. */
    private static final char EM_DASH = '\u2014';

    public static String getTextFromTextNode(SemanticTextNode textNode, OutputType outputType) {
        StringBuilder stringBuilder = new StringBuilder();
        for (TextColumn column : textNode.getColumns()) {
            List<TextBlock> blocks = column.getBlocks();
            for (int i = 0; i < blocks.size() - 1; i++) {
                TextBlock block = blocks.get(i);
                stringBuilder.append(getTextFromLines(block.getLines(), outputType));
                appendLineJoin(stringBuilder);
            }
            stringBuilder.append(getTextFromLines(blocks.get(blocks.size() - 1).getLines(), outputType));
        }
        return LineJoinRepair.repairSplitUrls(stringBuilder.toString());
    }

    public static String getTextFromLines(List<TextLine> textLines, OutputType outputType) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < textLines.size() - 1; i++) {
            TextLine line = textLines.get(i);
            appendLineForOutputType(line, outputType, stringBuilder);
            appendLineJoin(stringBuilder);
        }
        appendLineForOutputType(textLines.get(textLines.size() - 1), outputType, stringBuilder);
        return LineJoinRepair.repairSplitUrls(stringBuilder.toString());
    }

    private static void appendLineForOutputType(TextLine line, OutputType outputType, StringBuilder stringBuilder) {
        switch (outputType) {
            case MD:
                MarkdownGenerator.getTextFromLineForMarkdown(line, stringBuilder);
                break;
            case HTML:
                HtmlGenerator.getTextFromLineForHTML(line, stringBuilder);
                break;
            case JSON:
            case TXT:
                getTextFromLineForPlainText(line, stringBuilder);
                break;
            default:
                break;
        }
    }

    /**
     * The markup-free join every {@link OutputType} shares: chunk values in order,
     * separated by a single space - the same rule the semantic layer's own
     * {@code TextLine.toString()} uses to build {@code SemanticTextNode.getValue()},
     * which is what {@link OutputType#JSON} and {@link OutputType#TXT} render here.
     */
    private static void getTextFromLineForPlainText(TextLine line, StringBuilder stringBuilder) {
        boolean first = true;
        for (TextChunk chunk : line.getTextChunks()) {
            if (!first) {
                stringBuilder.append(' ');
            }
            stringBuilder.append(chunk.getValue());
            first = false;
        }
    }

    /**
     * Joins a wrapped line onto what comes next, standing in for the semantic layer's
     * {@code TextChunkUtils.formatLineEnd} to correct its one mistake: that method elides
     * a hyphen-minus, a soft hyphen, and an em dash alike, on the assumption that
     * whichever one ends the line is a word split by the wrap. True for the two hyphens;
     * an em dash is punctuation, not a broken word, so it is kept - flush against the
     * text on both sides, as em-dash typography sets it, rather than dropped along with
     * the space a break would otherwise get.
     */
    private static void appendLineJoin(StringBuilder stringBuilder) {
        if (StaticContainers.isKeepLineBreaks()) {
            stringBuilder.append('\n');
            return;
        }
        if (stringBuilder.length() == 0) {
            return;
        }
        char last = stringBuilder.charAt(stringBuilder.length() - 1);
        if (last == HYPHEN_MINUS || last == SOFT_HYPHEN) {
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        } else if (last != EM_DASH) {
            stringBuilder.append(' ');
        }
    }
}
