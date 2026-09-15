package com.dlsc.pdfviewfx;

import com.dlsc.pdfviewfx.PDFView.AnnotatableDocument;
import com.dlsc.pdfviewfx.PDFView.Document;
import com.dlsc.pdfviewfx.PDFView.SearchableDocument;
import com.dlsc.pdfviewfx.PDFView.SelectableDocument;

import com.dlsc.pdfviewfx.impl.SelectionExtractor;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.paint.Color;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationCaret;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationHighlight;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationStrikeout;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup;
import org.apache.pdfbox.printing.PDFPageable;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.RenderDestination;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Printable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An implementation of {@link Document} for the Apache PDFBox library.
 *
 * @see PDFView#setDocument(Document)
 */
public class PDFBoxDocument implements SearchableDocument, SelectableDocument, AnnotatableDocument {

    private static final PDColor YELLOW = new PDColor(new float[]{1, 1, 0}, PDDeviceRGB.INSTANCE);
    private static final PDColor RED = new PDColor(new float[]{1, 0, 0}, PDDeviceRGB.INSTANCE);
    private static final PDColor BLUE = new PDColor(new float[]{0, 0, 1}, PDDeviceRGB.INSTANCE);

    // ponytail: the author of new annotations is the OS user, add a property to the view when applications need to set it
    private static final String AUTHOR = System.getProperty("user.name");

    // size of the caret marking an insertion, in points
    private static final float CARET_SIZE = 10;

    private final PDDocument document;
    private final byte[] contentBytes;
    private final Map<Annotation, List<PDAnnotation>> annotations = new HashMap<>();

    private int numberOfPages;
    private BitSet landscapeCache;
    private SelectionExtractor textPositionExtractor = null;

    public PDFBoxDocument(InputStream pdfInputStream) {
        try {
            contentBytes = pdfInputStream.readAllBytes();
            document = createDocument();
            initCaches();
        } catch (IOException e) {
            throw new DocumentProcessingException(e);
        }
    }

    /**
     * Loads the given file. The file is read into memory, so it is not kept open and saving the annotated document
     * to the same file is safe.
     *
     * @param file the PDF file to load
     */
    public PDFBoxDocument(File file) {
        try {
            contentBytes = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new DocumentProcessingException(e);
        }
        document = createDocument();
        initCaches();
    }

    private void initCaches() {
        numberOfPages = document.getNumberOfPages();
        landscapeCache = new BitSet(numberOfPages);
        for (int i = 0; i < numberOfPages; i++) {
            PDPage page = document.getPage(i);
            PDRectangle cropBox = page.getCropBox();
            boolean landscape = cropBox.getHeight() < cropBox.getWidth();            
            landscapeCache.set(i, landscape);
        }
    }

    private PDDocument createDocument() {
        try {
            return Loader.loadPDF(contentBytes);
        } catch (IOException e) {
            throw new DocumentProcessingException(e);
        }
    }

    @Override
    public int getNumberOfPages() {
        return numberOfPages;
    }

    @Override
    public boolean isLandscape(int pageNumber) {
        return landscapeCache.get(pageNumber);
    }

    @Override
    @Deprecated
    public Pageable getPageable() {
        return new PDFPageable(createPrintDocument());
    }

    @Override
    public ClosablePageable createPageable() {
        return new PrintPageable(createPrintDocument());
    }

    /**
     * Creates the document for a print job: a copy of the annotated document, or a fresh parse of the original
     * bytes as long as nothing has been annotated.
     */
    private synchronized PDDocument createPrintDocument() {
        if (annotations.isEmpty()) {
            return createDocument();
        }

        ByteArrayOutputStream annotated = new ByteArrayOutputStream();
        save(annotated);
        try {
            return Loader.loadPDF(annotated.toByteArray());
        } catch (IOException e) {
            throw new DocumentProcessingException(e);
        }
    }

    /**
     * A pageable that owns the document that was loaded for a single print job.
     */
    static class PrintPageable implements ClosablePageable {

        private final PDDocument printDocument;
        private final PDFPageable pageable;

