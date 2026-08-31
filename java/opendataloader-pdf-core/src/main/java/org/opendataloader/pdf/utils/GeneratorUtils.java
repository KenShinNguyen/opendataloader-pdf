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
 * elides a hyphen-minus, a soft hyphen, *and* an em dash alike at a line break, on the
 * assumption that whichever one ends the line is always a word split by the wrap.
 * Auditing every hyphen-minus ending a line across a real book-length sample ("Think for
 * Yourself") turned up zero cases of that - "well-intentioned", "so-called",
 * "fact-checking", "non-smokers", "meta-analysis", "tuk-tuk" and the rest were all a
 * compound word's own hyphen, coincidentally falling at the wrap point, and eliding it
 * merged two words into one ("wellintentioned"). A soft hyphen is different: it exists
 * specifically to mark a discretionary break, so eliding it is still correct. An em dash
 * is punctuation, not a broken word, so it was never elided correctly to begin with.
 * {@link #getTextFromLineForPlainText} and {@link #appendLineJoin} fix all of this for
 * every {@link OutputType}, JSON's {@link OutputType#JSON} included, which is what makes
 * this the one canonical builder.
 */
public class GeneratorUtils {

    /**
     * A compound word's own hyphen. Never elided - see the class doc for why a hyphen
     * ending a line is not reliable evidence of a wrap-hyphenated word.
     */
    private static final char HYPHEN_MINUS = '-';
    /** Exists specifically to mark a discretionary break; elided at a line break. */
    private static final char SOFT_HYPHEN = '\u00AD';
    /** Punctuation, not a broken word - kept, and set flush against the text on both sides. */
    private static final char EM_DASH = '\u2014';
    /** The ASCII apostrophe, as used in a contraction or possessive. */
    private static final char APOSTROPHE = '\'';
    /** The typographic apostrophe most PDF text actually uses in place of {@link #APOSTROPHE}. */
    private static final char RIGHT_SINGLE_QUOTATION_MARK = '\u2019';

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
     * separated by a single space where one is missing - the same rule the semantic
     * layer's own {@code TextLine.toString()} uses to build
     * {@code SemanticTextNode.getValue()}, which is what {@link OutputType#JSON} and
     * {@link OutputType#TXT} render here.
     */
    private static void getTextFromLineForPlainText(TextLine line, StringBuilder stringBuilder) {
        boolean first = true;
        for (TextChunk chunk : line.getTextChunks()) {
            String value = chunk.getValue();
            if (!first && needsChunkSeparator(stringBuilder, value)) {
                stringBuilder.append(' ');
            }
            stringBuilder.append(value);
            first = false;
        }
    }

    /**
     * Whether a space belongs between what has been written for this line so far and the
     * next chunk's value.
     *
     * <p>A run split across two chunks on the same line - by a font or color change, most
     * often - can carry its own natural inter-word space already: a chunk's trailing
     * character, or the next chunk's leading one. Inserting another there doubled it
     * ("2)  Variazione..." for a chunk boundary that already fell right after "2) "),
     * invisible to a whitespace-collapsing comparison but not to an exact one. A space is
     * only missing, and only then added, when neither side already has one.
     *
     * <p>A hyphen-minus or em dash ending the accumulated text is the same case a step
     * further: a compound word's own hyphen can fall on a chunk boundary too ("well-" +
     * "intentioned", split by a style change right at the hyphen, not a line wrap), and
     * unlike an ordinary letter, a hyphen or dash already reads as flush against what
     * follows - inserting a space there would read "well- intentioned", not
     * "well-intentioned". No word boundary needs a space right after one, the way
     * {@link #appendLineJoin} already treats them at a line break.
     *
     * <p>An apostrophe is the same case again, on either side of the boundary instead of
     * only the trailing one: a contraction or possessive can arrive with its apostrophe as
     * its own chunk ("I" + "'" + "m", "can" + "'t", "Wright" + "'s"), and both the chunk
     * before it and the chunk after already read as flush against the apostrophe - the
     * ordinary rule alone would read either boundary as a missing word gap and produce
     * "I ' m" or "Wright ' s".
     */
    public static boolean needsChunkSeparator(StringBuilder stringBuilder, String nextValue) {
        if (stringBuilder.length() == 0 || nextValue.isEmpty()) {
            return false;
        }
        char last = stringBuilder.charAt(stringBuilder.length() - 1);
        if (last == HYPHEN_MINUS || last == SOFT_HYPHEN || last == EM_DASH
                || last == APOSTROPHE || last == RIGHT_SINGLE_QUOTATION_MARK) {
            return false;
        }
        char next = nextValue.charAt(0);
        if (next == APOSTROPHE || next == RIGHT_SINGLE_QUOTATION_MARK) {
            return false;
        }
        return !Character.isWhitespace(last) && !Character.isWhitespace(next);
    }

    /**
     * Joins a wrapped line onto what comes next, standing in for the semantic layer's
     * {@code TextChunkUtils.formatLineEnd} to correct its mistakes: that method elides a
     * hyphen-minus, a soft hyphen, and an em dash alike, on the assumption that whichever
     * one ends the line is always a word split by the wrap.
     *
     * <p>Only a soft hyphen actually says that - it exists specifically to mark a
     * discretionary break, so eliding it is correct. A hyphen-minus does not say that: a
     * compound word's own hyphen ("well-intentioned", "so-called", "fact-checking") reads
     * identically to a wrap-hyphenated one at the character level, and coincidentally
     * falling at the wrap point - which is exactly when this method runs - is not evidence
     * either way. Eliding it on that assumption merged real compound words into one
     * ("wellintentioned"); auditing every hyphen-minus ending a line across a real
     * book-length sample found this is what a line-ending hyphen-minus actually is, with
     * no counterexamples. So it is kept, the way an em dash already was: flush against the
     * text on both sides, nothing added or removed. An apostrophe ending the accumulated
     * text - a possessive or contraction broken at the apostrophe itself - is kept the
     * same way, for the same reason {@link #needsChunkSeparator} keeps it flush at a chunk
     * boundary.
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
        if (last == SOFT_HYPHEN) {
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        } else if (last != EM_DASH && last != HYPHEN_MINUS
                && last != APOSTROPHE && last != RIGHT_SINGLE_QUOTATION_MARK) {
            stringBuilder.append(' ');
        }
    }
}
