package com.dlsc.pdfviewfx;

import com.dlsc.pdfviewfx.PDFView.Document.DocumentProcessingException;
import com.dlsc.pdfviewfx.impl.DefaultPrintHandler;
import com.dlsc.pdfviewfx.skins.PDFViewSkin;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.ColorConverter;
import javafx.css.converter.SizeConverter;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Skin;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCombination;
import javafx.scene.paint.Color;
import javafx.stage.Window;

import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Printable;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A PDF viewer based on Apache PDFBox. The view shows thumbnails
 * on the left and the full page on the right. The user can zoom in,
 * rotate, fit size, etc...
 */
public class PDFView extends Control {

    private static final Logger LOG = Logger.getLogger(PDFView.class.getName());

    private static final boolean DEFAULT_SHOW_THUMBNAILS = true;
    private static final boolean DEFAULT_SHOW_TOOLBAR = true;
    private static final boolean DEFAULT_SHOW_PRINT_BUTTON = true;
    private static final boolean DEFAULT_SHOW_SEARCH_RESULTS = true;
    private static final boolean DEFAULT_SHOW_ALL = false;
    private static final double DEFAULT_THUMBNAIL_SIZE = 200d;
    private static final Color DEFAULT_SEARCH_RESULT_COLOR = Color.RED;
    private static final Color DEFAULT_SELECTION_COLOR = Color.BLUE;

    /**
     * The base name of the resource bundle that ships with this library.
     */
    public static final String BUNDLE_BASE_NAME = "com.dlsc.pdfviewfx.pdf-view";

