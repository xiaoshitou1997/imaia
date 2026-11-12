package com.imaia.consumer.dispatch;

/**
 * The same event failed repeatedly. Continuing would replay it forever, so the
 * stream stops instead of looping; the failure is reported and the process exits
 * when the platform's exit-on-failure is enabled.
 */
public class TerminalHandlerFailureException extends RuntimeException {

    public TerminalHandlerFailureException(String receipt, int failures, Throwable cause) {
        super("Event " + receipt + " failed " + failures
                + " consecutive times; stopping instead of replaying it forever.", cause);
    }
}
