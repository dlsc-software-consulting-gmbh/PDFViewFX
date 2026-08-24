package com.dlsc.pdfviewfx;

import com.dlsc.pdfviewfx.PDFView.Document.ClosablePageable;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class PDFBoxDocumentTest {

    private PDFBoxDocument createTestDocument() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.addPage(new PDPage());
            document.save(out);
        }

        return new PDFBoxDocument(new ByteArrayInputStream(out.toByteArray()));
    }

    @Test
    public void shouldCreateIndependentPageables() throws IOException {
        PDFBoxDocument document = createTestDocument();

        try {
            ClosablePageable first = document.createPageable();
            ClosablePageable second = document.createPageable();

            assertNotSame(first, second);
            assertEquals(2, first.getNumberOfPages());
            assertEquals(2, second.getNumberOfPages());

            first.close();

            // closing one pageable must affect neither the other pageable nor the document
            assertEquals(2, second.getNumberOfPages());
            assertEquals(2, document.getNumberOfPages());

            second.close();
        } finally {
            document.close();
        }
    }

    @Test
    public void shouldReleasePageableResources() throws IOException {
        PDFBoxDocument document = createTestDocument();

        try {
            PDFBoxDocument.PrintPageable pageable = (PDFBoxDocument.PrintPageable) document.createPageable();
            assertFalse(pageable.getPrintDocument().getDocument().isClosed());

            pageable.close();

            // closing more than once must not cause any trouble
            pageable.close();

            assertTrue("the document loaded for printing should have been closed", pageable.getPrintDocument().getDocument().isClosed());
        } finally {
            document.close();
        }
    }
}
