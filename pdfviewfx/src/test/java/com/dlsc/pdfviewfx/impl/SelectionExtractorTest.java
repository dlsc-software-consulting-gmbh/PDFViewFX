package com.dlsc.pdfviewfx.impl;

import com.dlsc.pdfviewfx.Selection;
import com.dlsc.pdfviewfx.Selection.Mode;
import javafx.geometry.Point2D;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SelectionExtractorTest {

    private static final float LINE_HEIGHT = 14;
    private static final float FIRST_BASELINE = 120;

    private static final List<String> LEFT = List.of("left one", "left two", "left three", "left four", "left five", "left six");
    private static final List<String> RIGHT = List.of("right one", "right two", "right three", "right four", "right five", "right six");

    private interface ContentWriter {
        void write(PDPageContentStream content) throws IOException;
    }

    private static SelectionExtractor extract(ContentWriter writer) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                writer.write(content);
            }
            document.save(out);
        }

        SelectionExtractor extractor = new SelectionExtractor(0);
        try (PDDocument document = Loader.loadPDF(out.toByteArray())) {
            extractor.writeText(document, Writer.nullWriter());
        }
        return extractor;
    }

    /**
     * Writes a line of text, the baseline measured from the top of the page like the selection coordinates.
     */
    private static void writeLine(PDPageContentStream content, float x, float baseline, float fontSize, String text) throws IOException {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), fontSize);
        content.newLineAtOffset(x, PDRectangle.LETTER.getHeight() - baseline);
        content.showText(text);
        content.endText();
    }

    /**
     * A title spanning both columns, followed by two columns of six lines each.
     */
    private static SelectionExtractor extractTwoColumnPage() throws IOException {
        return extract(content -> {
            writeLine(content, 72, 60, 20, "A title that spans both columns");
            for (int i = 0; i < LEFT.size(); i++) {
                writeLine(content, 72, FIRST_BASELINE + i * LINE_HEIGHT, 10, LEFT.get(i));
                writeLine(content, 320, FIRST_BASELINE + i * LINE_HEIGHT, 10, RIGHT.get(i));
            }
        });
    }

    /**
     * A point within the text of the given line, counted from zero.
     */
    private static Point2D at(double x, int line) {
        return new Point2D(x, FIRST_BASELINE + line * LINE_HEIGHT - 3);
    }

    private static String select(SelectionExtractor extractor, Point2D start, Point2D end) {
        Selection selection = extractor.getSelection(0, start, end, Mode.LINE);
        return selection == null ? null : selection.getSelectedText();
    }

    @Test
    public void shouldKeepSelectionWithinLeftColumn() throws IOException {
        assertEquals("left two\nleft three\nleft four", select(extractTwoColumnPage(), at(100, 1), at(100, 3)));
    }

    @Test
    public void shouldKeepSelectionWithinRightColumn() throws IOException {
        assertEquals("right two\nright three\nright four", select(extractTwoColumnPage(), at(350, 1), at(350, 3)));
    }

    @Test
    public void shouldSelectAcrossColumnsInReadingOrder() throws IOException {
        // dragged from the right column upwards into the left column
        assertEquals("left five\nleft six\nright one\nright two", select(extractTwoColumnPage(), at(350, 1), at(100, 4)));
    }

    @Test
    public void shouldNotSplitLinesSpanningBothColumns() throws IOException {
        assertEquals("A title that spans both columns", select(extractTwoColumnPage(), new Point2D(80, 55), new Point2D(300, 55)));
    }

    @Test
    public void shouldNotSelectInTheGapBetweenLines() throws IOException {
        Point2D betweenLines = new Point2D(100, FIRST_BASELINE + LINE_HEIGHT + 5);
        assertNull(select(extractTwoColumnPage(), betweenLines, betweenLines));
    }

    @Test
    public void shouldIgnoreGapsOfSingleLines() throws IOException {
        SelectionExtractor extractor = extract(content -> {
            writeLine(content, 72, FIRST_BASELINE, 10, "line one");
            writeLine(content, 72, FIRST_BASELINE + LINE_HEIGHT, 10, "line two");
            writeLine(content, 72, FIRST_BASELINE + 2 * LINE_HEIGHT, 10, "left part");
            writeLine(content, 400, FIRST_BASELINE + 2 * LINE_HEIGHT, 10, "right part");
            writeLine(content, 72, FIRST_BASELINE + 3 * LINE_HEIGHT, 10, "line four");
        });

        Selection selection = extractor.getSelection(0, at(100, 1), at(100, 3), Mode.LINE);
        assertEquals("one rectangle per line, the line with the gap stays whole", 3, selection.getMarker().size());
        assertEquals("line two\nleft partright part\nline four", selection.getSelectedText());
    }
}
