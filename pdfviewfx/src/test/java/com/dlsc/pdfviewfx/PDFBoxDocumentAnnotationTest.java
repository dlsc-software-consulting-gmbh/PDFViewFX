package com.dlsc.pdfviewfx;

import com.dlsc.pdfviewfx.Annotation.Type;
import javafx.geometry.Rectangle2D;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationCaret;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PDFBoxDocumentAnnotationTest {

    private static final float PAGE_HEIGHT = PDRectangle.LETTER.getHeight(); // 792

    // a line of text as the selection would report it: origin in the upper left corner of the page
    private static final Rectangle2D LINE = new Rectangle2D(100, 50, 200, 12);

    private static PDFBoxDocument createDocument(int rotation) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            page.setRotation(rotation);
            document.addPage(page);
            document.save(out);
        }

        return new PDFBoxDocument(new ByteArrayInputStream(out.toByteArray()));
    }

    /**
     * Saves and reloads the document, so that the assertions see what other viewers will see.
     */
    private static PDDocument saveAndReload(PDFBoxDocument document) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return Loader.loadPDF(out.toByteArray());
    }

    @Test
    public void shouldStoreHighlightInUserSpace() throws IOException {
        PDFBoxDocument document = createDocument(0);

        try {
            document.addAnnotation(new Annotation(Type.HIGHLIGHT, 0, List.of(LINE), "looks good"));

            try (PDDocument saved = saveAndReload(document)) {
                List<PDAnnotation> annotations = saved.getPage(0).getAnnotations();
                assertEquals(1, annotations.size());

                PDAnnotationTextMarkup highlight = (PDAnnotationTextMarkup) annotations.get(0);
                assertEquals("Highlight", highlight.getSubtype());
                assertEquals("looks good", highlight.getContents());
                assertNotNull("other viewers need the appearance stream", highlight.getNormalAppearanceStream());

                // upper left, upper right, lower left, lower right, with the origin in the lower left corner of the page
                assertArrayEquals(new float[]{100, PAGE_HEIGHT - 50, 300, PAGE_HEIGHT - 50, 100, PAGE_HEIGHT - 62, 300, PAGE_HEIGHT - 62}, highlight.getQuadPoints(), 0.001f);

                PDRectangle rectangle = highlight.getRectangle();
                assertTrue(rectangle.getLowerLeftX() <= 100 && rectangle.getUpperRightX() >= 300);
                assertTrue(rectangle.getLowerLeftY() <= PAGE_HEIGHT - 62 && rectangle.getUpperRightY() >= PAGE_HEIGHT - 50);
            }
        } finally {
            document.close();
        }
    }

    @Test
    public void shouldMapMarkersOnRotatedPages() throws IOException {
        PDFBoxDocument document = createDocument(90);

        try {
            document.addAnnotation(new Annotation(Type.STRIKE_OUT, 0, List.of(new Rectangle2D(0, 0, 10, 10)), null));

            try (PDDocument saved = saveAndReload(document)) {
                PDAnnotationTextMarkup strikeout = (PDAnnotationTextMarkup) saved.getPage(0).getAnnotations().get(0);
                assertEquals("StrikeOut", strikeout.getSubtype());

                // the upper left corner of the rotated page is the lower left corner of the unrotated page
                assertArrayEquals(new float[]{0, 0, 0, 10, 10, 0, 10, 10}, strikeout.getQuadPoints(), 0.001f);
            }
        } finally {
            document.close();
        }
    }

    @Test
    public void shouldStoreReplacementAsCaretWithGroupedStrikeout() throws IOException {
        PDFBoxDocument document = createDocument(0);

        try {
            document.addAnnotation(new Annotation(Type.REPLACE, 0, List.of(LINE), "better"));

            try (PDDocument saved = saveAndReload(document)) {
                List<PDAnnotation> annotations = saved.getPage(0).getAnnotations();
                assertEquals(2, annotations.size());

                PDAnnotationCaret caret = (PDAnnotationCaret) annotations.get(0);
                assertEquals("better", caret.getContents());
                assertEquals("Replace", caret.getIntent());
                assertTrue("the caret sits at the end of the line", caret.getRectangle().contains(300, PAGE_HEIGHT - 62 + 5));

                PDAnnotationMarkup strikeout = (PDAnnotationMarkup) annotations.get(1);
                assertEquals("StrikeOut", strikeout.getSubtype());
                assertEquals("Group", strikeout.getReplyType());
                assertEquals(caret.getCOSObject(), strikeout.getInReplyTo().getCOSObject());
            }
        } finally {
            document.close();
        }
    }

    @Test
    public void shouldPlaceNoteAtMarker() throws IOException {
        PDFBoxDocument document = createDocument(0);

        try {
            document.addAnnotation(new Annotation(Type.NOTE, 0, List.of(new Rectangle2D(90, 90, 20, 20)), "todo"));

            try (PDDocument saved = saveAndReload(document)) {
                PDAnnotationText note = (PDAnnotationText) saved.getPage(0).getAnnotations().get(0);
                assertEquals("todo", note.getContents());
                assertEquals(PDAnnotationText.NAME_COMMENT, note.getName());

                // PDFBox adjusts the rectangle to the size of the icon, but the icon has to stay within the marker
                PDRectangle rectangle = note.getRectangle();
                assertTrue(rectangle.getWidth() > 0 && rectangle.getHeight() > 0);
                assertTrue(rectangle.getLowerLeftX() >= 90 && rectangle.getUpperRightX() <= 110);
                assertTrue(rectangle.getLowerLeftY() >= PAGE_HEIGHT - 110 && rectangle.getUpperRightY() <= PAGE_HEIGHT - 90);
            }
        } finally {
            document.close();
        }
    }

    @Test
    public void shouldRemoveAnnotations() throws IOException {
        PDFBoxDocument document = createDocument(0);

        try {
            Annotation highlight = new Annotation(Type.HIGHLIGHT, 0, List.of(LINE), null);
            Annotation replacement = new Annotation(Type.REPLACE, 0, List.of(LINE), "better");
            document.addAnnotation(highlight);
            document.addAnnotation(replacement);

            document.removeAnnotation(replacement);

            // unknown annotations are ignored
            document.removeAnnotation(new Annotation(Type.NOTE, 0, List.of(LINE), "unknown"));

            try (PDDocument saved = saveAndReload(document)) {
                List<PDAnnotation> annotations = saved.getPage(0).getAnnotations();
                assertEquals(1, annotations.size());
                assertEquals("Highlight", annotations.get(0).getSubtype());
            }
        } finally {
            document.close();
        }
    }

    @Test
    public void shouldPrintAnnotations() throws IOException {
        PDFBoxDocument document = createDocument(0);

        try {
            document.addAnnotation(new Annotation(Type.NOTE, 0, List.of(new Rectangle2D(90, 90, 20, 20)), "todo"));

            PDFBoxDocument.PrintPageable pageable = (PDFBoxDocument.PrintPageable) document.createPageable();
            try {
                assertEquals(1, pageable.getPrintDocument().getPage(0).getAnnotations().size());
            } finally {
                pageable.close();
            }
        } finally {
            document.close();
        }
    }
}
