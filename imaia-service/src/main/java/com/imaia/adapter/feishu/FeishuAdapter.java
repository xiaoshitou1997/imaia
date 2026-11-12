package com.imaia.adapter.feishu;

import com.imaia.adapter.ImAdapter;
import com.imaia.adapter.ImContractException;
import com.imaia.adapter.ProcessedMessageLedger;
import com.imaia.domain.ImMessage;
import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.model.CreateFileReq;
import com.lark.oapi.service.im.v1.model.CreateFileReqBody;
import com.lark.oapi.service.im.v1.model.CreateImageReq;
import com.lark.oapi.service.im.v1.model.CreateImageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.GetMessageReq;
import com.lark.oapi.service.im.v1.model.GetMessageResourceReq;
import com.lark.oapi.service.im.v1.model.GetMessageResourceResp;
import com.lark.oapi.service.im.v1.model.GetMessageResp;
import com.lark.oapi.service.im.v1.model.Mention;
import com.lark.oapi.service.im.v1.model.Message;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The feishu platform adapter over the official oapi-sdk: WS long-connection event
 * intake, im/v1 message fetch/send with uuid idempotency, and media up/download via
 * message resources — all normalized behind the {@link ImAdapter} contract, so the
 * core pipeline never sees feishu details.
 */
@Component
public class FeishuAdapter implements ImAdapter {

    public static final String PLATFORM = "feishu";

    /** Feishu chat ids (group conversations) carry the {@code oc_} prefix. */
    private static final String GROUP_THREAD_PREFIX = "oc_";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final FeishuConnection connection;
    private final ProcessedMessageLedger ledger;

    public FeishuAdapter(FeishuConnection connection, ProcessedMessageLedger ledger) {
        this.connection = connection;
        this.ledger = ledger;
    }

    @Override
    public String platform() {
        return PLATFORM;
    }

    @Override
    public String accountId() {
        return connection.config().appId();
    }

    @Override
    public ImMessage fetchMessage(String messageId) {
        Message message = fetchApiMessage(messageId);
        if (!messageId.equals(message.getMessageId()) || blank(message.getChatId())) {
            throw new ImContractException("Message identity drift: expected " + PLATFORM + "/"
                    + messageId + " but the fetched message is " + PLATFORM + "/"
                    + message.getMessageId() + " in chat " + message.getChatId() + ".");
        }
        String msgType = message.getMsgType();
        String content = message.getBody() == null ? null : message.getBody().getContent();
        String category = FeishuMessages.categoryOf(msgType);
        boolean mentioned = message.getMentions() != null && message.getMentions().length > 0;
        String senderId = message.getSender() == null ? null : message.getSender().getId();
        ImMessage imMessage = new ImMessage(accountId(), PLATFORM, message.getChatId(), messageId,
                senderId, null, "incoming", null, category, mentioned,
                FeishuMessages.extractBody(msgType, content), parseMillis(message.getCreateTime()));
        imMessage.setRaw(rawJson(msgType, content, message.getMentions()));
        return imMessage;
    }

    @Override
    public boolean hasProcessed(String messageId) {
        return ledger.hasProcessed(PLATFORM, messageId);
    }

    @Override
    public void markProcessed(String messageId, String threadId) {
        ledger.markProcessed(PLATFORM, messageId, threadId);
    }

    @Override
    public MediaBytes downloadAsset(String messageId, String category) {
        Message message = fetchApiMessage(messageId);
        String content = message.getBody() == null ? null : message.getBody().getContent();
        String fileKey = FeishuMessages.mediaKey(message.getMsgType(), content, category);
        if (fileKey == null) {
            return null;
        }
        return downloadResource(messageId, fileKey, "image".equals(category) ? "image" : "file",
                mediaMime(category));
    }

    @Override
    public MediaBytes downloadPreview(String messageId) {
        Message message = fetchApiMessage(messageId);
        String content = message.getBody() == null ? null : message.getBody().getContent();
        String imageKey = FeishuMessages.previewKey(content);
        if (imageKey == null) {
            return null;
        }
        return downloadResource(messageId, imageKey, "image", "image/jpeg");
    }

    @Override
    public void sendText(String threadId, String text, String idempotencyKey) {
        createMessage(threadId, "text", json(Map.of("text", text)), idempotencyKey);
    }

    @Override
    public void sendMedia(String threadId, String kind, String storageRef, String idempotencyKey) {
        if ("image".equals(kind)) {
            createMessage(threadId, "image", json(Map.of("image_key", storageRef)), idempotencyKey);
        } else {
            // voice/video ride as file messages on feishu (audio bubbles need opus
            // uploads with duration metadata — a later iteration)
            createMessage(threadId, "file", json(Map.of("file_key", storageRef)), idempotencyKey);
        }
    }

