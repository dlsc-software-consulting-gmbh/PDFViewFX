package com.dlsc.pdfviewfx;

import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;

public class TextPositionExtractor  extends PDFTextStripper {

    private int pageNumber = -1;
    private List<TextPosition> textPositions = new ArrayList<>(2048);
    
    public TextPositionExtractor(int pageNumber) {
        setStartPage(pageNumber+1);
        setEndPage(pageNumber+1);
        this.pageNumber = pageNumber;
    }

    @Override
    protected void writeString(String text, List<TextPosition> positions) {
        for (TextPosition textPosition : positions) {
            textPositions.add(textPosition);
//            System.out.println(
//                    "page: " + pageNumber + ": " + textPosition + 
//                    " from " + textPosition.getXDirAdj() + " / " + textPosition.getYDirAdj() + 
//                    ", width: " + textPosition.getWidth() + ", height: " + textPosition.getHeight());
        }
    }
    
    public int getPageNumber() {
        return pageNumber;
    }
    
    public List<Rectangle2D> getSelectionRectangles(Point2D start, Point2D end) {
        List<Rectangle2D> selectionRectangles = new ArrayList<>();
        Rectangle2D cropBox = new Rectangle2D(start.getX(), start.getY(), end.getX() - start.getX(), end.getY() - start.getY());
        for (TextPosition candidate : textPositions) {
            if (cropBox.contains(candidate.getXDirAdj(), candidate.getYDirAdj())) {
                selectionRectangles.add(new Rectangle2D(candidate.getXDirAdj(), candidate.getYDirAdj() - candidate.getHeight(), candidate.getWidth(), candidate.getHeight()));
            }
        }
        return selectionRectangles;
    }
}