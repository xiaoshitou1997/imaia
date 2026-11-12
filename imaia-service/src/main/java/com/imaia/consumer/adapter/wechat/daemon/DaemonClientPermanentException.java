package com.imaia.consumer.adapter.wechat.daemon;

/**
 * A request the daemon rejected in a way that retrying cannot fix, such as an
 * authentication failure or a wrong identifier. Surfaced as a terminal error instead of
 * entering the retry loop.
 */
public class DaemonClientPermanentException extends RuntimeException {

    public DaemonClientPermanentException(String message, Throwable cause) {
        super(message, cause);
    }
}
