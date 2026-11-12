package com.imaia.adapter.feishu;

/**
 * A feishu OpenAPI call failed. Replayed by the dispatcher with backoff until it
 * succeeds or the poison threshold stops the stream; auth-level faults surface the
 * same way with their code in the message.
 */
public class FeishuApiException extends RuntimeException {

    public FeishuApiException(String message) {
        super(message);
    }

    public FeishuApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
