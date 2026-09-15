package com.dlsc.pdfviewfx.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.dlsc.pdfviewfx.Selection;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;

/**
 * SelectionExtractor allows to get selections for a given page of the pdf file. The lines of the page are kept in
 * reading order, so that a selection in one of two columns does not spill over into the other column.
 */
public class SelectionExtractor extends PDFTextStripper {

    // the share of the lines of a page that have to share a gap for it to count as the gap between two columns
    private static final double COLUMN_GAP_SHARE = 0.3;

    private final int pageNumber;
    private final List<TextLine> rawLines = new ArrayList<>(64);
    private TextLine currentLine;
    private List<TextLine> lines;

    public SelectionExtractor(int pageNumber) {
        setStartPage(pageNumber + 1);
        setEndPage(pageNumber + 1);
        setSortByPosition(true);
        this.pageNumber = pageNumber;
    }

    @Override
    protected void writeString(String text, List<TextPosition> positions) {
        for (TextPosition textPosition : positions) {
            if (currentLine == null) {
                currentLine = new TextLine(textPosition);
                rawLines.add(currentLine);
            } else {
                TextLine oldLine = currentLine;
                currentLine = currentLine.add(textPosition);
                if (currentLine != oldLine) {
                    rawLines.add(currentLine);
                }
            }
        }
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public Selection getSelection(int pageNumber, Point2D start, Point2D end, Selection.Mode mode) {
        if (lines == null) {
            lines = createReadingOrder();
        }

        if (start.getY() > end.getY()) {
            Point2D tmp = end;
            end = start;
            start = tmp;
        }

        TextLine startLine = lineAt(start, true);
        TextLine endLine = lineAt(end, false);
        if (startLine == null || endLine == null) {
            return null;
        }

        int startIndex = lines.indexOf(startLine);
        int endIndex = lines.indexOf(endLine);
        if (startIndex > endIndex) {
            if (startLine.column == endLine.column) {
                // both points lie in the gap between two lines
                return null;
            }

            // the upper point lies in the right column, so the selection starts at the lower point
            TextLine tmpLine = startLine;
            startLine = endLine;
            endLine = tmpLine;
            Point2D tmp = start;
            start = end;
            end = tmp;
            int tmpIndex = startIndex;
            startIndex = endIndex;
            endIndex = tmpIndex;
        }

        List<Rectangle2D> selectionRectangles = new ArrayList<>();
        StringBuilder selectionText = new StringBuilder();
        if (startLine == endLine) {
            startLine.collectSelection(start.getX(), end.getX(), mode, selectionRectangles, selectionText);
        } else {
            startLine.collectSelection(start.getX(), Double.MAX_VALUE, mode, selectionRectangles, selectionText);
            for (int index = startIndex + 1; index <= endIndex; index++) {
                TextLine line = lines.get(index);
                selectionText.append(line.isOnSameLineAs(lines.get(index - 1)) ? " " : "\n");
                line.collectSelection(Double.MIN_VALUE, index == endIndex ? end.getX() : Double.MAX_VALUE, mode, selectionRectangles, selectionText);
            }
        }

        return selectionText.isEmpty() ? null : new Selection(pageNumber, selectionRectangles, selectionText.toString());
    }

    /**
     * Returns the line closest to the given point. If no line covers the height of the point, the search is limited
     * to the lines below it (for the start of a selection) or above it (for the end).
     */
    private TextLine lineAt(Point2D point, boolean below) {
        return lines.stream()
                .filter(line -> line.containsHeight(point.getY()) || (below ? line.getTop() > point.getY() : line.getBottom() < point.getY()))
                .min(Comparator.comparingDouble(line -> line.distanceTo(point)))
                .orElse(null);
    }

    /**
     * Sorts the lines the way they are read. The x position that lies inside a gap of a large share of the lines
     * is the middle of the gap between two columns: the lines left of it come first, then the lines right of it.
     * Lines crossing the gap are split into their two parts, unless they have no gap there (a title, a caption):
     * those keep their place above, between or below the columns.
     */
    private List<TextLine> createReadingOrder() {
        double boundary = 0;
        int boundaryLines = 0;
        double boundaryWidth = Double.MAX_VALUE;
        for (TextLine line : rawLines) {
            for (TextLine.Gap candidate : line.getGaps()) {
                int count = (int) rawLines.stream().filter(other -> other.getGapAt(candidate.center()) != null).count();
                if (count > boundaryLines || (count == boundaryLines && candidate.width() < boundaryWidth)) {
                    boundary = candidate.center();
                    boundaryLines = count;
                    boundaryWidth = candidate.width();
                }
            }
        }

        if (boundaryLines < Math.max(3, COLUMN_GAP_SHARE * rawLines.size())) {
            return rawLines;
        }

        double columnsTop = Double.MAX_VALUE;
        double columnsBottom = 0;

        List<TextLine> segments = new ArrayList<>();
        for (TextLine line : rawLines) {
            TextLine.Gap gap = line.getGapAt(boundary);
            if (gap == null) {
                segments.add(line);
            } else {
                segments.addAll(line.split(gap));
                columnsTop = Math.min(columnsTop, line.getTop());
                columnsBottom = Math.max(columnsBottom, line.getBottom());
            }
        }

        for (TextLine segment : segments) {
            boolean crossing = segment.getLeft() < boundary && segment.getRight() > boundary;
            segment.region = !crossing ? 1 : segment.getBottom() < columnsTop ? 0 : segment.getTop() > columnsBottom ? 2 : 1;
            segment.column = segment.getLeft() > boundary ? 1 : 0;
        }

        segments.sort(Comparator.comparingInt((TextLine line) -> line.region)
                .thenComparingInt(line -> line.column)
                .thenComparingDouble(TextLine::getTop)
                .thenComparingDouble(TextLine::getLeft));
        return segments;
    }
}
