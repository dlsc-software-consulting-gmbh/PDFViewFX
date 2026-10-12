package com.dlsc.pdfviewfx;

import javafx.geometry.Rectangle2D;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.Objects;

/**
 * A comment or text markup added to a document, see {@link PDFView#getAnnotations()}.
 *
 * @param type       the kind of annotation
 * @param pageNumber the number of the page the annotation belongs to (zero-based)
 * @param markers    the areas the annotation refers to, in the coordinate space of {@link Selection#getMarker()}:
 *                   the marked text (one rectangle per line, at least one) for the text markup types, the bounds
 *                   of the note icon for {@link Type#NOTE}
 * @param contents   the text of the annotation, i.e. the comment, the replacement text or the text to insert,
 *                   may be null
 * @param color      the color of the annotation, null for the default color of its type
 */
public record Annotation(Type type, int pageNumber, List<Rectangle2D> markers, String contents, Color color) {

    /**
     * The kinds of annotations that can be added to a document.
     */
    public enum Type {

        /**
         * Highlights the marked text, optionally with a comment.
         */
        HIGHLIGHT,

        /**
         * Strikes out the marked text.
         */
        STRIKE_OUT,

        /**
         * Strikes out the marked text and suggests {@link Annotation#contents()} as its replacement.
         */
        REPLACE,

        /**
         * Suggests inserting {@link Annotation#contents()} after the marked text.
         */
        INSERT,

        /**
         * A sticky note showing {@link Annotation#contents()}, placed at the marker.
         */
        NOTE
    }

    public Annotation {
        Objects.requireNonNull(type, "type can not be null");
        markers = List.copyOf(markers);
    }

    /**
     * Creates an annotation in the default color of its type.
     */
    public Annotation(Type type, int pageNumber, List<Rectangle2D> markers, String contents) {
        this(type, pageNumber, markers, contents, null);
    }
}
