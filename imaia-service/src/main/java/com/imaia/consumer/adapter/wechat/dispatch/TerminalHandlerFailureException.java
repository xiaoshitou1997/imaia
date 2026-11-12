package com.imaia.consumer.adapter.wechat.dispatch;

/**
 * The same event cursor failed repeatedly. Continuing would replay it forever, so the
 * stream stops instead of looping; the failure is reported and the consumer exits
 * when exit-on-failure is enabled.
 */
public class TerminalHandlerFailureException extends RuntimeException {

    public TerminalHandlerFailureException(String cursor, int failures, Throwable cause) {
        super("Event cursor " + cursor + " failed " + failures
                + " consecutive times; stopping instead of replaying it forever.", cause);
    }
}