        private boolean closed;

        PrintPageable(PDDocument printDocument) {
            this.printDocument = printDocument;
            this.pageable = new PDFPageable(printDocument);
        }

        PDDocument getPrintDocument() {
            return printDocument;
        }

        @Override
        public int getNumberOfPages() {
            return pageable.getNumberOfPages();
        }

        @Override
        public PageFormat getPageFormat(int pageIndex) {
            return pageable.getPageFormat(pageIndex);
        }

        @Override
        public Printable getPrintable(int pageIndex) {
            return pageable.getPrintable(pageIndex);
        }

        @Override
        public void close() {
            synchronized (this) {
                if (closed) {
                    return;
                }

                closed = true;
            }

            try {
                printDocument.close();
            } catch (IOException e) {
                throw new DocumentProcessingException(e);
            }
        }
    }

    @Override
    public synchronized BufferedImage renderPage(int pageNumber, float scale) {
        PDFRenderer renderer = new PDFRenderer(document);
        BufferedImage bufferedImage;

        try {
            bufferedImage = renderer.renderImage(pageNumber, scale, ImageType.ARGB, RenderDestination.VIEW);
        } catch (IOException e) {
            throw new DocumentProcessingException(e);
        }

        return bufferedImage;
    }

    @Override
    public List<PDFView.SearchResult> getSearchResults(String searchText) {

        List<PDFView.SearchResult> results = new ArrayList<>();

        PDFTextStripper stripper;

        stripper = new PDFTextStripper() {

            private int pageNumber = -1;

            @Override
            protected void startPage(PDPage page) {
                pageNumber++;
            }

            @Override
            protected void writeString(String text, List<TextPosition> textPositions) {
                if (StringUtils.containsIgnoreCase(text, searchText)) {
                    PDFView.SearchResult
                            result = new PDFView.SearchResult(searchText, text, pageNumber, calculateMarkerPosition(searchText, text, textPositions));
                    results.add(result);
                }
            }
        };

        try (PDDocument doc = createDocument()) {
            stripper.writeText(doc, Writer.nullWriter());
        } catch (IOException e) {
            throw new DocumentProcessingException(e);
        }

        return results;
    }

    @Override
    public synchronized void close() {
        try {
            document.close();
        } catch (IOException e) {
            throw new DocumentProcessingException(e);
        }
    }

    // The document is rendered on a background thread, so everything touching it is synchronized (PDFBox is not thread-safe).
    @Override
    public synchronized void addAnnotation(Annotation annotation) {
        PDPage page = document.getPage(annotation.pageNumber());
        AffineTransform toUserSpace = createUserSpaceTransform(page);

        List<PDAnnotation> created = switch (annotation.type()) {
            case HIGHLIGHT -> List.of(createTextMarkup(new PDAnnotationHighlight(), annotation.markers(), toUserSpace, toPDColor(annotation.color(), YELLOW), annotation.contents()));
            case STRIKE_OUT -> List.of(createTextMarkup(new PDAnnotationStrikeout(), annotation.markers(), toUserSpace, toPDColor(annotation.color(), RED), annotation.contents()));
            case INSERT -> List.of(createCaret(annotation.markers(), toUserSpace, toPDColor(annotation.color(), BLUE), annotation.contents()));
            case REPLACE -> {
                // stored the way Acrobat does it: the caret carries the new text, the strikeout is grouped with it
                PDColor color = toPDColor(annotation.color(), RED);
                PDAnnotationCaret caret = createCaret(annotation.markers(), toUserSpace, color, annotation.contents());
                caret.setIntent("Replace");
                PDAnnotationTextMarkup strikeout = createTextMarkup(new PDAnnotationStrikeout(), annotation.markers(), toUserSpace, color, null);
                strikeout.setInReplyTo(caret);
                strikeout.setReplyType("Group");
                yield List.of(caret, strikeout);
            }
            case NOTE -> List.of(createNote(annotation.markers(), toUserSpace, toPDColor(annotation.color(), YELLOW), annotation.contents()));
        };

        try {
            page.getAnnotations().addAll(created);
        } catch (IOException e) {
            throw new DocumentProcessingException(e);
        }

        // viewers other than PDFBox need the appearance streams when the document gets saved
        created.forEach(pdAnnotation -> pdAnnotation.constructAppearances(document));

        annotations.computeIfAbsent(annotation, key -> new ArrayList<>()).addAll(created);
    }

