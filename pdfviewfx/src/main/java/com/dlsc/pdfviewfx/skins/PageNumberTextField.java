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
                    //If Input is not a number don't change anything
                    if (change.getText().matches("[^0-9]")) {
                        return null;
                    }

                    //If change don't contains " check if change is in range
                    if (!change.getControlText().contains("\"")) {
                        if (Integer.parseInt(change.getControlNewText()) < 1 || Integer.parseInt(change.getControlNewText()) > getNumberOfPages()) {
                            return null;
                        }
                    } else {
                        //if change contains "" remove "" and check if is in range
                        String s = change.getControlNewText();

                        s = s.replaceAll("[\"]", "");
                        int value = Integer.parseInt(s);

                        if (value < 1 || value > getNumberOfPages()) {
                            return null;
                        }
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