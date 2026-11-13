package com.imaia.web;

/** 测试与控制器共用的时钟缝。 */
final class Clock {

    private Clock() {
    }

    static java.time.Instant now() {
        return java.time.Instant.now();
    }
}