    @Override
    public synchronized void removeAnnotation(Annotation annotation) {
        List<PDAnnotation> created = annotations.remove(annotation);
        if (created == null) {
            return;
        }

        try {
            document.getPage(annotation.pageNumber()).getAnnotations().removeAll(created);
        } catch (IOException e) {
            throw new DocumentProcessingException(e);
        }
    }

    @Override
    public synchronized void save(OutputStream out) {
        try {
            document.save(out);
        } catch (IOException e) {
            throw new DocumentProcessingException(e);
        }
    }

    private static PDAnnotationTextMarkup createTextMarkup(PDAnnotationTextMarkup markup, List<Rectangle2D> markers, AffineTransform toUserSpace, PDColor color, String contents) {
        float[] quadPoints = toUserSpace(markers, toUserSpace);
        markup.setQuadPoints(quadPoints);
        markup.setRectangle(bounds(quadPoints));
        return configure(markup, color, contents);
    }

    private static PDColor toPDColor(Color color, PDColor defaultColor) {
        if (color == null) {
            return defaultColor;
        }
        return new PDColor(new float[]{(float) color.getRed(), (float) color.getGreen(), (float) color.getBlue()}, PDDeviceRGB.INSTANCE);
    }

    private static PDAnnotationCaret createCaret(List<Rectangle2D> markers, AffineTransform toUserSpace, PDColor color, String contents) {
        // the caret sits on the bottom edge of the marked text, centered on its end
        Rectangle2D end = markers.getLast();
        Rectangle2D marker = new Rectangle2D(end.getMaxX() - CARET_SIZE / 2, end.getMaxY() - CARET_SIZE, CARET_SIZE, CARET_SIZE);

        PDAnnotationCaret caret = new PDAnnotationCaret();
        caret.setRectangle(bounds(toUserSpace(List.of(marker), toUserSpace)));
        return configure(caret, color, contents);
    }

    private static PDAnnotationText createNote(List<Rectangle2D> markers, AffineTransform toUserSpace, PDColor color, String contents) {
        PDAnnotationText note = new PDAnnotationText();
        note.setName(PDAnnotationText.NAME_COMMENT);
        note.setRectangle(bounds(toUserSpace(markers, toUserSpace)));
        return configure(note, color, contents);
    }

    private static <T extends PDAnnotationMarkup> T configure(T markup, PDColor color, String contents) {
        markup.setColor(color);
        if (contents != null) {
            markup.setContents(contents);
        }
        markup.setTitlePopup(AUTHOR);
        Calendar now = Calendar.getInstance();
        markup.setCreationDate(now);
        markup.setModifiedDate(now);
        return markup;
    }

    /**
     * Transforms the corners of the given rectangles to PDF user space. The result holds eight values per
     * rectangle in the order that text markup annotations expect for their quad points: upper left, upper right,
     * lower left, lower right.
     */
    private static float[] toUserSpace(List<Rectangle2D> markers, AffineTransform toUserSpace) {
        float[] points = new float[markers.size() * 8];
        int i = 0;
        for (Rectangle2D marker : markers) {
            points[i++] = (float) marker.getMinX();
            points[i++] = (float) marker.getMinY();
            points[i++] = (float) marker.getMaxX();
            points[i++] = (float) marker.getMinY();
            points[i++] = (float) marker.getMinX();
            points[i++] = (float) marker.getMaxY();
            points[i++] = (float) marker.getMaxX();
            points[i++] = (float) marker.getMaxY();
        }
        toUserSpace.transform(points, 0, points, 0, markers.size() * 4);
        return points;
    }

