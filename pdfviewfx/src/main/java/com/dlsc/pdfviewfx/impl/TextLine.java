package com.dlsc.pdfviewfx.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.dlsc.pdfviewfx.Selection;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.text.TextPosition;

import javafx.geometry.Rectangle2D;

/** TextLine represents one line of text in a pdf file. */
class TextLine {
    private List<TextPosition> textPositions = new ArrayList<TextPosition>(64);
    private double top = Double.MAX_VALUE;
    private double bottom = 0;

    TextLine(TextPosition textPosition) {
        addPosition(textPosition);
    }

    /** Add textPosition or create new line.
     * @param textPosition The text position to add to this line
     * @return this line, if the given text position fit into this line or a new TextLine object
     */
    TextLine add(TextPosition textPosition) {
        TextLine result = this;
        if (isOnThisLine(textPosition)) {
            addPosition(textPosition);
        } else {
            result = new TextLine(textPosition);
        }
        return result;
    }
    
    boolean containsHeight(double y) {
        return top <= y && y <= bottom;
    }

    double getBottom() {
        return bottom;
    }

    double getTop() {
        return top;
    }

    void collectSelection(double startx, double endx, Selection.Mode mode, List<Rectangle2D> selectionRectangles, StringBuilder selectionText) {
        if (startx > endx) {
            double tmp = endx;
            endx = startx;
            startx = tmp;
        }
        int startIndex = getStartIndex(startx, mode);
        int endIndex = getEndIndex(endx, mode);
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            for (int idx = startIndex; idx <= endIndex; idx++) {
                selectionText.append(textPositions.get(idx).getUnicode());
            }
            TextPosition start = textPositions.get(startIndex);
            TextPosition end = textPositions.get(endIndex);
            selectionRectangles.add(new Rectangle2D(start.getX(), top, end.getEndX() - start.getX(), bottom - top));
        }
    }

    private int getStartIndex(double startx, Selection.Mode mode) {
        int startIndex = -1;
        boolean lastWasBlank = true;
        int lastWordStartIdx = -1;

        int idx = 0;
        while (idx < textPositions.size() && startIndex == -1) {
            TextPosition textPosition = textPositions.get(idx);
            double middle = textPosition.getX() + textPosition.getWidth() / 2;
            if (startx <= middle) {
                startIndex = idx;
            }

            if (lastWasBlank) {
                lastWordStartIdx = idx;
            }
            lastWasBlank = textPosition.getUnicode().isBlank();

            idx++;
        }

        return switch (mode) {
            case CHARACTER -> startIndex;
            case WORD -> lastWordStartIdx;
            case LINE -> 0;
        };
    }

    private int getEndIndex(double endx, Selection.Mode mode) {
        int endIndex = -1;
        boolean lastWasBlank = true;
        int lastWordEndIdx = -1;

        int idx = textPositions.size() - 1;
        while (idx >= 0 && endIndex == -1) {
            TextPosition textPosition = textPositions.get(idx);
            double middle = textPosition.getX() + textPosition.getWidth() / 2;
            if (middle <= endx) {
                endIndex = idx;
            }

            if (lastWasBlank) {
                lastWordEndIdx = idx;
            }
            lastWasBlank = textPosition.getUnicode().isBlank();

            idx--;
        }

        return switch (mode) {
            case CHARACTER -> endIndex;
            case WORD -> lastWordEndIdx;
            case LINE -> textPositions.size() - 1;
        };
    }

    private void addPosition(TextPosition textPosition) {
        PDFont font = textPosition.getFont();
        float fontSize = textPosition.getFontSizeInPt();
        PDFontDescriptor fontDescriptor = font.getFontDescriptor();
        float descenderHeight = Math.abs((fontDescriptor.getDescent() / 1000.0f) * fontSize);
        float ascenderHeight = Math.abs((fontDescriptor.getAscent() / 1000.0f) * fontSize);

        top = Math.min(top, textPosition.getYDirAdj() - ascenderHeight);
        bottom = Math.max(bottom, textPosition.getYDirAdj() + descenderHeight);
        textPositions.add(textPosition);
    }
    
    private boolean isOnThisLine(TextPosition textPosition) {
        TextPosition lastTextPosition = textPositions.getLast();
        float tolerance = lastTextPosition.getHeight() / 2; 
        return Math.abs(lastTextPosition.getYDirAdj() - textPosition.getYDirAdj()) < tolerance;  
    }

    @Override
    public String toString() {
        return "TextLine [top: " + top + ", bottom: " + bottom + ", text: " +
            textPositions.stream().map(TextPosition::getUnicode).collect(Collectors.joining());
    }
}
