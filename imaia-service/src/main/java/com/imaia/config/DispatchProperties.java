package com.imaia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Event-dispatch behavior shared by every platform stream. */
@ConfigurationProperties("imaia.dispatch")
public record DispatchProperties(int handlerFailureThreshold) {
}
