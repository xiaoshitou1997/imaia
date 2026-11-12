package com.imaia.adapter.wechat.sse;

/**
 * The daemon rejected the resume cursor; the stream must be rebuilt from the live tail
 * and handlers get a chance to refresh their in-memory views.
 */
public class CursorExpiredException extends RuntimeException {

    public CursorExpiredException(String message) {
        super(message);
    }
}
