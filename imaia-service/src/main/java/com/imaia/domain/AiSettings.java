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
 * ({@code persona}, {@code autoreply}, {@code self_command_alias},
 * {@code self_command_enabled}) resolve
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

    /**
     * 自指令别名（scoped）：本人 outgoing 消息手打「@别名 内容」即触发 AI 在该线
     * 回复；null/空 = 该线未配置别名（不继承，见 {@link #selfCommandEnabled}）。
     * 与 mention 元数据无关，判据是发送者方向。
     */
    private String selfCommandAlias;

    /**
     * 自指令开关（scoped，默认开）：与别名分开承载，才能表达「在别的层配了别名、
     * 但本会话要关掉」——只靠清空别名只能回落到外层，无法关闭。别名与开关同时
     * 就绪才生效。
     */
    private boolean selfCommandEnabled;

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
