package com.imaia.consumer.adapter;

/**
 * A send whose idempotency key was already used: the platform reports the earlier
 * attempt delivered the content. Handlers treat this as success — resending would
 * duplicate the message.
 */
public class DuplicateSendException extends RuntimeException {

    public DuplicateSendException(String message) {
        super(message);
    }
}
