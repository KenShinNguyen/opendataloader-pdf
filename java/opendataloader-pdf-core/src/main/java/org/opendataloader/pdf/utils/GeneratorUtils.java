package org.opendataloader.pdf.utils;

import org.opendataloader.pdf.html.HtmlGenerator;
import org.opendataloader.pdf.markdown.MarkdownGenerator;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextBlock;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
 * assumption that whichever one ends the line is always a word split by the wrap. That
 * assumption is not decidable from the character alone - see {@link #appendLineJoin} for
 * how a hyphen-minus is actually resolved. {@link #getTextFromLineForPlainText} and
 * {@link #appendLineJoin} fix all of this for every {@link OutputType}, JSON's
 * {@link OutputType#JSON} included, which is what makes this the one canonical builder.
 */
public class GeneratorUtils {

    /**
     * A hyphen-minus ending a line reads identically whether it is a word split by the
     * wrap ("congru-" + "ence" -> "congruence") or a compound word's own hyphen that
     * happens to land at the wrap point ("well-" + "intentioned") - the two are the same
     * character in the same position, and neither reading is evidence over the other.
     * {@link #appendLineJoin} resolves this by dictionary lookup instead of a fixed rule:
     * see {@link #joinsIntoADictionaryWord}.
     */
    private static final char HYPHEN_MINUS = '-';
    /** Exists specifically to mark a discretionary break; elided at a line break. */
    private static final char SOFT_HYPHEN = '­';
    /** Punctuation, not a broken word - kept, and set flush against the text on both sides. */
    private static final char EM_DASH = '—';
    /** The ASCII apostrophe, as used in a contraction or possessive. */
    private static final char APOSTROPHE = '\'';
    /** The typographic apostrophe most PDF text actually uses in place of {@link #APOSTROPHE}. */
    private static final char RIGHT_SINGLE_QUOTATION_MARK = '’';

    /** Resource path of the wordlist {@link #joinsIntoADictionaryWord} looks up against. */
    private static final String ENGLISH_WORDS_RESOURCE = "english-words.txt";

    /**
     * Lazily-loaded, lowercase-only English wordlist (SCOWL, via the Debian {@code
     * wamerican} package - see THIRD_PARTY_NOTICES.md), used only to settle whether a
     * hyphen-minus ending a line is a wrap-hyphenated word. Loaded once per process; a
     * missing or unreadable resource degrades to an empty set rather than failing
     * document processing, so a hyphen-minus is then always kept, same as before this
     * dictionary check existed.
     */
    private static final class EnglishWords {
        private static final Set<String> WORDS = load();

        private EnglishWords() {
        }

        private static Set<String> load() {
            try (InputStream stream = GeneratorUtils.class.getResourceAsStream(ENGLISH_WORDS_RESOURCE)) {
                if (stream == null) {
                    return Set.of();
                }
                Set<String> words = new HashSet<>(90000);
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isEmpty()) {
                            words.add(line);
                        }
                    }
                }
                return words;
            } catch (IOException | UncheckedIOException exception) {
                return Set.of();
            }
        }
    }

    public static String getTextFromTextNode(SemanticTextNode textNode, OutputType outputType) {
        StringBuilder stringBuilder = new StringBuilder();
        for (TextColumn column : textNode.getColumns()) {
            List<TextBlock> blocks = column.getBlocks();
            for (int i = 0; i < blocks.size(); i++) {
                String segment = getTextFromLines(blocks.get(i).getLines(), outputType);
                if (i == 0) {
                    stringBuilder.append(segment);
                } else {
                    appendLineJoin(stringBuilder, segment);
                    stringBuilder.append(segment);
                }
            }
        }
        return LineJoinRepair.repairSplitUrls(stringBuilder.toString());
    }

    public static String getTextFromLines(List<TextLine> textLines, OutputType outputType) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < textLines.size(); i++) {
            StringBuilder segment = new StringBuilder();
            appendLineForOutputType(textLines.get(i), outputType, segment);
            if (i == 0) {
                stringBuilder.append(segment);
            } else {
                appendLineJoin(stringBuilder, segment);
                stringBuilder.append(segment);
            }
        }
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
     * "well-intentioned". No word boundary needs a space right after one. Unlike at a line
     * break, a hyphen sitting at a same-line chunk boundary is not ambiguous the way
     * {@link #appendLineJoin} has to resolve - nothing about a font or color change looks
     * like a word wrap, so it is always a compound word's own hyphen here, kept the same
     * way every time.
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
     * <p>Only a soft hyphen actually says that on its own - it exists specifically to mark
     * a discretionary break, so eliding it is correct regardless of what the two halves
     * spell. A hyphen-minus does not say that: a compound word's own hyphen
     * ("well-intentioned", "so-called") and a genuinely wrap-hyphenated word
     * ("congru-" + "ence" -> "congruence") read identically at the character level, and
     * real documents produce both - a ragged-right ebook essentially never wrap-hyphenates,
     * while a justified academic text hyphenates constantly, and no rule that looks only at
     * the hyphen and its position can tell the two apart. {@link #joinsIntoADictionaryWord}
     * settles it the way a reader would: by whether the two halves spell a real word once
     * joined. An em dash and an apostrophe ending the accumulated text are punctuation and
     * a mid-word mark, never a line-wrapped word either way, so they are kept unconditionally
     * the same way a soft hyphen is elided unconditionally - flush against the text on both
     * sides, nothing added or removed, for the same reason {@link #needsChunkSeparator}
     * keeps an apostrophe flush at a chunk boundary.
     *
     * @param next what {@code stringBuilder} is about to have appended to it - read only to
     *             settle a trailing hyphen-minus, never modified
     */
    private static void appendLineJoin(StringBuilder stringBuilder, CharSequence next) {
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
        } else if (last == HYPHEN_MINUS) {
            if (joinsIntoADictionaryWord(stringBuilder, next)) {
                stringBuilder.deleteCharAt(stringBuilder.length() - 1);
            }
        } else if (last != EM_DASH && last != APOSTROPHE && last != RIGHT_SINGLE_QUOTATION_MARK) {
            stringBuilder.append(' ');
        }
    }

    /**
     * Whether the letters immediately before the hyphen-minus ending {@code stringBuilder}
     * and the letters immediately starting {@code next}, joined together, spell a real
     * English word - the signal {@link #appendLineJoin} uses to tell a wrap-hyphenated word
     * from a compound word's own hyphen, since the two are not distinguishable any other
     * way at this point in the text. An empty half on either side (the hyphen is not
     * actually between two words - a number range, a bare bullet) never counts as a match,
     * so it falls back to keeping the hyphen, same as a half that fails the lookup.
     */
    private static boolean joinsIntoADictionaryWord(StringBuilder stringBuilder, CharSequence next) {
        String left = trailingLetters(stringBuilder, stringBuilder.length() - 1);
        if (left.isEmpty()) {
            return false;
        }
        String right = leadingLetters(next);
        if (right.isEmpty()) {
            return false;
        }
        return EnglishWords.WORDS.contains(left + right);
    }

    /** The run of letters in {@code text} immediately before index {@code beforeIndex}, lowercased. */
    private static String trailingLetters(CharSequence text, int beforeIndex) {
        int start = beforeIndex;
        while (start > 0 && Character.isLetter(text.charAt(start - 1))) {
            start--;
        }
        return text.subSequence(start, beforeIndex).toString().toLowerCase(Locale.ROOT);
    }

    /** The run of letters starting {@code text}, lowercased. */
    private static String leadingLetters(CharSequence text) {
        int end = 0;
        while (end < text.length() && Character.isLetter(text.charAt(end))) {
            end++;
        }
        return text.subSequence(0, end).toString().toLowerCase(Locale.ROOT);
    }
}