    @Override
    public String uploadMedia(byte[] data, String filename, String mimeType) {
        Path temp = null;
        try {
            temp = Files.createTempFile("imaia-upload", suffix(filename));
            Files.write(temp, data);
            if (mimeType != null && mimeType.startsWith("image/")) {
                CreateImageReq request = CreateImageReq.newBuilder()
                        .createImageReqBody(CreateImageReqBody.newBuilder()
                                .imageType("message")
                                .image(temp.toFile())
                                .build())
                        .build();
                var response = client().im().v1().image().create(request);
                requireSuccess(response.success(), response.getCode(), response.getMsg(), "upload image " + filename);
                return response.getData().getImageKey();
            }
            CreateFileReq request = CreateFileReq.newBuilder()
                    .createFileReqBody(CreateFileReqBody.newBuilder()
                            .fileType(fileKind(filename))
                            .fileName(filename)
                            .file(temp.toFile())
                            .build())
                    .build();
            var response = client().im().v1().file().create(request);
            requireSuccess(response.success(), response.getCode(), response.getMsg(), "upload file " + filename);
            return response.getData().getFileKey();
        } catch (IOException failure) {
            throw new FeishuApiException("Temp file for upload " + filename + " failed.", failure);
        } catch (Exception failure) {
            if (failure instanceof FeishuApiException apiFailure) {
                throw apiFailure;
            }
            throw new FeishuApiException("Upload of " + filename + " failed: " + failure.getMessage(), failure);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // temp cleanup is best-effort
                }
            }
        }
    }

    @Override
    public boolean isGroupThread(String threadId) {
        return threadId != null && threadId.startsWith(GROUP_THREAD_PREFIX);
    }

    /** Sends one message into a chat; the uuid (idempotency key) dedupes replays server-side. */
    private void createMessage(String threadId, String msgType, String content, String idempotencyKey) {
        CreateMessageReq request = CreateMessageReq.newBuilder()
                .receiveIdType("chat_id")
                .createMessageReqBody(CreateMessageReqBody.newBuilder()
                        .receiveId(threadId)
                        .msgType(msgType)
                        .content(content)
                        .uuid(idempotencyKey)
                        .build())
                .build();
        try {
            var response = client().im().v1().message().create(request);
            requireSuccess(response.success(), response.getCode(), response.getMsg(),
                    "send " + msgType + " to " + threadId);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new FeishuApiException("Send to " + threadId + " interrupted.", interrupted);
        } catch (Exception failure) {
            throw new FeishuApiException("Send to " + threadId + " failed: " + failure.getMessage(), failure);
        }
    }

    /** The SDK message of one id, typed and identity-checked. */
    private Message fetchApiMessage(String messageId) {
        GetMessageResp response;
        try {
            response = client().im().v1().message().get(GetMessageReq.newBuilder()
                    .messageId(messageId)
                    .build());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new FeishuApiException("Fetch of message " + messageId + " interrupted.", interrupted);
        } catch (Exception failure) {
            throw new FeishuApiException("Fetch of message " + messageId + " failed: " + failure.getMessage(), failure);
        }
        requireSuccess(response.success(), response.getCode(), response.getMsg(), "get message " + messageId);
        Message[] items = response.getData() == null ? null : response.getData().getItems();
        if (items == null || items.length == 0) {
            throw new ImContractException("Feishu message " + messageId + " is gone or unreadable.");
        }
        return items[0];
    }

    /** Downloads one message resource (image/file); empty responses yield null. */
    private MediaBytes downloadResource(String messageId, String fileKey, String type, String mime) {
        GetMessageResourceResp response;
        try {
            response = client().im().v1().messageResource().get(GetMessageResourceReq.newBuilder()
                    .messageId(messageId)
                    .fileKey(fileKey)
                    .type(type)
                    .build());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new FeishuApiException("Download of " + fileKey + " interrupted.", interrupted);
        } catch (Exception failure) {
            throw new FeishuApiException("Download of " + fileKey + " failed: " + failure.getMessage(), failure);
        }
        requireSuccess(response.success(), response.getCode(), response.getMsg(),
                "download " + type + " " + fileKey);
        java.io.ByteArrayOutputStream stream = response.getData();
        if (stream == null || stream.size() == 0) {
            return null;
        }
        return new MediaBytes(stream.toByteArray(), mime);
    }

    private void requireSuccess(boolean success, int code, String msg, String what) {
        if (!success) {
            throw new FeishuApiException("Feishu API " + what + " failed: code=" + code + " msg=" + msg);
        }
    }

    private Client client() {
        return connection.client();
    }

    /** A conservative mime per category; the platform does not expose the real one. */
    private static String mediaMime(String category) {
        return switch (category == null ? "" : category) {
            case "image" -> "image/jpeg";
            case "voice" -> "audio/ogg";
            case "video" -> "video/mp4";
            default -> "application/octet-stream";
        };
    }

    /** The file-type bucket feishu uploads want, derived from the filename. */
    private static String fileKind(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".opus")) {
            return "opus";
        }
        if (lower.endsWith(".mp4")) {
            return "mp4";
        }
        if (lower.endsWith(".pdf")) {
            return "pdf";
        }
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) {
            return "doc";
        }
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) {
            return "xls";
        }
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) {
            return "ppt";
        }
        return "stream";
    }

    private static String suffix(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        return dot < 0 ? ".bin" : filename.substring(dot);
    }

    private static String json(Object payload) {
        return MAPPER.writeValueAsString(payload);
    }

    /** The original payload snapshot kept on the archive row for later platform-specific reads. */
    private static String rawJson(String msgType, String content, Mention[] mentions) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("msgType", msgType);
        raw.put("content", FeishuMessages.parse(content));
        if (mentions != null && mentions.length > 0) {
            List<Map<String, String>> names = new ArrayList<>();
            for (Mention mention : mentions) {
                names.add(Map.of("key", mention.getKey() == null ? "" : mention.getKey(),
                        "name", mention.getName() == null ? "" : mention.getName()));
            }
            raw.put("mentions", names);
        }
        return MAPPER.writeValueAsString(raw);
    }

    private static Instant parseMillis(String epochMillis) {
        if (epochMillis == null || epochMillis.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(epochMillis.trim()));
        } catch (NumberFormatException failure) {
            return Instant.now();
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
