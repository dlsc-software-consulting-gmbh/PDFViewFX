package com.dlsc.pdfviewfx.impl;

import com.dlsc.pdfviewfx.PDFView.Document.ClosablePageable;

import java.awt.print.PageFormat;
import java.awt.print.Printable;

/**
 * A pageable that only exposes a subset of the pages of another pageable. It is used for
 * printing a page range, as the page range attribute of the printing API is not reliably
 * supported by all platforms.
 */
public class PageSubsetPageable implements ClosablePageable {

    private final ClosablePageable source;
    private final int[] pageIndexes;

    /**
     * Constructs a new pageable.
     *
     * @param source      the pageable containing all pages of the document
     * @param pageIndexes the zero-based indexes of the pages that shall be printed
     */
    public PageSubsetPageable(ClosablePageable source, int[] pageIndexes) {
        this.source = source;
        this.pageIndexes = pageIndexes.clone();
    }

    @Override
    public int getNumberOfPages() {
        return pageIndexes.length;
    }

    @Override
    public PageFormat getPageFormat(int pageIndex) {
        return source.getPageFormat(toSourceIndex(pageIndex));
    }

    @Override
    public Printable getPrintable(int pageIndex) {
        int sourceIndex = toSourceIndex(pageIndex);
        Printable printable = source.getPrintable(sourceIndex);

        // the printable of the source expects the index of the page within the whole document
        return (graphics, pageFormat, index) -> printable.print(graphics, pageFormat, sourceIndex);
    }

    @Override
    public void close() {
        source.close();
    }

    private int toSourceIndex(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= pageIndexes.length) {
            throw new IndexOutOfBoundsException("invalid page index: " + pageIndex);
        }

        return pageIndexes[pageIndex];
    }
}
