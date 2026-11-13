package com.imaia.adapter.feishu;

import com.imaia.domain.MessageCategory;
import tools.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pure mapping helpers from feishu message payloads to the normalized model:
 * msg_type → category, content JSON → display body and media keys. No SDK or
 * Spring types here, so the mapping is unit-testable in isolation.
 */
public final class FeishuMessages {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private FeishuMessages() {
    }

    /** The feishu msg_type → normalized category the core logic switches on. */
    public static MessageCategory categoryOf(String msgType) {
        return switch (msgType == null ? "" : msgType) {
            case "text" -> MessageCategory.TEXT;
            case "image" -> MessageCategory.IMAGE;
            case "audio" -> MessageCategory.VOICE;
            case "media" -> MessageCategory.VIDEO;
            case "sticker" -> MessageCategory.STICKER;
            case "file" -> MessageCategory.FILE;
            case "post", "interactive", "share_chat", "share_user" -> MessageCategory.RICH;
            default -> MessageCategory.OTHER;
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
    public static String mediaKey(String msgType, String contentJson, MessageCategory category) {
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

    /** Parses one feishu content JSON; malformed payloads yield null (logged upstream). */
    public static JsonNode parse(String contentJson) {
        if (!StringUtils.hasText(contentJson)) {
            return null;
        }
        try {
            return MAPPER.readTree(contentJson);
        } catch (RuntimeException failure) {
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
