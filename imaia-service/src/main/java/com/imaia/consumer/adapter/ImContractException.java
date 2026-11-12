package com.imaia.consumer.adapter;

/**
 * A platform payload violated the normalized contract the adapters guarantee to the
 * core (missing identity fields, drifted message identity). Replayed like any stream
 * error; a contract failure is never silently skipped.
 */
public class ImContractException extends RuntimeException {

    public ImContractException(String message) {
        super(message);
    }

    public ImContractException(String message, Throwable cause) {
        super(message, cause);
    }
}
