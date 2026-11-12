package com.imaia.consumer.adapter.wechat.daemon.dto;

/** Acknowledgement of the last event cursor a named consumer has handled. */
public record EventAckRequest(String cursor) {
}
