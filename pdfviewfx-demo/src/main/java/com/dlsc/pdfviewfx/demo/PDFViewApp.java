package com.dlsc.pdfviewfx.demo;

import com.dlsc.pdfviewfx.PDFView;
import fr.brouillard.oss.cssfx.CSSFX;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;

import java.awt.GraphicsEnvironment;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;

import javax.swing.SwingUtilities;
import java.io.File;
import java.util.Objects;

public class PDFViewApp extends Application {

    static {
        if (Boolean.getBoolean("atlantafx")) {
            setUserAgentStylesheet(Objects.requireNonNull(PDFViewApp.class.getResource("/nord-dark.css")).toExternalForm());
        }
    }

    private FileChooser chooser;

    protected final PDFView pdfView = new PDFView();

    @Override
    public void start(Stage primaryStage) {
        GraphicsEnvironment.getLocalGraphicsEnvironment();
        MenuItem loadItem = new MenuItem("Load PDF...");
        loadItem.setAccelerator(KeyCombination.valueOf("SHORTCUT+o"));
        loadItem.setOnAction(evt -> {
            if (chooser == null) {
                chooser = new FileChooser();
                chooser.setTitle("Load PDF File");
                final ExtensionFilter filter = new ExtensionFilter("PDF Files", "*.pdf");
                chooser.getExtensionFilters().add(filter);
                chooser.setSelectedExtensionFilter(filter);
            }

            final File file = chooser.showOpenDialog(pdfView.getScene().getWindow());
            if (file != null) {
                pdfView.load(file);
            }
        });

        try {
            pdfView.load(getClass().getResourceAsStream("/tesla3-owners-manual-short.pdf"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        MenuItem closeItem = new MenuItem("Close PDF");
        closeItem.setAccelerator(KeyCombination.valueOf("SHORTCUT+c"));
        closeItem.setOnAction(evt -> pdfView.unload());
        closeItem.disableProperty().bind(Bindings.isNull(pdfView.documentProperty()));

        MenuItem printItem = new MenuItem("Print PDF...");
        printItem.setAccelerator(KeyCombination.valueOf("SHORTCUT+p"));
        printItem.setOnAction(evt -> {
            SwingUtilities.invokeLater(() -> {
                PDFView.Document pdfDoc = pdfView.getDocument();
                if (pdfDoc != null) {
                    PrinterJob job = PrinterJob.getPrinterJob();
                    job.setPageable(pdfDoc.getPageable());
                    if (job.printDialog()) {
                        try {
                            job.print();
                        } catch (PrinterException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        });
        printItem.disableProperty().bind(Bindings.isNull(pdfView.documentProperty()));

        Menu fileMenu = new Menu("File");
        ObservableList<MenuItem> fileMenuItems = fileMenu.getItems();
        fileMenuItems.add(loadItem);
        fileMenuItems.add(closeItem);
        fileMenuItems.add(new SeparatorMenuItem());
        fileMenuItems.add(printItem);

        MenuBar menuBar = new MenuBar(fileMenu);
        menuBar.setUseSystemMenuBar(false);

        VBox.setVgrow(pdfView, Priority.ALWAYS);
        VBox box = new VBox(menuBar, pdfView);
        box.setFillWidth(true);

        if (Boolean.getBoolean("atlantafx")) {
            pdfView.getStylesheets().setAll(Objects.requireNonNull(PDFViewApp.class.getResource("/pdf-view-atlanta.css")).toExternalForm());
        }

        Scene scene = new Scene(box);

        CSSFX.start(primaryStage);

        primaryStage.setTitle("PDF View");
        primaryStage.setWidth(1000);
        primaryStage.setHeight(900);
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
        primaryStage.show();
    }
}
