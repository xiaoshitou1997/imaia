package com.imaia.web;

import java.time.Instant;

/** 测试与控制器共用的时钟缝。 */
final class Clock {

    
    private Clock() {
    }

    static Instant now() {
        return Instant.now();
    }
}
