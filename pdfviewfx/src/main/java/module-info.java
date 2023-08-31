module com.dlsc.pdfviewfx {
    requires transitive javafx.controls;
    requires transitive java.desktop;

    requires javafx.swing;

    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.materialdesign;

    requires com.dlsc.unitfx;

    requires java.logging;

    requires org.apache.pdfbox;
    requires org.apache.pdfbox.io;
    requires org.apache.commons.lang3;
    requires org.controlsfx.controls;

    exports com.dlsc.pdfviewfx;
}