package com.imaia.consumer.ai;

import com.imaia.consumer.TestProperties;
import com.imaia.consumer.domain.AssistantProfile;
import com.imaia.consumer.domain.RiskPolicy;
import com.imaia.consumer.config.ChatModelFactory;
import com.imaia.consumer.config.ModelRegistry;
import com.imaia.consumer.adapter.ImAdapter;
import com.imaia.consumer.adapter.ImAdapters;
import com.imaia.consumer.adapter.ImEvent;
import com.imaia.consumer.adapter.ProcessedMessageLedger;
import com.imaia.consumer.adapter.wechat.WechatConnection;
import com.imaia.consumer.adapter.wechat.daemon.DaemonClient;
import com.imaia.consumer.adapter.WechatAdapter;
import com.imaia.consumer.repository.MemoryFactRepository;
import com.imaia.consumer.repository.AiArtifactRepository;
import com.imaia.consumer.repository.ContactRepository;
import com.imaia.consumer.repository.ModelCallLogRepository;
import com.imaia.consumer.repository.ScheduledActionRepository;
import com.imaia.consumer.repository.ImMessageRepository;
import com.imaia.consumer.repository.AiMessageRepository;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end reply behaviour against simulated daemon, chat-endpoint and
 * image-endpoint routes: text, vision, generated images ([IMAGE: …]), the mention
 * and dedup gates, and the reply idempotency keys derived from the triggering
 * message id.
 */
class AiReplyHandlerTest {

