package com.imaia.consumer.ai;

/** Volume gate for AI replies: per-thread per-minute, global hourly and daily caps. */
public interface ReplyRateLimiter {

    /** Whether a reply may be sent in the thread now; consuming a slot when it returns true. */
    boolean tryAcquire(String threadId);
}
