package com.imaia.adapter.wechat.daemon;

import com.imaia.adapter.wechat.WechatConnection;
import com.imaia.adapter.wechat.daemon.dto.EventAckRequest;
import com.imaia.adapter.wechat.daemon.dto.Message;
import com.imaia.adapter.wechat.daemon.dto.SendReceipt;
import com.imaia.adapter.wechat.daemon.dto.TextSendRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.web.util.UriBuilder;

/**
 * Blocking client for the daemon's account-scoped HTTP API. Handlers run on a bounded
 * scheduler, so every call blocks with a per-call timeout; the long-lived event stream
 * never goes through this class.
 */
@Component
public class DaemonClient {

    private final WebClient daemonWebClient;
    private final WechatConnection connection;
    private final Object accountLock = new Object();
    private volatile String accountId;

    public DaemonClient(WebClient daemonWebClient, WechatConnection connection) {
        this.daemonWebClient = daemonWebClient;
        this.connection = connection;
    }

    /**
     * The account id from configuration, or the first account the token can read.
     * Resolved once and cached for the lifetime of the consumer.
     */
    public String accountId() {
        String resolved = accountId;
        if (resolved == null) {
            synchronized (accountLock) {
                if (accountId == null) {
                    accountId = resolveAccountId();
                }
                resolved = accountId;
            }
        }
        return resolved;
    }

    private String resolveAccountId() {
        String configured = connection.config().account();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        JsonNode response = getJson(builder -> builder.path("/v1/accounts").build(), "accounts");
        JsonNode items = response.path("items");
        if (!items.isArray() || items.isEmpty()) {
            throw new DaemonContractException(
                    "The token can read no WeChat account; create one first with 'wx accounts create'.");
        }
        JsonNode firstId = items.get(0).get("id");
        if (firstId == null || !firstId.isString() || firstId.asString().isBlank()) {
            throw new DaemonContractException("The first account entry has no usable id.");
        }
        return firstId.asString();
    }

    /** Reads one saved message. */
    public Message getMessage(String messageId) {
        JsonNode body = getJson(
                builder -> builder.path("/v1/accounts/{accountId}/messages/{messageId}")
                        .build(accountId(), messageId),
                "message " + messageId);
        return Message.fromJson(body);
    }

    /**
     * Records how far this consumer has handled the event stream on the daemon. This is
     * consumer progress only; it never marks chats read.
     */
    public void ackCursor(String consumerName, String cursor) {
        blocking(daemonWebClient.post()
                .uri(builder -> builder.path("/v1/accounts/{accountId}/consumers/{consumer}/ack")
                        .build(accountId(), consumerName))
                .bodyValue(new EventAckRequest(cursor))
                .retrieve()
                .toBodilessEntity());
    }

    /** Sends text with a fresh idempotency key. */
    public SendReceipt sendText(String threadId, String text) {
        return sendText(threadId, text, UUID.randomUUID().toString());
    }

    /**
     * Sends text with a caller-provided idempotency key. Repeating a key returns the
     * original send instead of sending twice; an uncertain outcome must be looked up,
     * never resent under a new key.
     */
    public SendReceipt sendText(String threadId, String text, String idempotencyKey) {
        JsonNode body = blocking(daemonWebClient.post()
                .uri(builder -> builder.path("/v1/accounts/{accountId}/sends").build(accountId()))
                .header("Idempotency-Key", idempotencyKey)
                .bodyValue(new TextSendRequest("text", threadId, text))
                .retrieve()
                .bodyToMono(JsonNode.class));
        return new SendReceipt(idempotencyKey, body);
    }

    private JsonNode getJson(Function<UriBuilder, URI> uri, String what) {
        try {
            JsonNode body = blocking(daemonWebClient.get().uri(uri).retrieve().bodyToMono(JsonNode.class));
            if (body == null) {
                throw new DaemonContractException("Empty response for " + what + ".");
            }
            return body;
        } catch (WebClientResponseException failure) {
            throw translate(failure, what);
        }
    }

    private RuntimeException translate(WebClientResponseException failure, String what) {
        HttpStatusCode status = failure.getStatusCode();
        if (status.is4xxClientError() && status.value() != 408 && status.value() != 429) {
            return new DaemonClientPermanentException("Request for " + what
                    + " failed permanently with HTTP " + status.value()
                    + "; check the token, its scopes and the identifiers used.", failure);
        }
        return failure;
    }

    /**
     * Lists the media assets of one message. Parsing stays lenient because the
     * listing wraps assets in an object or a bare array depending on version.
     */
    public JsonNode getMediaAssets(String messageId) {
        JsonNode body = getJson(
                builder -> builder.path("/v1/accounts/{accountId}/messages/{messageId}/media")
                        .build(accountId(), messageId),
                "media of message " + messageId);
        JsonNode array = body.isArray() ? body
                : body.get("assets") != null ? body.get("assets")
                : body.get("items") != null ? body.get("items")
                : null;
        if (array == null || !array.isArray()) {
            throw new DaemonContractException("Media listing of message " + messageId + " has no asset array.");
        }
        return array;
    }

