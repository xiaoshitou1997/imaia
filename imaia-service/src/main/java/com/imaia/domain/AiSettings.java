package com.imaia.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Resolved view over the {@code ai_setting} KV table for one scope: global-only
 * keys (default prompt, context window, @-gate, quiet hours, timezone, master
 * switch) come from the global rows and ignore scoped rows; scoped keys
 * ({@code persona}, {@code autoreply}) resolve
 * conversation &gt; connection &gt; platform &gt; global &gt; code default.
 * Persistence lives in {@link AiSetting}; this plain value object is what the
 * reply pipeline and the WebUI read and write.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AiSettings {

    /** Resolved line persona; null = fall back to the default prompt. */
    private String persona;

    /** Line-level AI reply switch (default off). */
    private boolean autoreply;

    private String systemPrompt;

    private int historyLimit;

    private boolean requireGroupMention;

    private boolean quietHoursEnabled;

    private String quietHoursStart;

    private String quietHoursEnd;

    private String timezone;

    private boolean enabled;

    /** Web search endpoint base (Tavily-compatible); empty disables the tool. */
    private String webSearchBaseUrl;

    /** Web search API key; empty disables the tool. */
    private String webSearchApiKey;

    /** Image generation endpoint base (OpenAI images protocol); empty disables the tool. */
    private String imageBaseUrl;

    /** Image generation API key. */
    private String imageApiKey;

    /** Image generation model id; empty disables the tool. */
    private String imageModel;

    /** Video generation endpoint base (Agnes task protocol); empty disables the tool. */
    private String videoBaseUrl;

    /** Video generation API key. */
    private String videoApiKey;

    /** Video generation model id; empty disables the tool. */
    private String videoModel;

    private Instant updatedAt;
}
