package org.opendataloader.pdf.html;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendataloader.pdf.entities.SemanticFormula;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;

class HtmlGeneratorTest {
    /**
     * Creates a TextChunk with the given style properties.
     * Assumptions about internal representation:
     * - setFontColor(double[]) expects RGB in range [0.0, 1.0].
     * - setItalicAngle(0.0) → isItalic() == false, non‑zero → true.
     * - setFontWeight(double) is stored and getRoundedFontWeight() rounds it.
     */
    private TextChunk createChunk(String text, boolean strikethrough, boolean underlined) {
        BoundingBox dummyBox = new BoundingBox(0, 0, 10, 20, 30);
        TextChunk chunk = new TextChunk(dummyBox, text, 12, 100.0);

        if (strikethrough) {
            chunk.setIsStrikethroughText();      // sets the flag to true
        }

        if (underlined) {
            chunk.setIsUnderlinedText();      // sets the flag to true
        }
        return chunk;
    }

    /**
     * Builds the expected style attribute value for a given combination.
     * The order matches getTextStyle(): strikethrough → italic → color → weight.
     */
    private String expectedStyle(boolean strikethrough, boolean underlined) {
        StringBuilder style = new StringBuilder();
        if (strikethrough && underlined) {
            style.append("text-decoration: line-through underline; ");
        } else if (strikethrough) {
            style.append("text-decoration: line-through; ");
        } else if (underlined) {
            style.append("text-decoration: underline; ");
        }
        return style.toString().trim();
    }

    static Stream<Arguments> styleCombinations() {
        List<Arguments> args = new ArrayList<>();
        boolean[] bools = { false, true };
        for (boolean s : bools) {
            for (boolean i : bools) {
                args.add(Arguments.of(s, i));
            }
        }
        return args.stream();
    }

    @ParameterizedTest(name = "strikethrough={0}, underlined={1}")
    @MethodSource("styleCombinations")
    void testAllStyleCombinations(boolean strikethrough, boolean underlined) {
        TextChunk chunk = createChunk("A", strikethrough, underlined);
        TextLine line = new TextLine(chunk);
        StringBuilder sb = new StringBuilder();

        HtmlGenerator.getTextFromLineForHTML(line, sb);

        String expected;
        if (strikethrough || underlined) {
            String styleAttr = expectedStyle(strikethrough, underlined);
            expected = "<span style=\"" + styleAttr + "\">A</span>";
        } else {
            expected = "A";
        }
        assertEquals(expected, sb.toString());
    }

    @Test
    void testEmptyLine() {
        TextLine line = new TextLine();
        StringBuilder sb = new StringBuilder();
        HtmlGenerator.getTextFromLineForHTML(line, sb);
        assertEquals("", sb.toString());
    }

    @Test
    void testPdfTextIsEscapedForHtmlBodyContext() {
        TextChunk chunk = createChunk("<script>alert(1)</script>&", false, false);
        TextLine line = new TextLine(chunk);
        StringBuilder sb = new StringBuilder();

        HtmlGenerator.getTextFromLineForHTML(line, sb);

        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;&amp;", sb.toString());
    }

    @Test
    void testStyledPdfTextIsEscapedInsideSpan() {
        TextChunk chunk = createChunk("<img src=x onerror=alert(1)>", false, true);
        TextLine line = new TextLine(chunk);
        StringBuilder sb = new StringBuilder();

        HtmlGenerator.getTextFromLineForHTML(line, sb);

        assertEquals(
            "<span style=\"text-decoration: underline;\">&lt;img src=x onerror=alert(1)&gt;</span>",
            sb.toString());
    }

    @Test
    void testHtmlTextEscapingHandlesTitleCharactersAndNull() {
        assertEquals("report &lt;draft&gt; &amp; notes", HtmlGenerator.escapeHtmlText("report <draft> & notes"));
        assertEquals("", HtmlGenerator.escapeHtmlText(null));
    }

    @Test
    void testAmpersandIsEscapedExactlyOnce() {
        TextChunk chunk = new TextChunk(new BoundingBox(0, 0, 10, 20, 30),
            "&lt; & &amp;", 12, 100.0);
        chunk.setFontWeight(400.0);
        TextLine line = new TextLine(chunk);
        StringBuilder sb = new StringBuilder();

        HtmlGenerator.getTextFromLineForHTML(line, sb);

        assertEquals("&amp;lt; &amp; &amp;amp;", sb.toString());
    }

    @Test
    void testFormulaLatexIsEscaped() {
        String formulaLatex = "x < y & z";
        String result = HtmlGenerator.escapeHtmlText(formulaLatex);
        assertEquals("x &lt; y &amp; z", result);
    }

    @Test
    void testHtmlAttributeEscapingHandlesQuotesAndNewlines() {
        assertEquals("quote&quot; and null byte",
            HtmlGenerator.escapeHtmlAttribute("quote\" and\u0000\nnull byte"));
    }

    // ----- getTextFromLineForHTML: space between chunks on the same line -----

    /**
     * A line split into two chunks - e.g. by a font or color change mid-line - used to
     * be glued together with no separator. The semantic layer's own text join
     * (SemanticTextNode.getValue(), what JSON's "content" field carries) inserts a space
     * between every chunk on a line; HTML now matches it.
     */
    @Test
    void testGetTextFromLineForHTML_insertsSpaceBetweenChunks() {
        TextLine line = new TextLine();
        line.add(createChunk("Hello", false, false));
        line.add(createChunk("world", false, false));
        StringBuilder sb = new StringBuilder();

        HtmlGenerator.getTextFromLineForHTML(line, sb);

        assertEquals("Hello world", sb.toString());
    }

    /**
     * A chunk boundary that already carries its own space must not get a second one
     * inserted next to it. Regression test for Issue #336's financial-statement row,
     * where this doubling stayed invisible under HTML's whitespace-collapsing test but
     * broke the exact-text Markdown table check that shares this same join logic.
     */
    @Test
    void testGetTextFromLineForHTML_doesNotDoubleASpaceAlreadyAtTheBoundary() {
        TextLine line = new TextLine();
        line.add(createChunk("Hello ", false, false));
        line.add(createChunk("world", false, false));
        StringBuilder sb = new StringBuilder();

        HtmlGenerator.getTextFromLineForHTML(line, sb);

        assertEquals("Hello world", sb.toString());
    }

    /**
     * The inserted space sits outside the styled chunk's "<span>", so a styled chunk
     * following a plain one reads "Hello <span ...>world</span>", not
     * "Hello<span ...> world</span>".
     */
    @Test
    void testGetTextFromLineForHTML_spaceSitsOutsideStyledSpan() {
        TextLine line = new TextLine();
        line.add(createChunk("Hello", false, false));
        line.add(createChunk("world", false, true));
        StringBuilder sb = new StringBuilder();

        HtmlGenerator.getTextFromLineForHTML(line, sb);

        assertEquals("Hello <span style=\"text-decoration: underline;\">world</span>", sb.toString());
    }
}
