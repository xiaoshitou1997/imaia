package com.imaia.adapter.feishu;

import com.imaia.domain.Platform;

import com.imaia.adapter.ImAdapter;
import com.imaia.adapter.ImContractException;
import com.imaia.adapter.IngestLedger;
import com.imaia.domain.ImAccount;
import com.imaia.domain.MediaKind;
import com.imaia.domain.MessageKind;
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
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.util.StringUtils;
import java.util.Map;

/**
 * The feishu platform adapter over the official oapi-sdk: WS long-connection event
 * intake, im/v1 message fetch/send with uuid idempotency, and media up/download via
 * message resources — all normalized behind the {@link ImAdapter} contract, so the
 * core pipeline never sees feishu details.
 */
@Component
public class FeishuAdapter implements ImAdapter {


    /** Feishu chat ids (group conversations) carry the {@code oc_} prefix. */
    private static final String GROUP_THREAD_PREFIX = "oc_";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final FeishuConnection connection;
    private final IngestLedger ledger;
    /** AI 发送产生的平台消息 id（有界队列）：归档时精确回标 origin=ai。 */
    private final Deque<String> recentAiContextPlatformMessageIds = new ArrayDeque<>();

    /** 归档与 AI 回复两条链对同一消息各拉一次 SDK；有界 memo 消除重复网络调用（origin 归因在 memo 外）。 */
    private static final int FETCH_API_MEMO_MAX = 128;
    private final Map<String, Message> fetchApiMemo = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Message> eldest) {
                    return size() > FETCH_API_MEMO_MAX;
                }
            });

    public FeishuAdapter(FeishuConnection connection, IngestLedger ledger) {
        this.connection = connection;
        this.ledger = ledger;
    }

    @Override
    public Platform platform() {
        return Platform.FEISHU;
    }

    @Override
    public ImAccount account() {
        return connection.row();
    }

    @Override
    public boolean configured() {
        return connection.enabled();
    }

    @Override
    public FetchedMessage fetchMessage(String platformMessageId) {
        Message message = memoizedFetchApiMessage(platformMessageId);
        if (!platformMessageId.equals(message.getMessageId()) || blank(message.getChatId())) {
            throw new ImContractException("Message identity drift: expected " + Platform.FEISHU + "/"
                    + platformMessageId + " but the fetched message is " + Platform.FEISHU + "/"
                    + message.getMessageId() + " in chat " + message.getChatId() + ".");
        }
        String msgType = message.getMsgType();
        String content = message.getBody() == null ? null : message.getBody().getContent();
        MessageKind category = FeishuMessages.categoryOf(msgType);
        boolean mentioned = message.getMentions() != null && message.getMentions().length > 0;
        String senderPlatformId = message.getSender() == null ? null : message.getSender().getId();
        String origin = rememberAiOrigin(platformMessageId);
        return new FetchedMessage(message.getChatId(), platformMessageId,
                senderPlatformId, null, "incoming", null, category, mentioned, origin,
                FeishuMessages.extractBody(msgType, content), rawJson(msgType, content, message.getMentions()),
                parseMillis(message.getCreateTime()));
    }

    /** 发送成功时记录平台消息 id；归档命中即标 origin=ai。 */
    private String rememberAiOrigin(String platformMessageId) {
        boolean matched = recentAiContextPlatformMessageIds.remove(platformMessageId);
        return matched ? "ai" : "human";
    }

    @Override
    public boolean hasProcessed(String platformMessageId) {
        return ledger.hasProcessed(account().getId(), platformMessageId);
    }

    @Override
    public void markProcessed(String platformMessageId) {
        ledger.markProcessed(account().getId(), platformMessageId);
    }

    @Override
    public List<AttachmentRef> attachments(String platformMessageId, MessageKind category) {
        if (category != MessageKind.IMAGE && category != MessageKind.VOICE
                && category != MessageKind.VIDEO && category != MessageKind.STICKER
                && category != MessageKind.FILE) {
            return List.of();
        }
        Message message = fetchApiMessage(platformMessageId);
        String content = message.getBody() == null ? null : message.getBody().getContent();
        String fileKey = FeishuMessages.mediaKey(message.getMsgType(), content, category);
        if (fileKey == null) {
            return List.of();
        }
        return List.of(new AttachmentRef(attachmentKind(category), fileKey,
                mediaMime(category), null, null));
    }

    private static com.imaia.domain.AttachmentKind attachmentKind(MessageKind category) {
        return switch (category) {
            case IMAGE -> com.imaia.domain.AttachmentKind.IMAGE;
            case VOICE -> com.imaia.domain.AttachmentKind.VOICE;
            case VIDEO -> com.imaia.domain.AttachmentKind.VIDEO;
            case STICKER -> com.imaia.domain.AttachmentKind.STICKER;
            default -> com.imaia.domain.AttachmentKind.FILE;
        };
    }

    @Override
    public MediaBytes downloadAsset(String platformMessageId, MessageKind category) {
        Message message = fetchApiMessage(platformMessageId);
        String content = message.getBody() == null ? null : message.getBody().getContent();
        String fileKey = FeishuMessages.mediaKey(message.getMsgType(), content, category);
        if (fileKey == null) {
            return null;
        }
        return downloadResource(platformMessageId, fileKey, category == MessageKind.IMAGE ? "image" : "file",
                mediaMime(category));
    }

    @Override
    public MediaBytes downloadPreview(String platformMessageId) {
        Message message = fetchApiMessage(platformMessageId);
        String content = message.getBody() == null ? null : message.getBody().getContent();
        String imageKey = FeishuMessages.previewKey(content);
        if (imageKey == null) {
            return null;
        }
        return downloadResource(platformMessageId, imageKey, "image", "image/jpeg");
    }

    @Override
    public String sendText(String conversationPlatformId, String text, String idempotencyKey) {
        String sentMessageId = createMessage(conversationPlatformId, "text", json(Map.of("text", text)), idempotencyKey);
        recordAiContextMessageId(sentMessageId);
        return sentMessageId;
    }

    @Override
    public String sendMedia(String conversationPlatformId, MediaKind kind, String storageRef, String idempotencyKey) {
        String sentMessageId;
        if (kind == MediaKind.IMAGE) {
            sentMessageId = createMessage(conversationPlatformId, "image", json(Map.of("image_key", storageRef)), idempotencyKey);
        } else {
            // voice/video ride as file messages on feishu (audio bubbles need opus
            // uploads with duration metadata — a later iteration)
            sentMessageId = createMessage(conversationPlatformId, "file", json(Map.of("file_key", storageRef)), idempotencyKey);
        }
        recordAiContextMessageId(sentMessageId);
        return sentMessageId;
    }

    /** 有界记录：AI 发送产生的平台消息 id，归档时精确回标 origin=ai。 */
    private void recordAiContextMessageId(String platformMessageId) {
        if (platformMessageId == null) {
            return;
        }
        if (recentAiContextPlatformMessageIds.size() >= 256) {
            recentAiContextPlatformMessageIds.pollFirst();
        }
        recentAiContextPlatformMessageIds.addLast(platformMessageId);
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
    public boolean isGroupConversation(String conversationPlatformId) {
        return conversationPlatformId != null && conversationPlatformId.startsWith(GROUP_THREAD_PREFIX);
    }

    /** Sends one message into a chat; the uuid (idempotency key) dedupes replays server-side.
     * Returns the platform message id of the sent message. */
    private String createMessage(String conversationPlatformId, String msgType, String content, String idempotencyKey) {
        CreateMessageReq request = CreateMessageReq.newBuilder()
                .receiveIdType("chat_id")
                .createMessageReqBody(CreateMessageReqBody.newBuilder()
                        .receiveId(conversationPlatformId)
                        .msgType(msgType)
                        .content(content)
                        .uuid(idempotencyKey)
                        .build())
                .build();
        try {
            var response = client().im().v1().message().create(request);
            requireSuccess(response.success(), response.getCode(), response.getMsg(),
                    "send " + msgType + " to " + conversationPlatformId);
            return response.getData() == null ? null : response.getData().getMessageId();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new FeishuApiException("Send to " + conversationPlatformId + " interrupted.", interrupted);
        } catch (Exception failure) {
            throw new FeishuApiException("Send to " + conversationPlatformId + " failed: " + failure.getMessage(), failure);
        }
    }

    /** The SDK message of one id, typed and identity-checked. */
    /** 有界 memo：同一条消息 id 在窗口内复用同一份 SDK 结果；origin 归因等副作用保持在 memo 外。 */
    private Message memoizedFetchApiMessage(String platformMessageId) {
        Message cached = fetchApiMemo.get(platformMessageId);
        if (cached != null) {
            return cached;
        }
        Message message = fetchApiMessage(platformMessageId);
        fetchApiMemo.put(platformMessageId, message);
        return message;
    }

    private Message fetchApiMessage(String platformMessageId) {
        GetMessageResp response;
        try {
            response = client().im().v1().message().get(GetMessageReq.newBuilder()
                    .messageId(platformMessageId)
                    .build());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new FeishuApiException("Fetch of message " + platformMessageId + " interrupted.", interrupted);
        } catch (Exception failure) {
            throw new FeishuApiException("Fetch of message " + platformMessageId + " failed: " + failure.getMessage(), failure);
        }
        requireSuccess(response.success(), response.getCode(), response.getMsg(), "get message " + platformMessageId);
        Message[] items = response.getData() == null ? null : response.getData().getItems();
        if (items == null || items.length == 0) {
            throw new ImContractException("Feishu message " + platformMessageId + " is gone or unreadable.");
        }
        return items[0];
    }

    /** Downloads one message resource (image/file); empty responses yield null. */
    private MediaBytes downloadResource(String platformMessageId, String fileKey, String type, String mime) {
        GetMessageResourceResp response;
        try {
            response = client().im().v1().messageResource().get(GetMessageResourceReq.newBuilder()
                    .messageId(platformMessageId)
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
    private static String mediaMime(MessageKind category) {
        return switch (category) {
            case IMAGE -> "image/jpeg";
            case VOICE -> "audio/ogg";
            case VIDEO -> "video/mp4";
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
        if (!StringUtils.hasText(epochMillis)) {
            return Instant.now();
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(epochMillis.trim()));
        } catch (NumberFormatException failure) {
            return Instant.now();
        }
    }

    private static boolean blank(String value) {
        return !StringUtils.hasText(value);
    }
}
