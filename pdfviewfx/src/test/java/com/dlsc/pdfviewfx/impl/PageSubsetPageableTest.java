package com.dlsc.pdfviewfx.impl;

import com.dlsc.pdfviewfx.PDFView.Document.ClosablePageable;
import org.junit.Test;

import java.awt.Graphics;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PageSubsetPageableTest {

    private static class TestPageable implements ClosablePageable {

        private final List<Integer> printedPages = new ArrayList<>();
        private final List<Integer> requestedFormats = new ArrayList<>();

        private boolean closed;

        @Override
        public int getNumberOfPages() {
            return 10;
        }

        @Override
        public PageFormat getPageFormat(int pageIndex) {
            requestedFormats.add(pageIndex);
            return new PageFormat();
        }

        @Override
        public Printable getPrintable(int pageIndex) {
            return (graphics, pageFormat, index) -> {
                printedPages.add(index);
                return Printable.PAGE_EXISTS;
            };
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    public void shouldOnlyExposeSelectedPages() {
        TestPageable source = new TestPageable();
        PageSubsetPageable pageable = new PageSubsetPageable(source, new int[]{2, 5});

        assertEquals(2, pageable.getNumberOfPages());

        pageable.getPageFormat(1);
        assertEquals(List.of(5), source.requestedFormats);
    }

    @Test
    public void shouldPrintThePageOfTheDocument() throws Exception {
        TestPageable source = new TestPageable();
        PageSubsetPageable pageable = new PageSubsetPageable(source, new int[]{2, 5});

        for (int page = 0; page < pageable.getNumberOfPages(); page++) {
            pageable.getPrintable(page).print((Graphics) null, pageable.getPageFormat(page), page);
        }

        // the printable of the source document has to be called with the absolute page index
        assertEquals(List.of(2, 5), source.printedPages);
    }

    @Test
    public void shouldRejectInvalidPageIndexes() {
        PageSubsetPageable pageable = new PageSubsetPageable(new TestPageable(), new int[]{2, 5});

        assertThrows(IndexOutOfBoundsException.class, () -> pageable.getPageFormat(2));
        assertThrows(IndexOutOfBoundsException.class, () -> pageable.getPrintable(-1));
    }

    @Test
    public void shouldCloseTheSourcePageable() {
        TestPageable source = new TestPageable();
        new PageSubsetPageable(source, new int[]{1}).close();

        assertTrue(source.closed);
    }
}
