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
package org.opendataloader.pdf.markdown;

import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.entities.SemanticFormula;
import org.opendataloader.pdf.entities.EnrichedImageChunk;
import org.opendataloader.pdf.entities.SemanticPicture;
import org.opendataloader.pdf.utils.Base64ImageUtils;
import org.opendataloader.pdf.utils.GeneratorUtils;
import org.opendataloader.pdf.utils.ImagesUtils;
import org.opendataloader.pdf.utils.OutputType;
import org.verapdf.wcag.algorithms.entities.*;
import org.verapdf.wcag.algorithms.entities.content.*;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.listLabelsDetection.NumberingStyleNames;

import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownGenerator implements Closeable {

    protected static final Logger LOGGER = Logger.getLogger(MarkdownGenerator.class.getCanonicalName());
    protected final java.io.Writer markdownWriter;
    protected final String markdownFileName;
    protected int tableNesting = 0;
    protected boolean isImageSupported;
    protected String markdownPageSeparator;
    /**
     * Page numbers (1-based) selected by --pages; an empty set means all pages.
     * Sourced from the raw {@link Config#getPageNumbers()} list (not the
     * validated set built by {@code DocumentProcessor.getValidPageNumbers}).
     * Safe to compare against {@code pageNumber + 1} because the surrounding
     * loop is bounded by the document's actual page count, so out-of-range
     * values from the raw list are never tested for membership.
     */
    protected final Set<Integer> selectedPageNumbers;
    protected boolean embedImages = false;
    protected String imageFormat = Config.IMAGE_FORMAT_PNG;
    protected boolean includeHeaderFooter = false;
    /**
     * Indent carried by the list currently being written, so a list nested under a list
     * item lines up under that item's text. Empty at the top level.
     */
    protected String listIndent = "";
    protected static final String strikethroughTextMD = "~~";
    /**
     * A '<' only opens a tag when a name-like character follows it, so that is
     * the only case worth escaping.
     */
    private static final Pattern TAG_OPENING = Pattern.compile("<(?=[A-Za-z!/?])");
    /**
     * Longest run of digits still reused as a Markdown ordered-list marker. A longer
     * "number" is not numbering, and CommonMark caps ordered-list markers at 9 digits.
     */
    private static final int MAX_LIST_NUMBER_DIGITS = 9;

    MarkdownGenerator(File inputPdf, Config config) throws IOException {
        String cutPdfFileName = inputPdf.getName();
        this.markdownFileName = config.getOutputFolder() + File.separator + cutPdfFileName.substring(0, cutPdfFileName.length() - 3) + "md";
        this.markdownWriter = new FileWriter(markdownFileName, StandardCharsets.UTF_8);
        this.isImageSupported = !config.isImageOutputOff() && config.isGenerateMarkdown();
        this.markdownPageSeparator = config.getMarkdownPageSeparator();
        this.selectedPageNumbers = new HashSet<>(config.getPageNumbers());
        this.embedImages = config.isEmbedImages();
        this.imageFormat = config.getImageFormat();
        this.includeHeaderFooter = config.isIncludeHeaderFooter();
    }

    /**
     * Creates a MarkdownGenerator that writes to an arbitrary Writer (e.g., stdout).
     */
    public MarkdownGenerator(java.io.Writer writer, Config config) {
        this.markdownFileName = null;
        this.markdownWriter = writer;
        this.isImageSupported = false;
        this.markdownPageSeparator = config.getMarkdownPageSeparator();
        this.selectedPageNumbers = new HashSet<>(config.getPageNumbers());
        this.embedImages = false;
        this.imageFormat = config.getImageFormat();
        this.includeHeaderFooter = config.isIncludeHeaderFooter();
    }

    public void writeToMarkdown(List<List<IObject>> contents) {
        try {
            for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
                if (selectedPageNumbers.isEmpty() || selectedPageNumbers.contains(pageNumber + 1)) {
                    writePageSeparator(pageNumber);
                }
                for (IObject content : contents.get(pageNumber)) {
                    if (!isSupportedContent(content)) {
                        continue;
                    }
                    this.write(content);
                    writeContentsSeparator();
                }
            }

            LOGGER.log(Level.INFO, "Created {0}", markdownFileName);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unable to create markdown output: " + e.getMessage());
        }
    }

    protected void writePageSeparator(int pageNumber) throws IOException {
        if (!markdownPageSeparator.isEmpty()) {
            markdownWriter.write(markdownPageSeparator.contains(Config.PAGE_NUMBER_STRING)
                ? markdownPageSeparator.replace(Config.PAGE_NUMBER_STRING, String.valueOf(pageNumber + 1))
                : markdownPageSeparator);
            writeContentsSeparator();
        }
    }

    protected boolean isSupportedContent(IObject content) {
        if (content instanceof SemanticHeaderOrFooter) {
            return includeHeaderFooter;
        }
        return content instanceof SemanticTextNode || // Heading, Paragraph etc...
            content instanceof SemanticFormula ||
            content instanceof SemanticPicture ||
            content instanceof TableBorder ||
            content instanceof PDFList ||
            content instanceof SemanticTOC ||
            (content instanceof ImageChunk && isImageSupported);
    }

    protected void writeContentsSeparator() throws IOException {
        writeLineBreak();
        writeLineBreak();
    }

    protected void write(IObject object) throws IOException {
        if (object instanceof SemanticHeaderOrFooter) {
            writeHeaderOrFooter((SemanticHeaderOrFooter) object);
        } else if (object instanceof SemanticPicture) {
            writePicture((SemanticPicture) object);
        } else if (object instanceof ImageChunk) {
            writeImage((ImageChunk) object);
        } else if (object instanceof SemanticFormula) {
            writeFormula((SemanticFormula) object);
        } else if (object instanceof SemanticHeading) {
            writeHeading((SemanticHeading) object);
        } else if (object instanceof SemanticParagraph) {
            writeParagraph((SemanticParagraph) object);
        } else if (object instanceof SemanticTextNode) {
            writeSemanticTextNode((SemanticTextNode) object);
        } else if (object instanceof TableBorder) {
            writeTable((TableBorder) object);
        } else if (object instanceof PDFList) {
            writeList((PDFList) object);
        } else if (object instanceof SemanticTOC) {
            writeTOC((SemanticTOC) object);
        }
    }

    /**
     * Wraps an image relative path as a CommonMark angle-bracket link destination
     * (`<...>`). The bare form `(my paper.png)` is terminated by the first space or
     * unbalanced parenthesis, so paths inheriting filenames with spaces, parens, or
     * brackets break in renderers (#405). The angle-bracket form is the
     * spec-recommended way to embed such paths and lets the on-disk path stay
     * byte-identical to the rendered link, which preserves user intent for both the
     * default `<stem>_images/` directory and any `--image-dir` value.
     *
     * Only `<`, `>`, and `\` are reserved inside the angle-bracket form; escape
     * those with a backslash. Newlines have no representable form in a link
     * destination — replace them with spaces so the destination stays well-formed.
     */
    static String formatMarkdownLinkDestination(String path) {
        if (path == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(path.length() + 2);
        sb.append('<');
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '<' || c == '>' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c == '\n' || c == '\r') {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        sb.append('>');
        return sb.toString();
    }

    protected void writeImage(ImageChunk image) {
        try {
            String absolutePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectory(), File.separator, image.getIndex(), imageFormat);
            String relativePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectoryName(), "/", image.getIndex(), imageFormat);

            if (ImagesUtils.isImageFileExists(absolutePath)) {
                String imageSource;
                if (embedImages) {
                    File imageFile = new File(absolutePath);
                    imageSource = Base64ImageUtils.toDataUri(imageFile, imageFormat);
                    if (imageSource == null) {
                        LOGGER.log(Level.WARNING, "Failed to convert image to Base64: {0}", absolutePath);
                    }
                } else {
                    imageSource = formatMarkdownLinkDestination(relativePath);
                }
                if (imageSource != null) {
                    // No "image N" fallback: PDF/UA forbids false alternatives,
                    // and an empty Markdown alt lets screen readers skip the
                    // image as a decorative element rather than reading a
                    // meaningless synthetic label.
                    String altText = (image instanceof EnrichedImageChunk && ((EnrichedImageChunk) image).hasDescription())
                            ? ((EnrichedImageChunk) image).sanitizeDescription()
                            : "";
                    String imageString = String.format(MarkdownSyntax.IMAGE_FORMAT, getCorrectMarkdownString(altText), imageSource);
                    markdownWriter.write(imageString);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to write image for markdown output: " + e.getMessage());
        }
    }

    /**
     * Writes a SemanticPicture with its description as alt text.
     *
     * @param picture The picture to write
     */
    protected void writePicture(SemanticPicture picture) {
        try {
            String absolutePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectory(), File.separator, picture.getPictureIndex(), imageFormat);
            String relativePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectoryName(), "/", picture.getPictureIndex(), imageFormat);

            if (ImagesUtils.isImageFileExists(absolutePath)) {
                String imageSource;
                if (embedImages) {
                    File imageFile = new File(absolutePath);
                    imageSource = Base64ImageUtils.toDataUri(imageFile, imageFormat);
                    if (imageSource == null) {
                        LOGGER.log(Level.WARNING, "Failed to convert image to Base64: {0}", absolutePath);
                    }
                } else {
                    imageSource = formatMarkdownLinkDestination(relativePath);
                }
                if (imageSource != null) {
                    String altText = picture.hasDescription()
                            ? picture.sanitizeDescription()
                            : "";
                    String imageString = String.format(MarkdownSyntax.IMAGE_FORMAT, getCorrectMarkdownString(altText), imageSource);
                    markdownWriter.write(imageString);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to write picture for markdown output: " + e.getMessage());
        }
    }

    /**
     * Writes a formula in LaTeX format wrapped in $$ delimiters.
     *
     * @param formula The formula to write
     */
    protected void writeFormula(SemanticFormula formula) throws IOException {
        markdownWriter.write(MarkdownSyntax.MATH_BLOCK_START);
        markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        markdownWriter.write(formula.getLatex());
        markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        markdownWriter.write(MarkdownSyntax.MATH_BLOCK_END);
    }

    protected void writeHeaderOrFooter(SemanticHeaderOrFooter headerOrFooter) throws IOException {
        for (IObject content : headerOrFooter.getContents()) {
            if (isSupportedContent(content)) {
                write(content);
                writeContentsSeparator();
            }
        }
    }

    protected void writeList(PDFList list) throws IOException {
        String parentIndent = listIndent;
        for (ListItem item : list.getListItems()) {
            String itemText = GeneratorUtils.getTextFromLines(item.getLines(), OutputType.MD);
            String marker = "";
            if (!isInsideTable()) {
                marker = listItemMarker(list.getNumberingStyle(), itemText, item.getLabelLength());
                if (markerReplacesLabel(list.getNumberingStyle(), marker)) {
                    itemText = stripListLabel(itemText, item.getLabelLength());
                }
                markdownWriter.write(parentIndent);
                markdownWriter.write(marker);
            }
            markdownWriter.write(getCorrectMarkdownString(itemText));
            writeLineBreak();

            List<IObject> itemContents = item.getContents();
            if (!itemContents.isEmpty()) {
                writeLineBreak();
                // Content of a nested list has to start at or past the column where this
                // item's own text starts, or Markdown reads it as a sibling of this item
                // instead of a child of it.
                listIndent = parentIndent + repeatSpace(marker.length());
                writeContents(itemContents, false);
                listIndent = parentIndent;
            }
        }
        listIndent = parentIndent;
    }

    /**
     * The Markdown marker to open a list item with.
     *
     * <p>Every list used to open with "-", which left an ordered list carrying two
     * markers: the bullet, and the label still sitting in the item's own text
     * ("- 1. Desire to belong"). Markdown can express decimal numbering natively, so a
     * decimal list reuses the document's own label as the marker - which also keeps the
     * numbering of a list split across a page break running on from where it left off,
     * rather than restarting at 1.
     *
     * <p>Roman numerals and letters have no Markdown equivalent. Rendering them as
     * decimals would renumber the document, so those keep the bullet and leave the label
     * in the text where it can still be read.
     */
    static String listItemMarker(String numberingStyle, String itemText, int labelLength) {
        String bullet = MarkdownSyntax.LIST_ITEM + MarkdownSyntax.SPACE;
        if (!NumberingStyleNames.ARABIC_NUMBERS.equals(numberingStyle)) {
            return bullet;
        }
        String label = readLabel(itemText, labelLength);
        if (label == null) {
            return bullet;
        }
        char last = label.charAt(label.length() - 1);
        if (last != '.' && last != ')') {
            label = label + '.';
        }
        return label + MarkdownSyntax.SPACE;
    }

    /**
     * Whether the marker already stands in for the item's label, so the label has to
     * come off the item's text: a bullet replaces it, and a reused decimal label would
     * otherwise be written twice.
     */
    static boolean markerReplacesLabel(String numberingStyle, String marker) {
        return NumberingStyleNames.UNORDERED.equals(numberingStyle)
            || !marker.startsWith(MarkdownSyntax.LIST_ITEM);
    }

    /**
     * The item's numbering label, or null when it is absent or is not a plain number
     * followed by at most one delimiter - anything else would not survive being reused
     * as a Markdown marker.
     */
    private static String readLabel(String itemText, int labelLength) {
        if (labelLength <= 0 || labelLength > itemText.length()) {
            return null;
        }
        String label = itemText.substring(0, labelLength).trim();
        if (label.isEmpty()) {
            return null;
        }
        int digits = 0;
        while (digits < label.length() && Character.isDigit(label.charAt(digits))) {
            digits++;
        }
        if (digits == 0 || digits > MAX_LIST_NUMBER_DIGITS || label.length() > digits + 1) {
            return null;
        }
        if (label.length() == digits + 1) {
            char delimiter = label.charAt(digits);
            if (delimiter != '.' && delimiter != ')') {
                return null;
            }
        }
        return label;
    }

    /**
     * Drops the item's numbering label from its text.
     *
     * <p>Called only when the marker already stands in for the label: a bullet, which
     * replaces it, or a reused decimal label, which would otherwise be written twice.
     * A roman or lettered label is left in the text, because nothing in the marker
     * carries it.
     */
    static String stripListLabel(String itemText, int labelLength) {
        if (labelLength <= 0 || labelLength > itemText.length()) {
            return itemText;
        }
        int start = labelLength;
        while (start < itemText.length() && itemText.charAt(start) == ' ') {
            start++;
        }
        return itemText.substring(start);
    }

    private static String repeatSpace(int length) {
        StringBuilder spaces = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            spaces.append(' ');
        }
        return spaces.toString();
    }

    protected void writeTOC(SemanticTOC toc) throws IOException {
        for (IObject item : toc.getTOCItems()) {
            if (item instanceof SemanticTOC) {
                writeTOC((SemanticTOC)item);
            } else if (item instanceof SemanticTOCI) {
                SemanticTOCI tocItem = (SemanticTOCI)item;
                markdownWriter.write(getCorrectMarkdownString(GeneratorUtils.getTextFromLines(tocItem.getLines(), OutputType.MD)));
                writeLineBreak();

                List<IObject> itemContents = tocItem.getContents();
                if (!itemContents.isEmpty()) {
                    writeLineBreak();
                    writeContents(itemContents, false);
                }
                writeLineBreak();
            }
        }
    }

    protected void writeSemanticTextNode(SemanticTextNode textNode) throws IOException {
        String value = GeneratorUtils.getTextFromTextNode(textNode, OutputType.MD);
        if (StaticContainers.isKeepLineBreaks()) {
            if (textNode instanceof SemanticHeading) {
                value = value.replace(MarkdownSyntax.LINE_BREAK, MarkdownSyntax.SPACE);
            } else if (isInsideTable()) {
                value = value.replace(MarkdownSyntax.LINE_BREAK, getLineBreak());
            }
        } else if (isInsideTable()) {
            // Always replace line breaks with space in table cells for proper markdown table formatting
            value = value.replace(MarkdownSyntax.LINE_BREAK, MarkdownSyntax.SPACE);
        }

        markdownWriter.write(getCorrectMarkdownString(value));
    }



    protected void writeTable(TableBorder table) throws IOException {
        enterTable();
        for (int rowNumber = 0; rowNumber < table.getNumberOfRows(); rowNumber++) {
            TableBorderRow row = table.getRow(rowNumber);
            markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
            for (int colNumber = 0; colNumber < table.getNumberOfColumns(); colNumber++) {
                TableBorderCell cell = row.getCell(colNumber);
                if (cell.getRowNumber() == rowNumber && cell.getColNumber() == colNumber) {
                    List<IObject> cellContents = cell.getContents();
                    writeContents(cellContents, true);
                } else {
                    writeSpace();
                }
                markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
            }
            markdownWriter.write(MarkdownSyntax.LINE_BREAK);
            //Due to markdown syntax we have to separate column headers
            if (rowNumber == 0) {
                markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
                for (int i = 0; i < table.getNumberOfColumns(); i++) {
                    markdownWriter.write(MarkdownSyntax.TABLE_HEADER_SEPARATOR);
                    markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
                }
                markdownWriter.write(MarkdownSyntax.LINE_BREAK);
            }
        }
        leaveTable();
    }

    protected void writeContents(List<IObject> contents, boolean isTable) throws IOException {
        boolean wroteAnyContent = false;
        for (int i = 0; i < contents.size(); i++) {
            IObject content = contents.get(i);
            if (!isSupportedContent(content)) {
                continue;
            }
            this.write(content);
            boolean isLastContent = i == contents.size() - 1;
            if (!isTable || !isLastContent) {
                writeContentsSeparator();
            }
            wroteAnyContent = true;
        }
        if (!wroteAnyContent && isTable) {
            writeSpace();
        }
    }

    protected void writeParagraph(SemanticParagraph textNode) throws IOException {
        writeSemanticTextNode(textNode);
    }

    protected void writeHeading(SemanticHeading heading) throws IOException {
        if (!isInsideTable()) {
            // Cap heading level to 1-6 per Markdown specification
            int headingLevel = Math.min(6, Math.max(1, heading.getHeadingLevel()));
            for (int i = 0; i < headingLevel; i++) {
                markdownWriter.write(MarkdownSyntax.HEADING_LEVEL);
            }
            markdownWriter.write(MarkdownSyntax.SPACE);
        }
        writeSemanticTextNode(heading);
    }

    protected void enterTable() {
        tableNesting++;
    }

    protected void leaveTable() {
        if (tableNesting > 0) {
            tableNesting--;
        }
    }

    protected boolean isInsideTable() {
        return tableNesting > 0;
    }

    protected String getLineBreak() {
        if (isInsideTable()) {
            return MarkdownSyntax.HTML_LINE_BREAK_TAG;
        } else {
            return MarkdownSyntax.LINE_BREAK;
        }
    }

    protected void writeLineBreak() throws IOException {
        markdownWriter.write(getLineBreak());
    }

    protected void writeSpace() throws IOException {
        markdownWriter.write(MarkdownSyntax.SPACE);
    }

    /**
     * Escapes only what Markdown actually reserves.
     *
     * <p>This used to convert '&amp;', '&lt;' and '&gt;' to HTML entities. Markdown gives
     * none of them that meaning: '&amp;' is an ordinary character, and '&gt;' only starts
     * a blockquote at the beginning of a line. Escaping them all corrupted
     * ordinary text for the consumers this output mainly serves — the ones that
     * read the Markdown as text rather than rendering it to HTML — so "R&amp;D"
     * arrived as "R&amp;amp;D". It also double-escaped any entity the PDF already
     * contained, turning "&amp;amp;" into "&amp;amp;amp;".
     *
     * <p>A '&lt;' that could open a tag is still escaped, because raw HTML in
     * Markdown reaches a renderer intact. A backslash is used rather than an
     * entity: CommonMark renders it as a literal '&lt;' and it leaves the text
     * readable.
     */
    protected String getCorrectMarkdownString(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replace("\u0000", "");
        return TAG_OPENING.matcher(sanitized).replaceAll(Matcher.quoteReplacement("\\<"));
    }

    public static void getTextFromLineForMarkdown(TextLine line, StringBuilder stringBuilder) {
        for (TextChunk chunk : line.getTextChunks()) {
            if (chunk.getIsStrikethroughText()) {
                stringBuilder.append(strikethroughTextMD);
            }
            stringBuilder.append(chunk.getValue());
            if (chunk.getIsStrikethroughText()) {
                stringBuilder.append(strikethroughTextMD);
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (markdownWriter != null) {
            markdownWriter.close();
        }
    }
}