    /** Downloads the bytes behind a daemon media path such as /v1/accounts/…/content. */
    public MediaBytes getBytesByPath(String path) {
        try {
            org.springframework.http.ResponseEntity<byte[]> response = blocking(daemonWebClient.get()
                    .uri(path)
                    .retrieve()
                    .toEntity(byte[].class));
            byte[] data = response.getBody();
            if (data == null || data.length == 0) {
                throw new DaemonContractException("Media content at " + path + " is empty.");
            }
            String contentType = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
            return new MediaBytes(data, contentType == null ? "application/octet-stream" : contentType);
        } catch (WebClientResponseException failure) {
            throw translate(failure, "media content " + path);
        }
    }

    /**
     * Triggers a daemon download for one asset and waits until the transfer job
     * finishes, bounded by a fixed budget. One request per call, never a resend.
     */
    public void requestTransferAndWait(String messageId, String assetId) {
        JsonNode job = blocking(daemonWebClient.post()
                .uri(builder -> builder.path("/v1/accounts/{accountId}/messages/{messageId}/media-transfers")
                        .build(accountId(), messageId))
                .bodyValue(new MediaTransferRequest(messageId, assetId, "best"))
                .retrieve()
                .bodyToMono(JsonNode.class));
        JsonNode jobId = job == null ? null : job.get("id");
        if (jobId == null || !jobId.isString() || jobId.asString().isBlank()) {
            throw new DaemonContractException("Media transfer of message " + messageId + " returned no job id.");
        }
        String id = jobId.asString();
        long deadline = System.currentTimeMillis() + TRANSFER_WAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(TRANSFER_POLL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new DaemonContractException("Media transfer wait interrupted.", interrupted);
            }
            JsonNode jobStateNode = getJson(
                    builder -> builder.path("/v1/accounts/{accountId}/jobs/{jobId}").build(accountId(), id),
                    "job " + id);
            String jobState = jobStateNode.path("state").isString() ? jobStateNode.path("state").asString() : "";
            if ("available".equals(jobState) || "completed".equals(jobState)) {
                return;
            }
            if ("failed".equals(jobState) || "cancelled".equals(jobState)) {
                throw new DaemonContractException("Media transfer job " + id + " ended with state " + jobState + ".");
            }
        }
        throw new DaemonContractException("Media transfer job " + id + " did not finish within the wait budget.");
    }

    /** The reply-flow media payload: raw bytes and their content type. */
    public record MediaBytes(byte[] data, String contentType) {
    }

    /** Body of a media transfer request; the daemon fills in the rest. */
    public record MediaTransferRequest(String messageId, String assetId, String quality) {
    }

    private static final long TRANSFER_WAIT_MILLIS = 30_000;
    private static final long TRANSFER_POLL_MILLIS = 700;

    /** Stages one file with the daemon (multipart) and returns its media source id. */
    public String uploadMedia(byte[] data, String filename, String mimeType) {
        org.springframework.http.client.MultipartBodyBuilder builder =
                new org.springframework.http.client.MultipartBodyBuilder();
        builder.part("file", new org.springframework.core.io.ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return filename;
            }
        }).contentType(org.springframework.http.MediaType.valueOf(mimeType));
        JsonNode body = blocking(daemonWebClient.post()
                .uri(uriBuilder -> uriBuilder.path("/v1/accounts/{accountId}/uploads").build(accountId()))
                .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                .body(org.springframework.web.reactive.function.BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(JsonNode.class));
        JsonNode id = body == null ? null : body.get("id");
        if (id == null || !id.isString() || id.asString().isBlank()) {
            throw new DaemonContractException("Upload of " + filename + " returned no source id.");
        }
        return id.asString();
    }

    /** Sends one staged media source to the chat under an idempotency key. */
    public SendReceipt sendMedia(String threadId, String kind, String sourceId, String idempotencyKey) {
        JsonNode body = blocking(daemonWebClient.post()
                .uri(builder -> builder.path("/v1/accounts/{accountId}/sends").build(accountId()))
                .header("Idempotency-Key", idempotencyKey)
                .bodyValue(new MediaSendRequest(threadId, kind, sourceId, null, null))
                .retrieve()
                .bodyToMono(JsonNode.class));
        return new SendReceipt(idempotencyKey, body);
    }

    /** Body of a media send: one staged source plus optional alternate copies. */
    public record MediaSendRequest(String threadId, String kind, String sourceId,
            String standardSourceId, String thumbnailSourceId) {
    }

    private <T> T blocking(Mono<T> call) {
        return call.timeout(Duration.ofSeconds(connection.config().callTimeoutSeconds())).block();
    }
}
