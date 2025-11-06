package com.dlsc.pdfviewfx.impl;

import java.util.ArrayList;
import java.util.List;

import com.dlsc.pdfviewfx.Selection;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;

/**
 * SelectionExtractor allows to get selections for a given page of the pdf file.
 */
public class SelectionExtractor extends PDFTextStripper {

    private final int pageNumber;
    private final List<TextLine> lines = new ArrayList<>(64);
    private TextLine currentLine;

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
                lines.add(currentLine);
            } else {
                TextLine oldLine = currentLine;
                currentLine = currentLine.add(textPosition);
                if (currentLine != oldLine) {
                    lines.add(currentLine);
                }
            }
        }
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public Selection getSelection(int pageNumber, Point2D start, Point2D end, Selection.Mode mode) {
        if (start.getY() > end.getY()) {
            Point2D tmp = end;
            end = start;
            start = tmp;
        }
        List<Rectangle2D> selectionRectangles = new ArrayList<>();
        TextLine startLine = getFirstLineAt(start.getY());
        TextLine endLine = getLastLineAt(end.getY());
        StringBuilder selectionText = new StringBuilder();
        if (startLine != null && endLine != null) {
            if (startLine == endLine) {
                startLine.collectSelection(start.getX(), end.getX(), mode, selectionRectangles, selectionText);
            } else {
                startLine.collectSelection(start.getX(), Double.MAX_VALUE, mode, selectionRectangles, selectionText);
                int startIdx = lines.indexOf(startLine) + 1;
                int endIdx = lines.indexOf(endLine);
                for (int idx = startIdx; idx < endIdx; idx++) {
                    selectionText.append("\n");
                    TextLine line = lines.get(idx);
                    line.collectSelection(Double.MIN_VALUE, Double.MAX_VALUE, mode, selectionRectangles, selectionText);
                }
                selectionText.append("\n");
                endLine.collectSelection(Double.MIN_VALUE, end.getX(), mode, selectionRectangles, selectionText);
            }
        }

        return selectionText.isEmpty() ? null : new Selection(pageNumber, selectionRectangles, selectionText.toString());
    }

    private TextLine getFirstLineAt(double y) {
        return lines.stream()
                .filter(line -> line.getBottom() >= y)
                .findFirst()
                .orElse(null);
    }

    private TextLine getLastLineAt(double y) {
        return lines.reversed().stream()
                .filter(line -> line.getTop() <= y)
                .findFirst()
                .orElse(null);
    }

}