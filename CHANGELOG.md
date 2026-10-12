# Change Log

## Unreleased

* Added annotations (#47): text selected on a page can be highlighted (with a comment), struck out, marked for
  replacement or for an insertion, and sticky notes can be placed anywhere on a page. The tools are available in
  the context menu and via the keys `A`, `S`, `R`, `I` and `N` while the page has the focus; an editor next to the
  new annotation takes its color and text. The annotations are
  stored as standard PDF annotations, so `PDFView.save(File)` writes a document that other viewers understand.
  Applications can add annotations programmatically via `PDFView.getAnnotations()`; documents that support this
  implement `PDFView.AnnotatableDocument`.
* A click into the empty space between two lines no longer selects text.
* Text selection respects two-column layouts: a selection stays within its column, a selection across the
  columns follows the reading order.
* `PDFBoxDocument` now reads files into memory instead of keeping them open, so that a document can be saved to
  the file it was loaded from.
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