package com.dlsc.pdfviewfx.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.dlsc.pdfviewfx.Selection;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.text.TextPosition;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;

/** TextLine represents one line of text in a pdf file, or the part of a line that lies within one column. */
class TextLine {

    /**
     * A gap between two characters that is wider than a word space, e.g. the gap between two columns.
     *
     * @param index the index of the first character after the gap
     * @param from  where the gap starts
     * @param to    where the gap ends
     */
    record Gap(int index, double from, double to) {

        boolean contains(double x) {
            return from <= x && x <= to;
        }

        double center() {
            return (from + to) / 2;
        }

        double width() {
            return to - from;
        }
    }

    private final List<TextPosition> textPositions = new ArrayList<TextPosition>(64);
    private double top = Double.MAX_VALUE;
    private double bottom = 0;
    private double left = Double.MAX_VALUE;
    private double right = 0;
    private List<Gap> gaps;

    // the place of the line in the reading order of its page, see SelectionExtractor
    int region;
    int column;

    TextLine(TextPosition textPosition) {
        addPosition(textPosition);
    }

    TextLine(List<TextPosition> textPositions) {
        textPositions.forEach(this::addPosition);
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

    double getLeft() {
        return left;
    }

    double getRight() {
        return right;
    }

    /**
     * Whether the given line is another part of the same line of text, e.g. the part in the other column.
     */
    boolean isOnSameLineAs(TextLine other) {
        return Math.abs((top + bottom) - (other.top + other.bottom)) < bottom - top;
    }

    /**
     * The distance between the given point and the bounds of this line, zero if the point lies within.
     */
    double distanceTo(Point2D point) {
        double dx = Math.max(0, Math.max(left - point.getX(), point.getX() - right));
        double dy = Math.max(0, Math.max(top - point.getY(), point.getY() - bottom));
        return dx + dy;
    }

    /**
     * The gaps between two characters of this line that are wider than two word spaces.
     */
    List<Gap> getGaps() {
        if (gaps == null) {
            gaps = new ArrayList<>();
            for (int index = 1; index < textPositions.size(); index++) {
                TextPosition previous = textPositions.get(index - 1);
                TextPosition position = textPositions.get(index);
                if (position.getX() - previous.getEndX() > 2 * widthOfSpace(position)) {
                    gaps.add(new Gap(index, previous.getEndX(), position.getX()));
                }
            }
        }
        return gaps;
    }

    /**
     * The gap of this line that contains the given x coordinate, null if there is none.
     */
    Gap getGapAt(double x) {
        return getGaps().stream().filter(gap -> gap.contains(x)).findFirst().orElse(null);
    }

    private static double widthOfSpace(TextPosition position) {
        float width = position.getWidthOfSpace();
        return width > 0 ? width : position.getFontSizeInPt() / 4;
    }

    /**
     * Splits the line at the given gap into the part before and the part after the gap.
     */
    List<TextLine> split(Gap gap) {
        return List.of(new TextLine(textPositions.subList(0, gap.index())), new TextLine(textPositions.subList(gap.index(), textPositions.size())));
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
        left = Math.min(left, textPosition.getX());
        right = Math.max(right, textPosition.getEndX());
        textPositions.add(textPosition);
        gaps = null;
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
