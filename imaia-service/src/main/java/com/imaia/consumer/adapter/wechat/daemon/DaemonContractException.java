package com.imaia.consumer.adapter.wechat.daemon;

/**
 * The daemon sent a payload that does not satisfy its documented contract. Retried as a
 * stream error; a contract failure is never silently skipped.
 */
public class DaemonContractException extends RuntimeException {

    public DaemonContractException(String message) {
        super(message);
    }

    public DaemonContractException(String message, Throwable cause) {
        super(message, cause);
    }
}
