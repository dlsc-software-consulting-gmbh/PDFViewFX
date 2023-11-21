package com.dlsc.pdfviewfx.demo;

import javafx.application.Application;
import javafx.stage.Stage;

import java.util.Objects;

public class PDFViewAtlantaApp extends PDFViewApp {

    @Override
    public void start(Stage primaryStage) {
        super.start(primaryStage);

        pdfView.getStylesheets().setAll(Objects.requireNonNull(PDFViewAtlantaApp.class.getResource("/pdf-view-atlanta.css")).toExternalForm());
    }

    public static void main(String[] args) {
 //       Application.setUserAgentStylesheet(Objects.requireNonNull(PDFViewAtlantaApp.class.getResource("/nord-light.css")).toExternalForm());
       Application.setUserAgentStylesheet(Objects.requireNonNull(PDFViewAtlantaApp.class.getResource("/nord-dark.css")).toExternalForm());
        launch(args);
    }
}
