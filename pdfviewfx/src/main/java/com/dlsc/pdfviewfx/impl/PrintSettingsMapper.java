package com.dlsc.pdfviewfx.impl;

import javafx.print.Collation;
import javafx.print.JobSettings;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.PageRange;
import javafx.print.Paper;
import javafx.print.PrintColor;
import javafx.print.PrintQuality;
import javafx.print.PrintSides;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Chromaticity;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.MediaSize;
import javax.print.attribute.standard.OrientationRequested;
import javax.print.attribute.standard.SheetCollate;
import javax.print.attribute.standard.Sides;
import javax.print.attribute.HashPrintRequestAttributeSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps the settings gathered via the JavaFX printing API (which is only used for showing
 * a dialog that fits into a JavaFX application) to the attributes required by the
 * {@code javax.print} API, which is used for the actual printing of the PDF document.
 */
public final class PrintSettingsMapper {

    private static final float POINTS_PER_INCH = 72f;

    private PrintSettingsMapper() {
    }

    /**
     * Converts the given JavaFX job settings into a set of print request attributes. Note that the
     * selected page range is not part of the returned attributes, as the page range attribute is
     * not reliably supported by all platforms.
     *
     * @param settings the settings as configured by the user via the JavaFX print dialog
     * @return the equivalent print request attributes
     * @see #toPageIndexes(PageRange[], int)
     */
    public static PrintRequestAttributeSet createAttributes(JobSettings settings) {
        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();

        if (settings == null) {
            return attributes;
        }

        addIfNotNull(attributes, toCopies(settings.getCopies()));
        addIfNotNull(attributes, toSides(settings.getPrintSides()));
        addIfNotNull(attributes, toSheetCollate(settings.getCollation()));
        addIfNotNull(attributes, toPrintQuality(settings.getPrintQuality()));
        addIfNotNull(attributes, toChromaticity(settings.getPrintColor()));

        PageLayout pageLayout = settings.getPageLayout();
        if (pageLayout != null) {
            addIfNotNull(attributes, toOrientationRequested(pageLayout.getPageOrientation()));

            Paper paper = pageLayout.getPaper();
            if (paper != null) {
                addIfNotNull(attributes, findMedia(paper.getWidth(), paper.getHeight()));
            }
        }

        String jobName = settings.getJobName();
        if (jobName != null && !jobName.isBlank()) {
            attributes.add(new JobName(jobName, null));
        }

        return attributes;
    }

    /**
     * Looks up the print service with the given name. Falls back to the default print
     * service if no service with a matching name can be found.
     *
     * @param printerName the name of the printer, may be null
     * @return the matching print service or null if no print service is available at all
     */
    public static PrintService findPrintService(String printerName) {
        if (printerName != null && !printerName.isBlank()) {
            for (PrintService service : PrintServiceLookup.lookupPrintServices(null, null)) {
                if (printerName.equals(service.getName())) {
                    return service;
                }
            }
        }

        return PrintServiceLookup.lookupDefaultPrintService();
    }

    static Copies toCopies(int copies) {
        return copies > 0 ? new Copies(copies) : null;
    }

    /**
     * Determines the (zero-based) indexes of the pages that are covered by the given page ranges.
     * The pages are selected by the library itself instead of via the page range attribute of the
     * printing API, because that attribute is ignored on some platforms and even causes nothing to
     * be printed at all on macOS (see JDK-8297191).
     *
     * @param ranges         the page ranges as configured by the user, may be null
     * @param numberOfPages  the total number of pages of the document
     * @return the indexes of the pages to print or null if all pages have to be printed
     */
    public static int[] toPageIndexes(PageRange[] ranges, int numberOfPages) {
        if (ranges == null || ranges.length == 0) {
            return null;
        }

        int[][] members = new int[ranges.length][];

        for (int i = 0; i < ranges.length; i++) {
            PageRange range = ranges[i];
            members[i] = range != null ? new int[]{range.getStartPage(), range.getEndPage()} : new int[]{0, -1};
        }

        return toPageIndexes(members, numberOfPages);
    }

    static int[] toPageIndexes(int[][] ranges, int numberOfPages) {
        if (ranges == null || ranges.length == 0) {
            return null;
        }

        List<Integer> indexes = new ArrayList<>();

        for (int[] range : ranges) {
            if (range == null || range.length < 2) {
                continue;
            }

            for (int page = Math.max(1, range[0]); page <= Math.min(numberOfPages, range[1]); page++) {
                int index = page - 1;
                if (!indexes.contains(index)) {
                    indexes.add(index);
                }
            }
        }

        return indexes.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Determines the media (paper) that matches the given dimensions.
     *
     * @param widthInPoints  the paper width in points (1/72 inch)
     * @param heightInPoints the paper height in points (1/72 inch)
     * @return the matching media or null if no standard media matches
     */
    static Media findMedia(double widthInPoints, double heightInPoints) {
        if (widthInPoints <= 0 || heightInPoints <= 0) {
            return null;
        }

        return MediaSize.findMedia((float) (widthInPoints / POINTS_PER_INCH), (float) (heightInPoints / POINTS_PER_INCH), MediaSize.INCH);
    }

    static OrientationRequested toOrientationRequested(PageOrientation orientation) {
        if (orientation == null) {
            return null;
        }

        return switch (orientation) {
            case PORTRAIT -> OrientationRequested.PORTRAIT;
            case LANDSCAPE -> OrientationRequested.LANDSCAPE;
            case REVERSE_PORTRAIT -> OrientationRequested.REVERSE_PORTRAIT;
            case REVERSE_LANDSCAPE -> OrientationRequested.REVERSE_LANDSCAPE;
        };
    }

    static Sides toSides(PrintSides sides) {
        if (sides == null) {
            return null;
        }

        if (PrintSides.DUPLEX.equals(sides)) {
            return Sides.TWO_SIDED_LONG_EDGE;
        }

        if (PrintSides.TUMBLE.equals(sides)) {
            return Sides.TWO_SIDED_SHORT_EDGE;
        }

        return Sides.ONE_SIDED;
    }

    static SheetCollate toSheetCollate(Collation collation) {
        if (collation == null) {
            return null;
        }

        return Collation.COLLATED.equals(collation) ? SheetCollate.COLLATED : SheetCollate.UNCOLLATED;
    }

    static javax.print.attribute.standard.PrintQuality toPrintQuality(PrintQuality quality) {
        if (quality == null) {
            return null;
        }

        if (PrintQuality.DRAFT.equals(quality) || PrintQuality.LOW.equals(quality)) {
            return javax.print.attribute.standard.PrintQuality.DRAFT;
        }

        if (PrintQuality.HIGH.equals(quality)) {
            return javax.print.attribute.standard.PrintQuality.HIGH;
        }

        return javax.print.attribute.standard.PrintQuality.NORMAL;
    }

    static Chromaticity toChromaticity(PrintColor color) {
        if (color == null) {
            return null;
        }

        return PrintColor.MONOCHROME.equals(color) ? Chromaticity.MONOCHROME : Chromaticity.COLOR;
    }

    private static void addIfNotNull(PrintRequestAttributeSet attributes, javax.print.attribute.Attribute attribute) {
        if (attribute != null) {
            attributes.add(attribute);
        }
    }
}
