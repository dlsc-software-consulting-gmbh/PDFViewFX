package com.dlsc.pdfviewfx.impl;

import javafx.print.Collation;
import javafx.print.PageOrientation;
import javafx.print.PrintColor;
import javafx.print.PrintQuality;
import javafx.print.PrintSides;
import org.junit.Test;

import javax.print.attribute.standard.Chromaticity;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OrientationRequested;
import javax.print.attribute.standard.PageRanges;
import javax.print.attribute.standard.SheetCollate;
import javax.print.attribute.standard.Sides;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PrintSettingsMapperTest {

    @Test
    public void shouldMapCopies() {
        assertEquals(3, PrintSettingsMapper.toCopies(3).getValue());
        assertNull(PrintSettingsMapper.toCopies(0));
        assertNull(PrintSettingsMapper.toCopies(-1));
    }

    @Test
    public void shouldMapPageRangesToPageIndexes() {
        assertArrayEquals(new int[]{0, 1, 2, 6}, PrintSettingsMapper.toPageIndexes(new int[][]{{1, 3}, {7, 7}}, 10));
    }

    @Test
    public void shouldClipPageIndexesToDocument() {
        assertArrayEquals(new int[]{1, 2}, PrintSettingsMapper.toPageIndexes(new int[][]{{2, 8}}, 3));
        assertArrayEquals(new int[]{0}, PrintSettingsMapper.toPageIndexes(new int[][]{{0, 1}}, 3));
        assertArrayEquals(new int[0], PrintSettingsMapper.toPageIndexes(new int[][]{{7, 9}}, 3));
    }

    @Test
    public void shouldNotRepeatPageIndexes() {
        assertArrayEquals(new int[]{4, 5, 6, 7, 0, 1, 2, 3}, PrintSettingsMapper.toPageIndexes(new int[][]{{5, 8}, {1, 6}}, 10));
    }

    @Test
    public void shouldPrintAllPagesWithoutPageRanges() {
        assertNull(PrintSettingsMapper.toPageIndexes((int[][]) null, 5));
        assertNull(PrintSettingsMapper.toPageIndexes(new int[0][], 5));
    }

    @Test
    public void shouldNotAddPageRangesToAttributes() {
        // the page range is applied by the library itself, see JDK-8297191
        assertNull(PrintSettingsMapper.createAttributes(null).get(PageRanges.class));
    }

    @Test
    public void shouldFindMediaForA4() {
        assertEquals(MediaSizeName.ISO_A4, PrintSettingsMapper.findMedia(595.275, 841.89));
    }

    @Test
    public void shouldFindMediaForLetter() {
        assertEquals(MediaSizeName.NA_LETTER, PrintSettingsMapper.findMedia(612, 792));
    }

    @Test
    public void shouldIgnoreInvalidPaperSize() {
        assertNull(PrintSettingsMapper.findMedia(0, 100));
        assertNull(PrintSettingsMapper.findMedia(100, 0));
    }

    @Test
    public void shouldMapOrientation() {
        assertEquals(OrientationRequested.PORTRAIT, PrintSettingsMapper.toOrientationRequested(PageOrientation.PORTRAIT));
        assertEquals(OrientationRequested.LANDSCAPE, PrintSettingsMapper.toOrientationRequested(PageOrientation.LANDSCAPE));
        assertEquals(OrientationRequested.REVERSE_PORTRAIT, PrintSettingsMapper.toOrientationRequested(PageOrientation.REVERSE_PORTRAIT));
        assertEquals(OrientationRequested.REVERSE_LANDSCAPE, PrintSettingsMapper.toOrientationRequested(PageOrientation.REVERSE_LANDSCAPE));
        assertNull(PrintSettingsMapper.toOrientationRequested(null));
    }

    @Test
    public void shouldMapSides() {
        assertEquals(Sides.ONE_SIDED, PrintSettingsMapper.toSides(PrintSides.ONE_SIDED));
        assertEquals(Sides.TWO_SIDED_LONG_EDGE, PrintSettingsMapper.toSides(PrintSides.DUPLEX));
        assertEquals(Sides.TWO_SIDED_SHORT_EDGE, PrintSettingsMapper.toSides(PrintSides.TUMBLE));
        assertNull(PrintSettingsMapper.toSides(null));
    }

    @Test
    public void shouldMapCollation() {
        assertEquals(SheetCollate.COLLATED, PrintSettingsMapper.toSheetCollate(Collation.COLLATED));
        assertEquals(SheetCollate.UNCOLLATED, PrintSettingsMapper.toSheetCollate(Collation.UNCOLLATED));
        assertNull(PrintSettingsMapper.toSheetCollate(null));
    }

    @Test
    public void shouldMapPrintQuality() {
        assertEquals(javax.print.attribute.standard.PrintQuality.DRAFT, PrintSettingsMapper.toPrintQuality(PrintQuality.DRAFT));
        assertEquals(javax.print.attribute.standard.PrintQuality.DRAFT, PrintSettingsMapper.toPrintQuality(PrintQuality.LOW));
        assertEquals(javax.print.attribute.standard.PrintQuality.NORMAL, PrintSettingsMapper.toPrintQuality(PrintQuality.NORMAL));
        assertEquals(javax.print.attribute.standard.PrintQuality.HIGH, PrintSettingsMapper.toPrintQuality(PrintQuality.HIGH));
        assertNull(PrintSettingsMapper.toPrintQuality(null));
    }

    @Test
    public void shouldMapPrintColor() {
        assertEquals(Chromaticity.COLOR, PrintSettingsMapper.toChromaticity(PrintColor.COLOR));
        assertEquals(Chromaticity.MONOCHROME, PrintSettingsMapper.toChromaticity(PrintColor.MONOCHROME));
        assertNull(PrintSettingsMapper.toChromaticity(null));
    }

    @Test
    public void shouldReturnEmptyAttributesForMissingSettings() {
        assertEquals(0, PrintSettingsMapper.createAttributes(null).size());
    }
}
