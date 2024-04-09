module com.dlsc.pdfviewfx.demo {
    requires com.dlsc.pdfviewfx;
    requires org.scenicview.scenicview;
    requires javafx.web; // due to scenic view
    requires javafx.fxml; // due to scenic view
    requires fr.brouillard.oss.cssfx;
    exports com.dlsc.pdfviewfx.demo;
}