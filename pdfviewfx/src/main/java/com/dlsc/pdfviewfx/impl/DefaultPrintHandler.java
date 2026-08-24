package com.dlsc.pdfviewfx.impl;

import com.dlsc.pdfviewfx.PDFView;
import com.dlsc.pdfviewfx.PDFView.Document;
import com.dlsc.pdfviewfx.PDFView.Document.ClosablePageable;
import javafx.print.PageRange;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.stage.Window;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import javax.print.SimpleDoc;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.event.PrintJobAdapter;
import javax.print.event.PrintJobEvent;
import java.awt.EventQueue;
import java.awt.print.PrinterException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The default implementation of {@link PDFView.PrintHandler}. The JavaFX printing API is used
 * for showing a print dialog that fits into a JavaFX application. The settings chosen by the
 * user are then mapped to {@code javax.print} attributes and the document is printed via the
 * pageable created by {@link Document#createPageable()}. This way the printed output keeps its
 * vector quality and the resources allocated for printing are released as soon as the print
 * job has ended.
 */
public class DefaultPrintHandler implements PDFView.PrintHandler {

    private static final DocFlavor FLAVOR = DocFlavor.SERVICE_FORMATTED.PAGEABLE;

    @Override
    public Runnable createPrintJob(PDFView view, Window owner) {
        Document document = view.getDocument();
        if (document == null) {
            return null;
        }

        PrinterJob dialogJob = PrinterJob.createPrinterJob();
        if (dialogJob == null) {
            throw new IllegalStateException("no printer available");
        }

        String printerName;
        PrintRequestAttributeSet attributes;
        PageRange[] pageRanges;

        try {
            if (!dialogJob.showPrintDialog(owner)) {
                return null;
            }

            Printer printer = dialogJob.getPrinter();
            printerName = printer != null ? printer.getName() : null;
            attributes = PrintSettingsMapper.createAttributes(dialogJob.getJobSettings());
            pageRanges = dialogJob.getJobSettings().getPageRanges();
        } finally {
            // the JavaFX job is only used for showing the dialog, it must never print
            dialogJob.cancelJob();
        }

        return () -> print(document, printerName, attributes, pageRanges);
    }

    private void print(Document document, String printerName, PrintRequestAttributeSet attributes, PageRange[] pageRanges) {
        PrintService service = PrintSettingsMapper.findPrintService(printerName);
        if (service == null) {
            throw new IllegalStateException("no print service found for printer: " + printerName);
        }

        // the printer job of the AWT printing API uses the printing pipeline of the platform,
        // which is why it is the preferred way of printing. Print services on the other hand
        // often only support printing a pageable by converting it to PostScript first, which
        // is rejected by printers that only accept PDF.
        java.awt.print.PrinterJob printerJob = java.awt.print.PrinterJob.getPrinterJob();
        boolean printerJobSupported = true;

        try {
            printerJob.setPrintService(service);
        } catch (PrinterException e) {
            printerJobSupported = false;
        }

        if (!printerJobSupported && !service.isDocFlavorSupported(FLAVOR)) {
            throw new IllegalStateException("printing is not supported by print service: " + service.getName());
        }

        try (ClosablePageable pageable = selectPages(document.createPageable(), pageRanges)) {
            if (pageable.getNumberOfPages() == 0) {
                return;
            }

            if (printerJobSupported) {
                printViaPrinterJob(printerJob, pageable, attributes);
            } else {
                printViaPrintService(service, pageable, attributes);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("printing failed", e);
        }
    }

    private void printViaPrintService(PrintService service, ClosablePageable pageable, PrintRequestAttributeSet attributes) throws Exception {
        DocPrintJob job = service.createPrintJob();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<PrintJobEvent> failure = new AtomicReference<>();

        job.addPrintJobListener(new PrintJobAdapter() {

            @Override
            public void printJobCompleted(PrintJobEvent event) {
                finish(null);
            }

            @Override
            public void printJobCanceled(PrintJobEvent event) {
                finish(null);
            }

            @Override
            public void printJobFailed(PrintJobEvent event) {
                finish(event);
            }

            @Override
            public void printJobNoMoreEvents(PrintJobEvent event) {
                finish(null);
            }

            private void finish(PrintJobEvent event) {
                if (event != null) {
                    failure.compareAndSet(null, event);
                }

                // releases the document that was loaded for this print job
                pageable.close();

                latch.countDown();
            }
        });

        Doc doc = new SimpleDoc(pageable, FLAVOR, null);
        job.print(doc, attributes);

        // the timeout only prevents this thread from being blocked forever should the
        // print service fail to deliver any of the expected events
        latch.await(10, TimeUnit.MINUTES);

        if (failure.get() != null) {
            throw new IllegalStateException("the print job failed");
        }
    }

    private ClosablePageable selectPages(ClosablePageable pageable, PageRange[] pageRanges) {
        int[] pageIndexes = PrintSettingsMapper.toPageIndexes(pageRanges, pageable.getNumberOfPages());
        if (pageIndexes == null) {
            return pageable;
        }

        return new PageSubsetPageable(pageable, pageIndexes);
    }

    private void printViaPrinterJob(java.awt.print.PrinterJob job, ClosablePageable pageable, PrintRequestAttributeSet attributes) throws Exception {
        job.setPageable(pageable);

        AtomicReference<Exception> error = new AtomicReference<>();

        Runnable printing = () -> {
            try {
                job.print(attributes);
            } catch (Exception e) {
                error.set(e);
            }
        };

        // the native printing of the platform has to be triggered from the event dispatch
        // thread of AWT, otherwise the print loop will never be executed inside applications
        // that are not driven by the AWT event queue (which is the case for JavaFX)
        if (EventQueue.isDispatchThread()) {
            printing.run();
        } else {
            EventQueue.invokeAndWait(printing);
        }

        if (error.get() != null) {
            throw error.get();
        }
    }
}
