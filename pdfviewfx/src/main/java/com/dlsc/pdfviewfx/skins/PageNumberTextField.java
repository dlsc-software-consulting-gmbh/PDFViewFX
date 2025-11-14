package com.dlsc.pdfviewfx.skins;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Pos;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.util.function.UnaryOperator;

public class PageNumberTextField extends TextField {

    public PageNumberTextField() {
        setAlignment(Pos.CENTER_RIGHT);

        UnaryOperator<TextFormatter.Change> filter = change -> {

            //if something got added
            if (change.isAdded()) {

                //if change is " add "" in texfield
                if (change.getText().equals("\"")) {

                    if (!change.getControlText().contains("\"")) {
                        change.setText("\"\"");
                        return change;
                    } else {
                        //if textfield already contains ""
                        return null;
                    }

                } else {
                    try {
                        String s = change.getControlNewText();
                        if (s.contains("\"")) {  // remove ", if present
                            s = s.replaceAll("[\"]", "");
                        }
                        int value = Integer.parseInt(s);
                        if (value < 1 || (value > getNumberOfPages() && value != 1)) {
                            return null;
                        }
                    } catch (NumberFormatException e) { // e.g. overflow, not a number, etc.
                        return null;
                    }
                }
            }

            return change;
        };

        setTextFormatter(new TextFormatter<>(filter));

        textProperty().addListener((observable, oldValue, newValue) -> {
            try {
                setValue(Integer.parseInt(newValue));
            } catch (NumberFormatException e) {
                // swallow exception
            }
        });
    }

    private final IntegerProperty value = new SimpleIntegerProperty(this, "value", 0);

    public final int getValue() {
        return value.get();
    }

    public final IntegerProperty valueProperty() {
        return value;
    }

    public final void setValue(int value) {
        this.value.set(value);
        setText("" + value);
    }

    private final IntegerProperty numberOfPages = new SimpleIntegerProperty(this, "numberOfPages", 0);

    public final int getNumberOfPages() {
        return numberOfPages.get();
    }

    public final IntegerProperty numberOfPagesProperty() {
        return numberOfPages;
    }

    public final void setNumberOfPages(int numberOfPages) {
        this.numberOfPages.set(numberOfPages);
    }
}