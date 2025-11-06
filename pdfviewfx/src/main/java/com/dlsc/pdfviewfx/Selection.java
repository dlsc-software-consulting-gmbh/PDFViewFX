package com.dlsc.pdfviewfx;

import java.util.List;

import javafx.geometry.Rectangle2D;

/** Selection represents a textual selection within the PDF file. */
public class Selection {
    private int pageNumber;
    private final List<Rectangle2D> marker;
    private final String selectedText;

    public enum Mode {
        CHARACTER, WORD, LINE;

        public static Mode forClickCount(int clickCount) {
            return switch (clickCount) {
                case 1 -> CHARACTER;
                case 2 -> WORD;
                case 3 -> LINE;
                default -> CHARACTER;
            };
        }
    }
    /**
     * Constructs a new selection.
     *
     * @param pageNumber  The number of the page the selection lives in
     * @param marker The list of rectangles to be highlighted (in PDF coordinates)
     */
    public Selection(int pageNumber, List<Rectangle2D> marker, String selectedText) {
        this.pageNumber = pageNumber;
        this.marker = marker;
        this.selectedText = selectedText;
    }

    public List<Rectangle2D> getMarker() {
        return marker;
    }

    public List<Rectangle2D> getScaledMarker(double scale) {
        return marker.stream()
            .map(m -> new Rectangle2D(m.getMinX() * scale, m.getMinY() * scale, m.getWidth() * scale, m.getHeight() * scale))
            .toList();
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public String getSelectedText() {
        return selectedText;
    }

    @Override
    public String toString() {
        return "[selection page: " + pageNumber + ", rects: " + marker.size() + "]";
    }
}