    private static ResourceBundle loadDefaultBundle() {
        return ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.getDefault(), PDFView.class.getModule());
    }

    /**
     * Looks up the text stored for the given key. The lookup is performed on the given bundle first. If the
     * bundle does not contain the key then the default bundle shipped with this library will be used. If even
     * that one does not contain the key then the key itself will be returned. This ensures that applications
     * can pass in bundles that only override a subset of the texts without risking a
     * {@link MissingResourceException} at runtime.
     *
     * @param bundle the bundle to use for the lookup, may be null
     * @param key the key of the requested text
     * @return the text for the given key, never null
     */
    public static String getString(ResourceBundle bundle, String key) {
        Objects.requireNonNull(key, "key can not be null");

        if (bundle != null && bundle.containsKey(key)) {
            return bundle.getString(key);
        }

        ResourceBundle defaultBundle = loadDefaultBundle();
        if (defaultBundle.containsKey(key)) {
            return defaultBundle.getString(key);
        }

        return key;
    }

    /**
     * Constructs a new view.
     */
    public PDFView() {
        super();

        getStyleClass().add("pdf-view");
        setFocusTraversable(false);

        zoomFactorProperty().addListener(it -> {
            if (getZoomFactor() < 1) {
                throw new IllegalArgumentException("zoom factor can not be smaller than 1");
            } else if (getZoomFactor() > getMaxZoomFactor()) {
                throw new IllegalArgumentException("zoom factor can not be larger than max zoom factor, but " + getZoomFactor() + " > " + getMaxZoomFactor());
            }
        });

        showAllProperty().addListener(it -> {
            if (isShowAll()) {
                setZoomFactor(1);
            }
        });

        selectedSearchResultProperty().addListener(it -> {
            SearchResult result = getSelectedSearchResult();
            if (result != null) {
                setPage(result.getPageNumber());
            }
        });

        documentProperty().addListener((obs, oldDoc, newDoc) -> {
            if (oldDoc != null) {
                oldDoc.close();
            }

            setSearchText(null);
        });

        MenuItem copyMenuItem = new MenuItem(getString("pdf-view.menu.copy"));
        copyMenuItem.disableProperty().bind(selection.isNull());
        copyMenuItem.setOnAction(e -> copy());
        copyMenuItem.setAccelerator(KeyCombination.keyCombination("Shortcut+C"));
        setContextMenu(new ContextMenu(copyMenuItem));
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new PDFViewSkin(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return Objects.requireNonNull(PDFView.class.getResource("pdf-view.css")).toExternalForm();
    }

    // resource bundle

    private final ObjectProperty<ResourceBundle> resourceBundle = new SimpleObjectProperty<>(this, "resourceBundle", loadDefaultBundle()) {
        @Override
        public void set(ResourceBundle newValue) {
            super.set(newValue != null ? newValue : loadDefaultBundle());
        }
    };

    /**
     * Stores the resource bundle that will be used for looking up the texts shown by the view, e.g. the
     * tooltips of the toolbar buttons. The default bundle ships with this library and supports a number of
     * languages. Applications can replace it with their own bundle in order to add languages or to override
     * individual texts. Keys that are missing in a custom bundle will be looked up in the default bundle, so
     * a bundle only needs to define the texts that it actually wants to change.
     * <p>
     * Please note that the texts are only read once, when the view creates its skin. Changing the bundle
     * afterwards will have no effect on a view that is already showing. The bundle has to be set before the
     * view gets displayed for the first time.
     * </p>
     *
     * @return the resource bundle used for the texts shown by the view
     */
    public final ObjectProperty<ResourceBundle> resourceBundleProperty() {
        return resourceBundle;
    }

    public final ResourceBundle getResourceBundle() {
        return resourceBundle.get();
    }

    public final void setResourceBundle(ResourceBundle resourceBundle) {
        this.resourceBundle.set(resourceBundle);
    }

    /**
     * Looks up the text stored for the given key in the bundle returned by {@link #getResourceBundle()}.
     *
     * @param key the key of the requested text
     * @return the text for the given key, never null
     */
    public final String getString(String key) {
        return getString(getResourceBundle(), key);
    }

    // show thumbnails

    private final BooleanProperty showThumbnails = new StyleableBooleanProperty(DEFAULT_SHOW_THUMBNAILS) {
        @Override
        public Object getBean() {
            return PDFView.this;
        }

        @Override
        public String getName() {
            return "showThumbnails";
        }

        @Override
        public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
            return StyleableProperties.SHOW_THUMBNAILS;
        }
    };

    public final boolean isShowThumbnails() {
        return showThumbnails.get();
    }

    /**
     * A flag used to control whether the view will display a thumbnail version of the pages
     * on the left-hand side.
     */
    public final BooleanProperty showThumbnailsProperty() {
        return showThumbnails;
    }

    public final void setShowThumbnails(boolean showThumbnails) {
        this.showThumbnails.set(showThumbnails);
    }

    // show toolbar

    private final BooleanProperty showToolBar = new StyleableBooleanProperty(DEFAULT_SHOW_TOOLBAR) {
        @Override
        public Object getBean() {
            return PDFView.this;
        }

        @Override
        public String getName() {
            return "showToolBar";
        }

        @Override
        public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
            return StyleableProperties.SHOW_TOOLBAR;
        }
    };

    public final boolean isShowToolBar() {
        return showToolBar.get();
    }

    /**
     * A flag used to control whether the view will include a toolbar with zoom, search, rotation
     * controls.
     */
    public final BooleanProperty showToolBarProperty() {
        return showToolBar;
    }

    public final void setShowToolBar(boolean showToolBar) {
        this.showToolBar.set(showToolBar);
    }

    // show print button

    private final BooleanProperty showPrintButton = new StyleableBooleanProperty(DEFAULT_SHOW_PRINT_BUTTON) {
        @Override
        public Object getBean() {
            return PDFView.this;
        }

        @Override
        public String getName() {
            return "showPrintButton";
        }

        @Override
        public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
            return StyleableProperties.SHOW_PRINT_BUTTON;
        }
    };

    public final boolean isShowPrintButton() {
        return showPrintButton.get();
    }

    /**
     * A flag used to control whether the toolbar will include a button for printing the
     * currently loaded document.
     *
     * @see #print()
     */
    public final BooleanProperty showPrintButtonProperty() {
        return showPrintButton;
    }

    public final void setShowPrintButton(boolean showPrintButton) {
        this.showPrintButton.set(showPrintButton);
    }

    // show search results

    private final BooleanProperty showSearchResults = new StyleableBooleanProperty(DEFAULT_SHOW_SEARCH_RESULTS) {
        @Override
        public Object getBean() {
            return PDFView.this;
        }

        @Override
        public String getName() {
            return "showSearchResults";
        }

        @Override
        public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
            return StyleableProperties.SHOW_TOOLBAR;
        }
    };

    public final boolean isShowSearchResults() {
        return showSearchResults.get();
    }

    public final BooleanProperty showSearchResultsProperty() {
        return showSearchResults;
    }

    public final void setShowSearchResults(boolean showSearchResults) {
        this.showSearchResults.set(showSearchResults);
    }

    /**
     * Caching thumbnails can be useful for low-powered systems with enough memory. The default value
     * is "true". When set to "true" each thumbnail image will be added to a hashmap cache, hence making it
     * necessary to only render once.
     */
    private final BooleanProperty cacheThumbnails = new SimpleBooleanProperty(this, "cacheThumbnails", true);

    public final boolean isCacheThumbnails() {
        return cacheThumbnails.get();
    }

    public final BooleanProperty cacheThumbnailsProperty() {
        return cacheThumbnails;
    }

    public final void setCacheThumbnails(boolean cacheThumbnails) {
        this.cacheThumbnails.set(cacheThumbnails);
    }

    /**
     * Sets the upper bounds for zoom operations. The default value is "4".
     */
    private final DoubleProperty maxZoomFactor = new SimpleDoubleProperty(this, "maxZoomFactor", 4);

    public final double getMaxZoomFactor() {
        return maxZoomFactor.get();
    }

    public final DoubleProperty maxZoomFactorProperty() {
        return maxZoomFactor;
    }

    public final void setMaxZoomFactor(double maxZoomFactor) {
        this.maxZoomFactor.set(maxZoomFactor);
    }

    /**
     * The current zoom factor. The default value is "1".
     */
    private final DoubleProperty zoomFactor = new SimpleDoubleProperty(this, "zoomFactor", 1);

    public final double getZoomFactor() {
        return zoomFactor.get();
    }

    public final DoubleProperty zoomFactorProperty() {
        return zoomFactor;
    }

    public final void setZoomFactor(double zoomFactor) {
        this.zoomFactor.set(zoomFactor);
    }

    /**
     * The page rotation in degrees. Supported values are only "0", "90", "180", "270", "360", ...
     * multiples of "90".
     */
    private final DoubleProperty pageRotation = new SimpleDoubleProperty(this, "pageRotation", 0) {
        @Override
        public void set(double newValue) {
            super.set(newValue % 360d);
        }
    };

    public final double getPageRotation() {
        return pageRotation.get();
    }

    public final DoubleProperty pageRotationProperty() {
        return pageRotation;
    }

    public final void setPageRotation(double pageRotation) {
        this.pageRotation.set(pageRotation);
    }

    /**
     * Convenience method to rotate the generated image by -90 degrees.
     */
    public final void rotateLeft() {
        setPageRotation(getPageRotation() - 90);
    }

    /**
     * Convenience method to rotate the generated image by +90 degrees.
     */
    public final void rotateRight() {
        setPageRotation(getPageRotation() + 90);
    }

    /**
     * Stores the number of the currently showing page.
     */
    private final IntegerProperty page = new SimpleIntegerProperty(this, "page");

    public final int getPage() {
        return page.get();
    }

    public final IntegerProperty pageProperty() {
        return page;
    }

    public final void setPage(int page) {
        this.page.set(page);
    }

    /**
     * Convenience method to show the next page. This simply increases the {@link #pageProperty()} value
     * by 1.
     *
     * @return true if the operation actually did cause a page change
     */
    public final boolean gotoNextPage() {
        int currentPage = getPage();
        setPage(Math.min(getDocument().getNumberOfPages() - 1, getPage() + 1));
        return currentPage != getPage();
    }

    /**
     * Convenience method to show the previous page. This simply decreases the {@link #pageProperty()} value
     * by 1.
     *
     * @return true if the operation actually did cause a page change
     */
    public final boolean gotoPreviousPage() {
        int currentPage = getPage();
        setPage(Math.max(0, getPage() - 1));
        return currentPage != getPage();
    }

    /**
     * Convenience method to show the last page.
     *
     * @return true if the operation actually did cause a page change
     */
    public final boolean gotoLastPage() {
        int currentPage = getPage();
        setPage(getDocument().getNumberOfPages() - 1);
        return currentPage != getPage();
    }

    /**
     * Copy selected text to clipboard.
     */
    public void copy() {
        if (getSelection() != null) {
            ClipboardContent content = new ClipboardContent();
            content.putString(getSelection().getSelectedText());
            Clipboard.getSystemClipboard().setContent(content);
        }
    }

    // printing

    /**
     * A handler responsible for the printing of the currently loaded document.
     *
     * @see PDFView#printHandlerProperty()
     */
    @FunctionalInterface
    public interface PrintHandler {

        /**
         * Creates the print job for the document currently shown by the given view. This method
         * is called on the JavaFX application thread and it is the right place to show a print
         * dialog. The returned job will be executed on a background thread.
         *
         * @param view  the view that requested the printing
         * @param owner the window that shall own the print dialog, may be null
         * @return the print job to execute or null if the user cancelled the printing
         */
        Runnable createPrintJob(PDFView view, Window owner);
    }

    private final ObjectProperty<PrintHandler> printHandler = new SimpleObjectProperty<>(this, "printHandler", new DefaultPrintHandler());

    public final PrintHandler getPrintHandler() {
        return printHandler.get();
    }

    /**
     * The handler that will be used by {@link #print()} to show a print dialog and to
     * perform the actual printing. The default handler shows the print dialog of the JavaFX
     * printing API and then prints the document via the {@code javax.print} API.
     */
    public final ObjectProperty<PrintHandler> printHandlerProperty() {
        return printHandler;
    }

    public final void setPrintHandler(PrintHandler printHandler) {
        this.printHandler.set(printHandler);
    }

    private final ObjectProperty<Consumer<Throwable>> onPrintError = new SimpleObjectProperty<>(this, "onPrintError", error -> LOG.log(Level.SEVERE, "printing failed", error));

    public final Consumer<Throwable> getOnPrintError() {
        return onPrintError.get();
    }

    /**
     * The callback that will be invoked on the JavaFX application thread whenever a print job
     * fails. The default implementation logs the error.
     */
    public final ObjectProperty<Consumer<Throwable>> onPrintErrorProperty() {
        return onPrintError;
    }

    public final void setOnPrintError(Consumer<Throwable> onPrintError) {
        this.onPrintError.set(onPrintError);
    }

    private final ReadOnlyBooleanWrapper printing = new ReadOnlyBooleanWrapper(this, "printing", false);

    public final boolean isPrinting() {
        return printing.get();
    }

    /**
     * A read-only flag that is set to true while a print job started via {@link #print()} is
     * still running.
     */
    public final ReadOnlyBooleanProperty printingProperty() {
        return printing.getReadOnlyProperty();
    }

    /**
     * Prints the currently loaded document. A print dialog will be shown so that the user can
     * choose the printer and the print settings. The document will then be printed on a
     * background thread, hence this method returns immediately. Errors will be passed to the
     * callback stored in {@link #onPrintErrorProperty()}.
     *
     * @see #print(Window)
     */
    public final void print() {
        print(getScene() != null ? getScene().getWindow() : null);
    }

    /**
     * Prints the currently loaded document. A print dialog will be shown so that the user can
     * choose the printer and the print settings. The document will then be printed on a
     * background thread, hence this method returns immediately. Errors will be passed to the
     * callback stored in {@link #onPrintErrorProperty()}.
     *
     * @param owner the window that will own the print dialog, may be null
     */
    public final void print(Window owner) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("printing has to be started on the JavaFX application thread");
        }

        if (getDocument() == null || isPrinting()) {
            return;
        }

        PrintHandler handler = getPrintHandler();
        if (handler == null) {
            return;
        }

        Runnable job;

        try {
            job = handler.createPrintJob(this, owner);
        } catch (Throwable throwable) {
            handlePrintError(throwable);
            return;
        }

        if (job == null) {
            // the user cancelled the printing
            return;
        }

        printing.set(true);

        Thread thread = new Thread(() -> {
            try {
                job.run();
            } catch (Throwable throwable) {
                Platform.runLater(() -> handlePrintError(throwable));
            } finally {
                Platform.runLater(() -> printing.set(false));
            }
        }, "PDFView Print Job Thread");

        thread.setDaemon(true);
        thread.start();
    }

    private void handlePrintError(Throwable throwable) {
        Consumer<Throwable> handler = getOnPrintError();
        if (handler != null) {
            handler.accept(throwable);
        }
    }

    // show all

    private final BooleanProperty showAll = new StyleableBooleanProperty(DEFAULT_SHOW_SEARCH_RESULTS) {
        @Override
        public Object getBean() {
            return PDFView.this;
        }

        @Override
        public String getName() {
            return "showAll";
        }

        @Override
        public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
            return StyleableProperties.SHOW_ALL;
        }
    };

    public final boolean isShowAll() {
        return showAll.get();
    }

    /**
     * A flag that controls whether we always want to show the entire page. If "true" then the page
     * will be constantly resized to fit the viewport of the scroll pane in which it is showing. In
     * this mode zooming is not possible.
     */
    public final BooleanProperty showAllProperty() {
        return showAll;
    }

    public final void setShowAll(boolean showAll) {
        this.showAll.set(showAll);
    }

    private final FloatProperty thumbnailPageScale = new SimpleFloatProperty(this, "thumbnailScale", 1f);

    public final float getThumbnailPageScale() {
        return thumbnailPageScale.get();
    }

    /**
     * The resolution / scale at which the thumbnails will be rendered. The default value is "1".
     */
    public final FloatProperty thumbnailPageScaleProperty() {
        return thumbnailPageScale;
    }

    public final void setThumbnailPageScale(float thumbnailPageScale) {
        this.thumbnailPageScale.set(thumbnailPageScale);
    }

    // page scale

    private final FloatProperty pageScale = new SimpleFloatProperty(this, "pageScale", 4f);

    public final float getPageScale() {
        return pageScale.get();
    }

    /**
     * The resolution / scale at which the main page will be rendered. The default value is "4".
     * The value has a direct impact on the size of the images being generated and the memory requirements.
     * Keep low on low-powered / low-resolution systems and high on large systems with hires displays.
     */
    public final FloatProperty pageScaleProperty() {
        return pageScale;
    }

    public final void setPageScale(float pageScale) {
        this.pageScale.set(pageScale);
    }

    // thumbnail size

    private final DoubleProperty thumbnailSize = new StyleableDoubleProperty(DEFAULT_THUMBNAIL_SIZE) {

        @Override
        public Object getBean() {
            return PDFView.this;
        }

        @Override
        public String getName() {
            return "thumbnailSize";
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.THUMBNAIL_SIZE;
        }
    };

    public final double getThumbnailSize() {
        return thumbnailSize.get();
    }

    /**
     * The size used for the images displayed in the thumbnail view. The default value is "200".
     */
    public final DoubleProperty thumbnailSizeProperty() {
        return thumbnailSize;
    }

    public final void setThumbnailSize(double thumbnailSize) {
        this.thumbnailSize.set(thumbnailSize);
    }

    private final ObjectProperty<Document> document = new SimpleObjectProperty<>(this, "document");

    /**
     * The currently loaded and displayed PDF document.
     */
    public final ObjectProperty<Document> documentProperty() {
        return document;
    }

    public final Document getDocument() {
        return document.get();
    }

    public final void setDocument(Document document) {
        setSelection(null);
        this.document.set(document);
    }

    /**
     * A text used for searching inside the document. Results will be highlighted.
     */
    private final StringProperty searchText = new SimpleStringProperty(this, "searchText");

    public final String getSearchText() {
        return searchText.get();
    }

    public final StringProperty searchTextProperty() {
        return searchText;
    }

    public final void setSearchText(String searchText) {
        this.searchText.set(searchText);
    }

    private final ListProperty<SearchResult> searchResults = new SimpleListProperty<>(this, "searchResults", FXCollections.observableArrayList());

    /**
     * Stores the list of currently found search results.
     *
     * @return the search results
     * @see #setSearchText(String)
     */
    public final ListProperty<SearchResult> searchResultsProperty() {
        return searchResults;
    }

    public final ObservableList<SearchResult> getSearchResults() {
        return searchResults.get();
    }

    public final void setSearchResults(ObservableList<SearchResult> searchResults) {
        this.searchResults.set(searchResults);
    }

    private final ObjectProperty<SearchResult> selectedSearchResult = new SimpleObjectProperty<>(this, "selectedSearchResult");

    /**
     * Stores the currently selected search result.
     *
     * @return the selected search result
     * @see #getSearchResults()
     * @see #setSearchText(String)
     */
    public final ObjectProperty<SearchResult> selectedSearchResultProperty() {
        return selectedSearchResult;
    }

    public final SearchResult getSelectedSearchResult() {
        return selectedSearchResult.get();
    }

    public final void setSelectedSearchResult(SearchResult selectedSearchResult) {
        this.selectedSearchResult.set(selectedSearchResult);
    }

    private final ObjectProperty<Color> searchResultColor = new StyleableObjectProperty<>(DEFAULT_SEARCH_RESULT_COLOR) {
        @Override
        public Object getBean() {
            return PDFView.this;
        }

        @Override
        public String getName() {
            return "searchResultColor";
        }

        @Override
        public CssMetaData<? extends Styleable, Color> getCssMetaData() {
            return StyleableProperties.SEARCH_RESULT_COLOR;
        }
    };

    /**
     * Stores the color to be used for highlighting search results.
     *
     * @return the search result highlight color
     */
    public final ObjectProperty<Color> searchResultColorProperty() {
        return searchResultColor;
    }

    public final Color getSearchResultColor() {
        return searchResultColor.get();
    }

    public final void setSearchResultColor(Color searchResultColor) {
        this.searchResultColor.set(searchResultColor);
    }

    private final ObjectProperty<Selection> selection = new SimpleObjectProperty<>(this, "selection");

    /**
     * Stores the currently selected search result.
     *
     * @return the selected search result
     * @see #getSearchResults()
     * @see #setSearchText(String)
     */
    public final ObjectProperty<Selection> selectionProperty() {
        return selection;
    }

    public final Selection getSelection() {
        return selection.get();
    }

    public final void setSelection(Selection selection) {
        this.selection.set(selection);
    }

    // selection color

    private final ObjectProperty<Color> selectionColor = new StyleableObjectProperty<>(DEFAULT_SELECTION_COLOR) {

        @Override
        public Object getBean() {
            return PDFView.this;
        }

        @Override
        public String getName() {
            return "selectionColor";
        }

        @Override
        public CssMetaData<? extends Styleable, Color> getCssMetaData() {
            return StyleableProperties.SELECTION_COLOR;
        }
    };

    /**
     * Stores the color to be used for highlighting search results.
     *
     * @return the search result highlight color
     */
    public final ObjectProperty<Color> selectionColorProperty() {
        return selectionColor;
    }

    public final Color getSelectionColor() {
        return selectionColor.get();
    }

    public final void setSelectionColor(Color selectionColor) {
        this.selectionColor.set(selectionColor);
    }
    
    /**
     * Loads the given PDF file.
     *
     * @param file a file containing a PDF document
     * @throws DocumentProcessingException if there is an error while reading/parsing a document.
     */
    public final void load(File file) {
        Objects.requireNonNull(file, "file can not be null");
        load(() -> new PDFBoxDocument(file));
    }

    /**
     * Loads the given PDF file.
     *
     * @param stream a stream returning a PDF document
     * @throws DocumentProcessingException if there is an error while reading/parsing a document.
     */
    public final void load(InputStream stream) {
        Objects.requireNonNull(stream, "stream can not be null");
        load(() -> new PDFBoxDocument(stream));
    }

    /**
     * Sets the document retrieved from the given supplier.
     *
     * @param supplier Document supplier.
     * @throws DocumentProcessingException if there is an error while reading/parsing of a document.
     */
    public final void load(Supplier<Document> supplier) {
        Objects.requireNonNull(supplier, "supplier can not be null");
        setDocument(supplier.get());
    }

    /**
     * Un-loads currently loaded document.
     */
    public final void unload() {
        setDocument(null);
        setSearchText(null);
        setZoomFactor(1);
        setRotate(0);
    }

    /**
     * The interface that needs to be implemented by any model object that
     * represents a PDF document and that wants to be displayed by the view.
     *
     * @see #setDocument(Document)
     * @see PDFBoxDocument
     */
    public interface Document {

        /**
         * Renders the page specified by the given number at the given scale.
         *
         * @param pageNumber the page number
         * @param scale      the scale
         * @return the generated buffered image
         */
        BufferedImage renderPage(int pageNumber, float scale);

        /**
         * Returns the total number of pages inside the document.
         *
         * @return the total number of pages
         */
        int getNumberOfPages();

        /**
         * Determines if the given page has a landscape orientation.
         *
         * @param pageNumber the page
         * @return true if the page has to be shown in landscape mode
         */
        boolean isLandscape(int pageNumber);

        /**
         * Returns a set of pages to be printed.
         *
         * @return a set of pages to be printed
         * @deprecated the pageable returned by this method might be backed by resources
         * that never get released. Use {@link #createPageable()} instead, which returns a
         * pageable that can (and must) be closed by its caller.
         */
        @Deprecated
        Pageable getPageable();

        /**
         * Creates a set of pages to be printed. The returned pageable owns the resources
         * required for printing (e.g. a separate copy of the parsed document) and has to be
         * closed by the caller once the print job has finished.
         * <p>
         * The default implementation delegates to the deprecated {@link #getPageable()} method
         * and performs no cleanup upon closing. Implementations that allocate resources for
         * printing should override this method.
         *
         * @return a closable set of pages to be printed
         */
        default ClosablePageable createPageable() {
            Pageable pageable = getPageable();
            return new ClosablePageable() {

                @Override
                public int getNumberOfPages() {
                    return pageable.getNumberOfPages();
                }

                @Override
                public PageFormat getPageFormat(int pageIndex) {
                    return pageable.getPageFormat(pageIndex);
                }

                @Override
                public Printable getPrintable(int pageIndex) {
                    return pageable.getPrintable(pageIndex);
                }

                @Override
                public void close() {
                }
            };
        }

        /**
         * Closes the document.
         */
        void close();

        /**
         * A {@link Pageable} that owns resources which have to be released once printing
         * has finished.
         *
         * @see Document#createPageable()
         */
        interface ClosablePageable extends Pageable, AutoCloseable {

            /**
             * Releases the resources held by this pageable. Implementations must be able to
             * cope with being called more than once.
             */
            @Override
            void close();
        }

        /**
         * A specialized exception for signalling processing errors while
         * reading / parsing a PDF file.
         */
        class DocumentProcessingException extends RuntimeException {

            /**
             * Constructs a new processing exception wrapping the given
             * cause.
             *
             * @param cause the reason for the exception
             */
            public DocumentProcessingException(Throwable cause) {
                super(cause);
            }
        }
    }

    /**
     * Documents that can be searched for a given text need to implement this
     * interface and return a list of search results.
     */
    public interface SearchableDocument extends Document {

        /**
         * Returns the list of search results for the given
         * search text.
         *
         * @param searchText the text for which to search
         * @return the list of search results
         */
        List<SearchResult> getSearchResults(String searchText);
    }

    /**
     * Represent a single match in the document.
     */
    public static class SearchResult implements Comparable<SearchResult> {

        private final String searchText;
        private final String textSnippet;
        private final int pageNumber;
        private final Rectangle2D marker;

        /**
         * Constructs a new search result.
         *
         * @param searchText  the text for which was searched
         * @param textSnippet a snippet of the text found at the search hit location
         * @param pageNumber  the page where the result can be found
         * @param marker      the visual bounds of the search hit
         */
        public SearchResult(String searchText, String textSnippet, int pageNumber, Rectangle2D marker) {
            this.searchText = searchText;
            this.textSnippet = textSnippet;
            this.pageNumber = pageNumber;
            this.marker = marker;
        }

        public Rectangle2D getMarker() {
            return marker;
        }

        public Rectangle2D getScaledMarker(double scale) {
            return new Rectangle2D(marker.getMinX() * scale, marker.getMinY() * scale, marker.getWidth() * scale, marker.getHeight() * scale);
        }

        public String getSearchText() {
            return searchText;
        }

        public String getTextSnippet() {
            return textSnippet;
        }

        public int getPageNumber() {
            return pageNumber;
        }

        @Override
        public int compareTo(SearchResult other) {
            int result = Integer.compare(pageNumber, other.pageNumber);

            if (result == 0) {
                result = Double.compare(getMarker().getMinY(), other.getMarker().getMinY());
            }

            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            SearchResult that = (SearchResult) o;
            return pageNumber == that.pageNumber &&
                    Objects.equals(searchText, that.searchText) &&
                    Objects.equals(textSnippet, that.textSnippet) &&
                    Objects.equals(marker, that.marker);
        }

        @Override
        public int hashCode() {
            return Objects.hash(searchText, textSnippet, pageNumber, marker);
        }
    }
    
    /**
     * Documents that can have text selection need to implement this
     * interface
     */
    public interface SelectableDocument extends Document {
        Selection getSelection(int pageNumber, Point2D start, Point2D end, Selection.Mode mode);
    }

    private static class StyleableProperties {
        private static final CssMetaData<PDFView, Boolean> SHOW_THUMBNAILS = new CssMetaData<>("-fx-show-thumbnails", BooleanConverter.getInstance(), DEFAULT_SHOW_THUMBNAILS) {

            @Override
            public boolean isSettable(PDFView control) {
                return !control.showThumbnails.isBound();
            }

            @Override
            public StyleableProperty<Boolean> getStyleableProperty(PDFView control) {
                return (StyleableProperty<Boolean>) control.showThumbnailsProperty();
            }
        };

        private static final CssMetaData<PDFView, Boolean> SHOW_TOOLBAR = new CssMetaData<>("-fx-show-toolbar", BooleanConverter.getInstance(), DEFAULT_SHOW_TOOLBAR) {

            @Override
            public boolean isSettable(PDFView control) {
                return !control.showToolBar.isBound();
            }

            @Override
            public StyleableProperty<Boolean> getStyleableProperty(PDFView control) {
                return (StyleableProperty<Boolean>) control.showToolBarProperty();
            }
        };

        private static final CssMetaData<PDFView, Boolean> SHOW_PRINT_BUTTON = new CssMetaData<>("-fx-show-print-button", BooleanConverter.getInstance(), DEFAULT_SHOW_PRINT_BUTTON) {

            @Override
            public boolean isSettable(PDFView control) {
                return !control.showPrintButton.isBound();
            }

            @Override
            public StyleableProperty<Boolean> getStyleableProperty(PDFView control) {
                return (StyleableProperty<Boolean>) control.showPrintButtonProperty();
            }
        };

        private static final CssMetaData<PDFView, Boolean> SHOW_SEARCH_RESULTS = new CssMetaData<>("-fx-show-search-results", BooleanConverter.getInstance(), DEFAULT_SHOW_SEARCH_RESULTS) {

            @Override
            public boolean isSettable(PDFView control) {
                return !control.showSearchResults.isBound();
            }

            @Override
            public StyleableProperty<Boolean> getStyleableProperty(PDFView control) {
                return (StyleableProperty<Boolean>) control.showSearchResultsProperty();
            }
        };

        private static final CssMetaData<PDFView, Boolean> SHOW_ALL = new CssMetaData<>("-fx-show-all", BooleanConverter.getInstance(), DEFAULT_SHOW_ALL) {

            @Override
            public boolean isSettable(PDFView control) {
                return !control.showAll.isBound();
            }

            @Override
            public StyleableProperty<Boolean> getStyleableProperty(PDFView control) {
                return (StyleableProperty<Boolean>) control.showAllProperty();
            }
        };

        private static final CssMetaData<PDFView, Number> THUMBNAIL_SIZE = new CssMetaData<>("-fx-thumbnail-size", SizeConverter.getInstance(), DEFAULT_THUMBNAIL_SIZE) {

            @Override
            public boolean isSettable(PDFView control) {
                return !control.thumbnailSize.isBound();
            }

            @Override
            public StyleableProperty<Number> getStyleableProperty(PDFView control) {
                return (StyleableProperty<Number>) control.thumbnailSizeProperty();
            }
        };

        private static final CssMetaData<PDFView, Color> SEARCH_RESULT_COLOR = new CssMetaData<>("-fx-search-result-color", ColorConverter.getInstance(), DEFAULT_SEARCH_RESULT_COLOR) {

            @Override
            public boolean isSettable(PDFView control) {
                return !control.searchResultColor.isBound();
            }

            @Override
            public StyleableProperty<Color> getStyleableProperty(PDFView control) {
                return (StyleableProperty<Color>) control.searchResultColorProperty();
            }
        };

        private static final CssMetaData<PDFView, Color> SELECTION_COLOR = new CssMetaData<>("-fx-selection-color", ColorConverter.getInstance(), DEFAULT_SELECTION_COLOR) {

            @Override
            public boolean isSettable(PDFView control) {
                return !control.selectionColor.isBound();
            }

            @Override
            public StyleableProperty<Color> getStyleableProperty(PDFView control) {
                return (StyleableProperty<Color>) control.selectionColorProperty();
            }
        };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            final List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(Control.getClassCssMetaData());
            Collections.addAll(styleables, SHOW_THUMBNAILS, SHOW_TOOLBAR, SHOW_PRINT_BUTTON, SHOW_SEARCH_RESULTS, SHOW_ALL, THUMBNAIL_SIZE, SEARCH_RESULT_COLOR, SELECTION_COLOR);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    @Override
    protected List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }
}
