package com.imaia.adapter.wechat.daemon.dto;

import com.imaia.adapter.wechat.daemon.DaemonContractException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * The saved message as the daemon returns it. Identity fields are validated; remaining
 * type-specific detail stays available through {@link #raw()} because the daemon keeps
 * adding message types.
 */
public record Message(String id, String accountId, String threadId, String serverId,
        String senderId, int type, String direction, String body, String createdAt,
        Mentions mentions, JsonNode raw) {

    /**
     * Mention metadata of the message. {@code state} is {@code known} when the mention
     * list was read and {@code unknown} when it is missing or could not be parsed; the
     * flags are null in the unknown case.
     */
    public record Mentions(String state, List<String> userIds, Boolean self, Boolean all, String source) {

        /** Whether the message mentions this account or everyone, per the daemon's metadata. */
        public boolean isReplyWorthyMention() {
            return "known".equals(state) && (Boolean.TRUE.equals(self) || Boolean.TRUE.equals(all));
        }

        private static Mentions fromJson(JsonNode node) {
            if (node == null || !node.isObject()) {
                return new Mentions("unknown", List.of(), null, null, null);
            }
            JsonNode state = node.get("state");
            List<String> userIds = new ArrayList<>();
            JsonNode ids = node.get("userIds");
            if (ids != null && ids.isArray()) {
                for (JsonNode id : ids) {
                    if (id.isString()) {
                        userIds.add(id.asString());
                    }
                }
            }
            return new Mentions(
                    state != null && state.isString() && !state.asString().isBlank()
                            ? state.asString() : "unknown",
                    List.copyOf(userIds),
                    nullableBoolean(node, "self"),
                    nullableBoolean(node, "all"),
                    optionalText(node, "source"));
        }

        private static Boolean nullableBoolean(JsonNode node, String field) {
            JsonNode value = node.get(field);
            return value != null && value.isBoolean() ? value.asBoolean() : null;
        }
    }

    public static Message fromJson(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new DaemonContractException("Message payload is not a JSON object.");
        }
        return new Message(
                requiredText(node, "id"),
                requiredText(node, "accountId"),
                requiredText(node, "threadId"),
                optionalText(node, "serverId"),
                optionalText(node, "senderId"),
                requiredInt(node, "type"),
                optionalText(node, "direction"),
                optionalText(node, "body"),
                optionalText(node, "createdAt"),
                Mentions.fromJson(node.get("mentions")),
                node);
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new DaemonContractException("Message field '" + field + "' is missing or not a non-empty string.");
        }
        return value.asString();
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw new DaemonContractException("Message field '" + field + "' is present but not a string.");
        }
        return value.asString();
    }

    private static int requiredInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new DaemonContractException("Message field '" + field + "' is missing or not a number.");
        }
        return value.asInt();
    }
}
