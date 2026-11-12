package com.imaia.ai;

/** Volume gate for AI replies: per-thread per-minute, plus hourly and daily caps,
 * all scoped per platform (risk control is a per-platform-account concern). */
public interface ReplyRateLimiter {

    /** Whether a reply may be sent in the thread now; consuming a slot when it returns true. */
    boolean tryAcquire(String platform, String conversationId);
}