    private static final byte[] FAKE_JPEG = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};

    private MockWebServer server;
    private AiMessageRepository aiMessages;
    private ProcessedMessageLedger ledger;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final Queue<String> sendKeys = new ConcurrentLinkedQueue<>();
    private final Queue<String> sendBodies = new ConcurrentLinkedQueue<>();
    private final Queue<String> gatewayPaths = new ConcurrentLinkedQueue<>();
    private final Queue<String> imageGenPaths = new ConcurrentLinkedQueue<>();
    private final Queue<String> gatewayReplies = new ConcurrentLinkedQueue<>();
    private final AtomicReference<String> messageJson = new AtomicReference<>("""
            {"id":"m1","accountId":"acc1","threadId":"t1","senderId":"wxid_a",
             "type":1,"direction":"incoming","body":"在吗"}
            """);
    private final AtomicReference<String> mediaListing = new AtomicReference<>("""
            {"assets":[{"id":"a1","kind":"image","variant":"standard","state":"available",
                        "contentType":"image/jpeg",
                        "contentUrl":"/v1/accounts/acc1/media/a1/content",
                        "preview":{"contentType":"image/jpeg",
                                   "contentUrl":"/v1/accounts/acc1/media/a1/preview"}}]}
            """);
    private final AtomicReference<String> gatewayReplyJson = new AtomicReference<>("""
            {"id":"chatcmpl-1","object":"chat.completion","created":1794320000,"model":"chat-m","choices":[{"index":0,"message":{"role":"assistant","content":"你好，我是AI助手"},"finish_reason":"stop"}]}
            """);

    @BeforeEach
    void startServer() throws IOException {
        aiMessages = Mockito.mock(AiMessageRepository.class);
        ledger = Mockito.mock(ProcessedMessageLedger.class);
        java.util.Set<String> handled = java.util.concurrent.ConcurrentHashMap.newKeySet();
        Mockito.when(ledger.hasProcessed(Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(inv -> !handled.add(inv.getArgument(1)));
        sendKeys.clear();
        sendBodies.clear();
        gatewayPaths.clear();
        imageGenPaths.clear();
        gatewayReplies.clear();
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path.startsWith("/images/generations")) {
                    imageGenPaths.add(path);
                    String b64 = Base64.getEncoder().encodeToString(FAKE_JPEG);
                    return json("{\"data\":[{\"b64_json\":\"" + b64 + "\",\"url\":null}]}");
                }
                if (path.startsWith("/v1/accounts/acc1/messages/m1/media-transfers")) {
                    return json("{\"id\":\"j1\",\"state\":\"queued\"}");
                }
                if (path.startsWith("/v1/accounts/acc1/jobs/j1")) {
                    return json("{\"state\":\"available\",\"id\":\"j1\"}");
                }
                if (path.startsWith("/v1/accounts/acc1/media/a1/preview")
                        || path.startsWith("/v1/accounts/acc1/media/a1/content")) {
                    Buffer buffer = new Buffer().write(FAKE_JPEG);
                    return new MockResponse().setBody(buffer)
                            .addHeader("Content-Type", "image/jpeg");
                }
                if (path.startsWith("/v1/accounts/acc1/messages/m1/media")) {
                    return json(mediaListing.get());
                }
                if (path.startsWith("/v1/accounts/acc1/messages/m1")) {
                    return json(messageJson.get());
                }
                if (path.startsWith("/v1/accounts/acc1/consumers/imaia/ack")) {
                    return new MockResponse().setResponseCode(200);
                }
                if (path.startsWith("/v1/accounts/acc1/uploads")) {
                    return json("{\"id\":\"src1\",\"name\":\"imaia-image.png\",\"mediaType\":\"image/png\"}");
                }
                if (path.startsWith("/v1/accounts/acc1/sends")) {
                    sendKeys.add(request.getHeader("Idempotency-Key"));
                    sendBodies.add(request.getBody().readUtf8());
                    return json("{\"id\":\"send1\"}");
                }
                if (path.startsWith("/chat/completions")) {
                    gatewayPaths.add(path);
                    String next = gatewayReplies.poll();
                    return json(next != null ? next : gatewayReplyJson.get());
                }
                imageGenPaths.add("UNMATCHED:" + request.getMethod() + " " + path);
                return new MockResponse().setResponseCode(404);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    private AiReplyHandler handler(String replyThreads) {
        return handlerWith(TestProperties.profile("test prompt", 4, replyThreads, true, false, 0, 0, 0),
                TestProperties.risk(6, 60, 100, "", ""), true);
    }

    private AiReplyHandler handlerWith(AssistantProfile profile, RiskPolicy risk, boolean imageEnabled) {
        WechatConnection connection = TestProperties.wechat(server.url("/").toString(), "acc1");
        ModelRegistry registry = imageEnabled
                ? TestProperties.modelRegistry(server.url("/").toString())
                : TestProperties.modelRegistryWithout(server.url("/").toString(),
                        ModelRegistry.PROTOCOL_IMAGES);
        WebClient webClient = WebClient.builder().baseUrl(server.url("/").toString()).build();
        DaemonClient daemonClient = new DaemonClient(webClient, connection);
        ImAdapter adapter = new WechatAdapter(daemonClient, ledger);
        MemoryFactRepository memoryFacts = Mockito.mock(MemoryFactRepository.class);
        Mockito.when(memoryFacts.findTop50ByAccountIdAndScopeTypeOrderByCreatedAtDesc(
                        Mockito.any(), Mockito.any())).thenReturn(List.of());
        ImMessageRepository archivedMessages = Mockito.mock(ImMessageRepository.class);
        Mockito.when(archivedMessages.findTop200ByAccountIdAndThreadIdOrderByArchivedAtDesc(
                        Mockito.any(), Mockito.any())).thenReturn(List.of());
        ChatTools chatTools = new ChatTools(registry, new ImageGenerationClient(registry),
                new VideoGenerationClient(registry));
        AiChatService chatService = new AiChatService(
                new OpenAiChatCompletion(new ChatModelFactory(), registry,
                        Mockito.mock(ModelCallLogRepository.class)),
                aiMessages, memoryFacts, TestProperties.profileService(profile), chatTools,
                Mockito.mock(ContactRepository.class));
        InMemoryRateLimiter limiter = InMemoryRateLimiter.ofLimits(
                risk.getRepliesPerThreadPerMinute(), risk.getMaxRepliesPerHour(), risk.getMaxRepliesPerDay());
        return new AiReplyHandler(new ImAdapters(List.of(adapter)), chatService, limiter,
                noneProvider(), new ImageGenerationClient(registry), new VideoGenerationClient(registry),
                new AudioSpeechClient(registry), registry, Mockito.mock(PersonAnalyzer.class),
                Mockito.mock(ScheduledActionRepository.class),
                Mockito.mock(AiArtifactRepository.class), memoryFacts, archivedMessages,
                TestProperties.profileService(profile), TestProperties.riskService(risk));
    }

    private static ObjectProvider<AsrTranscriber> noneProvider() {
        return new ObjectProvider<>() {
            @Override
            public AsrTranscriber getObject() {
                throw new java.util.NoSuchElementException("no ASR transcriber configured");
            }

            @Override
            public AsrTranscriber getIfAvailable() {
                return null;
            }
        };
    }

    private ImEvent event(String threadId) {
        return new ImEvent("wechat", "message.committed", "m1", threadId, "1",
                mapper.readTree("{\"messageId\":\"m1\",\"threadId\":\"" + threadId + "\"}"));
    }

    @Test
    void repliesToAllowedIncomingTextWithMessageDerivedKey() {
        handler("t1").handle(event("t1"));

        assertThat(gatewayPaths.size()).isEqualTo(1);
        assertThat(sendKeys.poll()).isEqualTo("imaia-ai-m1");
        String body = sendBodies.poll();
        assertThat(body).contains("\"threadId\":\"t1\"").contains("你好，我是AI助手");
    }

    @Test
    void ignoresUnlistedThreads() {
        handler("t1").handle(event("t9"));

        assertThat(gatewayPaths.size()).isEqualTo(0);
        assertThat(sendKeys.size()).isEqualTo(0);
    }

    @Test
    void ignoresOutgoingMessages() {
        messageJson.set("""
                {"id":"m1","accountId":"acc1","threadId":"t1","type":1,
                 "direction":"outgoing","body":"我自己发的"}
                """);
        handler("t1").handle(event("t1"));

        assertThat(gatewayPaths.size()).isEqualTo(0);
        assertThat(sendKeys.size()).isEqualTo(0);
    }

    @Test
    void ignoresUnsupportedLinkMessages() {
        messageJson.set("""
                {"id":"m1","accountId":"acc1","threadId":"t1","type":49,
                 "direction":"incoming"}
                """);
        handler("t1").handle(event("t1"));

        assertThat(gatewayPaths.size()).isEqualTo(0);
        assertThat(sendKeys.size()).isEqualTo(0);
    }

    @Test
    void repliesToImageThroughVision() {
        messageJson.set("""
                {"id":"m1","accountId":"acc1","threadId":"t1","senderId":"wxid_a",
                 "type":3,"direction":"incoming"}
                """);
        handler("t1").handle(event("t1"));

        assertThat(gatewayPaths.size()).isEqualTo(1);
        assertThat(sendKeys.poll()).isEqualTo("imaia-ai-m1");
        assertThat(sendBodies.poll()).contains("你好，我是AI助手");
    }

    @Test
    void visionRequestCarriesBase64Image() throws Exception {
        messageJson.set("""
                {"id":"m1","accountId":"acc1","threadId":"t1","senderId":"wxid_a",
                 "type":3,"direction":"incoming"}
                """);
        handler("t1").handle(event("t1"));

        String body = takeGatewayRequestBody();
        assertThat(body).contains("\"type\":\"image_url\"").contains("data:image/jpeg;base64,");
    }

    @Test
    void voiceWithoutAsrStaysSilent() {
        messageJson.set("""
                {"id":"m1","accountId":"acc1","threadId":"t1","senderId":"wxid_a",
                 "type":34,"direction":"incoming"}
                """);
        handler("t1").handle(event("t1"));

        assertThat(gatewayPaths.size()).isEqualTo(0);
        assertThat(sendKeys.size()).isEqualTo(0);
    }

    @Test
    void repliesToStickerThroughPreview() {
        messageJson.set("""
                {"id":"m1","accountId":"acc1","threadId":"t1","senderId":"wxid_a",
                 "type":47,"direction":"incoming"}
                """);
        handler("t1").handle(event("t1"));

        assertThat(gatewayPaths.size()).isEqualTo(1);
        assertThat(sendKeys.poll()).isEqualTo("imaia-ai-m1");
    }

    @Test
    void replayStaysSilentAfterProcessedRecord() {
        AiReplyHandler handler = handler("t1");

        handler.handle(event("t1"));
        handler.handle(event("t1"));

        assertThat(gatewayPaths.size()).isEqualTo(1);
        assertThat(sendKeys.size()).isEqualTo(1);
    }

    @Test
    void generatesAndSendsImageWhenModelAsksForIt() {
        gatewayReplyJson.set("""
                {"id":"chatcmpl-2","object":"chat.completion","created":1794320001,"model":"chat-m","choices":[{"index":0,"message":{"role":"assistant","content":"好的，这就画 [IMAGE: 一只红色的猫]"},"finish_reason":"stop"}]}
                """);
        handler("t1").handle(event("t1"));

        assertThat(imageGenPaths.size()).isEqualTo(1);
        assertThat(sendKeys.poll()).isEqualTo("imaia-ai-m1");
        assertThat(sendKeys.poll()).isEqualTo("imaia-ai-m1-img1");
        String textBody = sendBodies.poll();
        String imageBody = sendBodies.poll();
        assertThat(textBody).doesNotContain("[IMAGE:");
        assertThat(imageBody).contains("\"kind\":\"image\"").contains("\"sourceId\":\"src1\"");
    }

    @Test
    void nativeToolCallGeneratesAndSendsImage() {
        gatewayReplies.add("""
                {"id":"chatcmpl-3","object":"chat.completion","created":1794320003,"model":"chat-m","choices":[{"index":0,"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"generate_image","arguments":"{\\"prompt\\":\\"一只红色的猫\\"}"}}]},"finish_reason":"tool_calls"}]}
                """);
        gatewayReplies.add("""
                {"id":"chatcmpl-4","object":"chat.completion","created":1794320004,"model":"chat-m","choices":[{"index":0,"message":{"role":"assistant","content":"画好了"},"finish_reason":"stop"}]}
                """);
        handler("t1").handle(event("t1"));

        assertThat(gatewayPaths.size()).isEqualTo(2);
        assertThat(imageGenPaths.size()).isEqualTo(1);
        assertThat(sendKeys.poll()).isEqualTo("imaia-ai-m1");
        assertThat(sendBodies.poll()).contains("画好了");
        assertThat(sendKeys.poll()).isEqualTo("imaia-ai-m1-gen1");
        assertThat(sendBodies.poll()).contains("\"kind\":\"image\"").contains("\"sourceId\":\"src1\"");
    }

    @Test
    void missingImageToolKeepsTextAndDropsPrompt() {
        gatewayReplyJson.set("""
                {"id":"chatcmpl-3","object":"chat.completion","created":1794320002,"model":"chat-m","choices":[{"index":0,"message":{"role":"assistant","content":"画不了 [IMAGE: 猫]"},"finish_reason":"stop"}]}
                """);
        handlerWith(TestProperties.profile("test prompt", 4, "t1", true, false, 0, 0, 0), TestProperties.risk(6, 60, 100, "", ""), false)
                .handle(event("t1"));

        assertThat(imageGenPaths.size()).isEqualTo(0);
        assertThat(sendKeys.poll()).isEqualTo("imaia-ai-m1");
        assertThat(sendBodies.poll()).doesNotContain("[IMAGE:");
    }

    @Test
    void repliesInGroupWhenMentioned() {
        messageJson.set("""
                {"id":"m1","accountId":"acc1","threadId":"123@chatroom","senderId":"wxid_a",
                 "type":1,"direction":"incoming","body":"帮我看看",
                 "mentions":{"state":"known","userIds":["wxid_me"],"self":true,"all":false,
                             "source":"msgsource.atuserlist"}}
                """);
        handler("123@chatroom").handle(event("123@chatroom"));

        assertThat(gatewayPaths.size()).isEqualTo(1);
        assertThat(sendKeys.poll()).isEqualTo("imaia-ai-m1");
    }

    @Test
    void skipsGroupMessageWithoutMention() {
        messageJson.set("""
                {"id":"m1","accountId":"acc1","threadId":"123@chatroom","senderId":"wxid_a",
                 "type":1,"direction":"incoming","body":"闲聊一句",
                 "mentions":{"state":"known","userIds":[],"self":false,"all":false,
                             "source":"msgsource.atuserlist"}}
                """);
        handler("123@chatroom").handle(event("123@chatroom"));

        assertThat(gatewayPaths.size()).isEqualTo(0);
        assertThat(sendKeys.size()).isEqualTo(0);
    }

    @Test
    void capsRepliesPerThreadPerMinute() {
        AiReplyHandler limited = handlerWith(TestProperties.profile("test prompt", 4, "t1", true, false, 0, 0, 0), TestProperties.risk(1, 60, 100, "", ""), true);

        limited.handle(event("t1"));
        limited.handle(event("t1"));

        assertThat(gatewayPaths.size()).isEqualTo(1);
        assertThat(sendKeys.size()).isEqualTo(1);
    }

    @Test
    void delaysBeforeSending() {
        AiReplyHandler delayed = handlerWith(TestProperties.profile("test prompt", 4, "t1", true, false, 0, 0, 40), TestProperties.risk(6, 60, 100, "", ""), true);

        long start = System.currentTimeMillis();
        delayed.handle(event("t1"));

        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(40);
        assertThat(sendKeys.size()).isEqualTo(1);
    }

    @Test
    void quietWindowCoversNormalAndWrappedRanges() {
        AiReplyHandler wrapped = handlerWith(TestProperties.profile("test prompt", 4, "t1", true, false, 0, 0, 0), TestProperties.risk(6, 60, 100, "23:00", "07:30"), true);
        assertThat(wrapped.isQuietHours(java.time.LocalTime.of(23, 30))).isTrue();
        assertThat(wrapped.isQuietHours(java.time.LocalTime.of(3, 0))).isTrue();
        assertThat(wrapped.isQuietHours(java.time.LocalTime.of(12, 0))).isFalse();

        AiReplyHandler disabled = handlerWith(TestProperties.profile("test prompt", 4, "t1", true, false, 0, 0, 0), TestProperties.risk(6, 60, 100, "", ""), true);
        assertThat(disabled.isQuietHours(java.time.LocalTime.of(3, 0))).isFalse();
    }

    private String takeGatewayRequestBody() throws InterruptedException {
        java.util.List<String> seen = new java.util.ArrayList<>();
        for (int i = 0; i < 400; i++) {
            RecordedRequest request = server.takeRequest(25, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (request == null) continue;
            String path = request.getPath();
            seen.add(path);
            if (path.startsWith("/chat/completions")) {
                return request.getBody().readUtf8();
            }
        }
        throw new IllegalStateException("no /chat/completions request; saw: " + seen);
    }

    private static MockResponse json(String body) {
        return new MockResponse().setBody(body).addHeader("Content-Type", "application/json");
    }
}
