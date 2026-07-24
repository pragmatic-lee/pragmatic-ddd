package io.pragmatic.ddd.visual;

import io.pragmatic.ddd.base.PragmaticException;

public class VisualException extends PragmaticException {

    public VisualException(Throwable cause) {
        super(cause);
    }

    public VisualException(String message) {
        super(message);
    }

    public VisualException(String message, Throwable cause) {
        super(message, cause);
    }
}
