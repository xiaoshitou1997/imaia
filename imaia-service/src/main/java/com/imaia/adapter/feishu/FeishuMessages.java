package com.imaia.adapter.feishu;

import com.imaia.domain.MessageKind;
import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import com.imaia.config.JsonSupport;

/**
 * Pure mapping helpers from feishu message payloads to the normalized model:
 * msg_type → category, content JSON → display body and media keys. No SDK or
 * Spring types here, so the mapping is unit-testable in isolation.
 */
public final class FeishuMessages {

    private static final Logger log = LoggerFactory.getLogger(FeishuMessages.class);

    // 纯静态映射工具无构造注入点，经 JsonSupport 取容器装配的共享实例。

    private FeishuMessages() {
    }

    /** The feishu msg_type → normalized category the core logic switches on. */
    public static MessageKind categoryOf(String msgType) {
        return switch (msgType == null ? "" : msgType) {
            case "text" -> MessageKind.TEXT;
            case "image" -> MessageKind.IMAGE;
            case "audio" -> MessageKind.VOICE;
            case "media" -> MessageKind.VIDEO;
            case "sticker" -> MessageKind.STICKER;
            case "file" -> MessageKind.FILE;
            case "post", "interactive", "share_chat", "share_user" -> MessageKind.RICH;
            default -> MessageKind.OTHER;
        };
    }

    /** The displayable body of one message, or null for media-only types.
     * Text content carries {@code <at>} tags; they are stripped for the archive. */
    public static String extractBody(String msgType, String contentJson) {
        JsonNode content = parse(contentJson);
        if (content == null) {
            return null;
        }
        return switch (msgType == null ? "" : msgType) {
            case "text" -> stripAtTags(textField(content, "text"));
            case "post" -> textField(content, "title");
            default -> null;
        };
    }

    /** The media key of one category inside the content JSON, or null when absent. */
    public static String mediaKey(String msgType, String contentJson, MessageKind category) {
        JsonNode content = parse(contentJson);
        if (content == null) {
            return null;
        }
        return switch (category) {
            case IMAGE -> textField(content, "image_key");
            case VOICE, FILE, VIDEO -> textField(content, "file_key");
            default -> null;
        };
    }

    /** The cover image key of a video message, or null when absent. */
    public static String previewKey(String contentJson) {
        JsonNode content = parse(contentJson);
        return content == null ? null : textField(content, "image_key");
    }

    /**
     * Parses one feishu content JSON. Blank input is a legal absence (null);
     * present-but-malformed content is payload corruption — logged here and
     * returned as null so the archive keeps the rest of the message.
     */
    public static JsonNode parse(String contentJson) {
        if (!StringUtils.hasText(contentJson)) {
            return null;
        }
        try {
            return JsonSupport.mapper().readTree(contentJson);
        } catch (RuntimeException failure) {
            log.warn("Malformed feishu content JSON ({}): {}", failure.getMessage(), contentJson);
            return null;
        }
    }

    private static String textField(JsonNode content, String field) {
        JsonNode value = content.get(field);
        return value != null && value.isString() ? value.asString() : null;
    }

    /** Removes {@code <at ...>…</at>} tags and trims the leftovers. */
    static String stripAtTags(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("<at[^>]*>.*?</at>", "").replaceAll("<at[^>]*/>", "").trim();
    }
}
