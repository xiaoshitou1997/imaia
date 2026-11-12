package com.imaia.consumer.adapter.feishu;

import tools.jackson.databind.JsonNode;
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
    public static String categoryOf(String msgType) {
        return switch (msgType == null ? "" : msgType) {
            case "text" -> "text";
            case "image" -> "image";
            case "audio" -> "voice";
            case "media" -> "video";
            case "sticker" -> "sticker";
            case "file" -> "file";
            case "post", "interactive", "share_chat", "share_user" -> "rich";
            default -> "other";
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
    public static String mediaKey(String msgType, String contentJson, String category) {
        JsonNode content = parse(contentJson);
        if (content == null) {
            return null;
        }
        return switch (category) {
            case "image" -> textField(content, "image_key");
            case "voice", "file" -> textField(content, "file_key");
            case "video" -> textField(content, "file_key");
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
        if (contentJson == null || contentJson.isBlank()) {
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