    private static PDRectangle bounds(float[] points) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < points.length; i += 2) {
            minX = Math.min(minX, points[i]);
            maxX = Math.max(maxX, points[i]);
            minY = Math.min(minY, points[i + 1]);
            maxY = Math.max(maxY, points[i + 1]);
        }
        return new PDRectangle(minX, minY, maxX - minX, maxY - minY);
    }

    /**
     * Creates the transform from the coordinate space of the rendered page, which is the space of
     * {@link Selection#getMarker()} (points, origin in the upper left corner), to PDF user space. It is the
     * inverse of the transform that the PDFBox renderer applies for the crop box and the rotation of the page.
     */
    private static AffineTransform createUserSpaceTransform(PDPage page) {
        PDRectangle cropBox = page.getCropBox();
        int rotation = page.getRotation();

        AffineTransform transform = new AffineTransform();
        transform.translate(cropBox.getLowerLeftX(), cropBox.getLowerLeftY());
        transform.scale(1, -1);
        transform.translate(0, -cropBox.getHeight());
        transform.rotate(Math.toRadians(-rotation));
        switch (rotation) {
            case 90 -> transform.translate(-cropBox.getHeight(), 0);
            case 180 -> transform.translate(-cropBox.getWidth(), -cropBox.getHeight());
            case 270 -> transform.translate(0, -cropBox.getWidth());
            default -> {
            }
        }
        return transform;
    }

    private Rectangle2D calculateMarkerPosition(String searchText, String snippetText, List<TextPosition> textPositions) {
        int textPositionStartIndex = calculateTextPositionStartIndex(searchText, snippetText, textPositions);

        float x1 = Float.MAX_VALUE;
        float x2 = 0;
        float y1 = Float.MAX_VALUE;
        float y2 = 0;

        for (int textPositionIndex = textPositionStartIndex; textPositionIndex < textPositionStartIndex + searchText.length(); textPositionIndex++) {
            TextPosition position = textPositions.get(textPositionIndex);

            x1 = Math.min(x1, position.getXDirAdj());
            x2 = Math.max(x2, position.getXDirAdj() + position.getWidth());
            y1 = Math.min(y1, position.getYDirAdj() - position.getHeight());
            y2 = Math.max(y2, position.getYDirAdj());
        }

        x1 -= 2;
        x2 += 2;
        y1 -= 2;
        y2 += 2;

        return new Rectangle2D(x1, y1, x2 - x1, y2 - y1);
    }

    /**
     * Note that the number of textPositions might not be equal to the length of the snippetText.
     * So we need to account for that.
     * <p>
     * See: org.apache.pdfbox.text.PDFTextStripper.WordWithTextPositions
     */
    private int calculateTextPositionStartIndex(String searchText, String snippetText, List<TextPosition> textPositions) {

        int snippetTextStartIndex = snippetText.toLowerCase().indexOf(searchText.toLowerCase());

        int startIndexDecreaseDelta = 0;

        // If any TextPosition (up to the snippetTextStartIndex) contains more then one character, we have to account for that.
        for (int i = 0; i < snippetTextStartIndex; i++) {
            int numberOfCharactersInTextPosition = textPositions.get(i).getUnicode().length();
            if (numberOfCharactersInTextPosition > 1) {
                startIndexDecreaseDelta = startIndexDecreaseDelta + (numberOfCharactersInTextPosition - 1);
            }
        }

        return snippetTextStartIndex - startIndexDecreaseDelta;
    }

    // This method is synchronized to avoid multiple selection service calls at the same time
    @Override
    public synchronized Selection getSelection(int pageNumber, Point2D start, Point2D end, Selection.Mode mode) {
        if (textPositionExtractor == null || textPositionExtractor.getPageNumber() != pageNumber) {
            textPositionExtractor = new SelectionExtractor(pageNumber);
            try (PDDocument doc = createDocument()) { // TODO :: recreating document is expensive 
                textPositionExtractor.writeText(doc, Writer.nullWriter());
            } catch (IOException e) { }
        }
        return textPositionExtractor.getSelection(pageNumber, start, end, mode);
    }
}
