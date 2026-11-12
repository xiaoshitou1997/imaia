package com.imaia.adapter.wechat.daemon.dto;

/** Body of a plain text send. The daemon requires an Idempotency-Key header alongside it. */
public record TextSendRequest(String kind, String threadId, String text) {
}
