package com.dlsc.pdfviewfx;

import com.dlsc.pdfviewfx.PDFView.Document;
import com.dlsc.pdfviewfx.PDFView.SearchableDocument;
import com.dlsc.pdfviewfx.PDFView.SelectableDocument;

import com.dlsc.pdfviewfx.impl.SelectionExtractor;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.printing.PDFPageable;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.RenderDestination;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.awt.image.BufferedImage;
import java.awt.print.Pageable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * An implementation of {@link Document} for the Apache PDFBox library.
 *
 * @see PDFView#setDocument(Document)
 */
public class PDFBoxDocument implements SearchableDocument, SelectableDocument {

    private final PDDocument document;

    private byte[] contentBytes;
    private File contentFile;
    private int numberOfPages;
    private BitSet landscapeCache;
    private SelectionExtractor textPositionExtractor = new SelectionExtractor(-1);

    public PDFBoxDocument(InputStream pdfInputStream) {
        try {
            contentBytes = pdfInputStream.readAllBytes();
            document = createDocument();
            initCaches();
        } catch (IOException e) {
            throw new DocumentProcessingException(e);
        }
    }

    public PDFBoxDocument(File file) {
        contentFile = file;
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
            return contentFile == null ? Loader.loadPDF(contentBytes) : Loader.loadPDF(new RandomAccessReadBufferedFile(contentFile));
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
    public Pageable getPageable() {
        return new PDFPageable(createDocument());
    }

    @Override
    public BufferedImage renderPage(int pageNumber, float scale) {
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
    public void close() {
        try {
            document.close();
        } catch (IOException e) {
            throw new DocumentProcessingException(e);
        }
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
        if (textPositionExtractor.getPageNumber() != pageNumber) {
            textPositionExtractor = new SelectionExtractor(pageNumber);
            try (PDDocument doc = createDocument()) { // TODO :: recreating document is expensive 
                textPositionExtractor.writeText(doc, Writer.nullWriter());
            } catch (IOException e) { }
        }
        return textPositionExtractor.getSelection(pageNumber, start, end, mode);
    }
}
