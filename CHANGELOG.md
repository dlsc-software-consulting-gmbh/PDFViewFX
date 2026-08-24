# Change Log

## Unreleased

* Improved printing (#30):
  * added `PDFView.print()` / `PDFView.print(Window)`, so applications no longer have to implement the
    printing themselves.
  * the print dialog is now shown via the JavaFX printing API, the settings are mapped to
    `javax.print` attributes and the actual printing is done via PDFBox and the printing pipeline
    of the platform.
  * printing runs on a background thread, `PDFView.printingProperty()` signals a running job and
    `PDFView.onPrintErrorProperty()` receives errors.
  * added a print button to the toolbar, controlled via `PDFView.showPrintButtonProperty()` /
    `-fx-show-print-button`.
  * added `PDFView.PrintHandler` and `PDFView.printHandlerProperty()` for custom printing flows.
  * added `PDFView.Document.createPageable()`, which returns a closable pageable, and deprecated
    `PDFView.Document.getPageable()`, whose result kept an open `PDDocument` alive forever.
  * the selected page range is applied by the library itself instead of via the page range
    attribute of the printing API, which prints nothing on macOS for ranges that do not start
    at the first page (JDK-8297191).