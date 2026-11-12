package com.imaia.consumer.ai;

import com.imaia.consumer.TestProperties;
import com.imaia.consumer.config.WechatAdapterProperties;
import com.imaia.consumer.config.ChatModelFactory;
import com.imaia.consumer.config.ModelRegistry;
import com.imaia.consumer.repository.ContactRepository;
import com.imaia.consumer.repository.ModelCallLogRepository;
import com.imaia.consumer.adapter.ImAdapter;
import com.imaia.consumer.adapter.wechat.daemon.DaemonClient;
import com.imaia.consumer.repository.AiMessageRepository;
import com.imaia.consumer.domain.AiMessage;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import com.imaia.consumer.repository.MemoryFactRepository;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chat orchestration against a simulated OpenAI-compatible endpoint: the system
 * prompt and persisted history travel with each call, per-thread history stays
 * separate, the persisted history is bounded, and [IMAGE: …] / [VIDEO: …] markers
 * are parsed out of the reply into generation prompts.
 */
class AiChatServiceTest {

    private MockWebServer server;
    private AiMessageRepository aiMessages;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
        aiMessages = Mockito.mock(AiMessageRepository.class);
        List<AiMessage> saved = new ArrayList<>();
        Mockito.when(aiMessages.saveAll(Mockito.anyList())).thenAnswer(inv -> {
            List<AiMessage> rows = inv.getArgument(0);
            saved.addAll(rows);
            return rows;
        });
        Mockito.when(aiMessages.findRecentContext(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(inv -> {
                    String account = inv.getArgument(0);
                    String thread = inv.getArgument(1);
                    int limit = ((org.springframework.data.domain.Pageable) inv.getArgument(2)).getPageSize();
                    return saved.stream()
                            .filter(row -> row.getAccountId().equals(account) && row.getThreadId().equals(thread))
                            .sorted(java.util.Comparator.comparing(AiMessage::getCreatedAt).reversed())
                            .limit(limit)
                            .toList()
                            .reversed();
                });
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    private AiChatService service(int historyLimit) {
        WechatAdapterProperties properties = TestProperties.wechat(server.url("/").toString(), "acc1");
        DaemonClient daemonClient = new DaemonClient(WebClient.create(), properties);
        ImAdapter adapter = Mockito.mock(ImAdapter.class);
        Mockito.when(adapter.accountId()).thenReturn("acc1");
        Mockito.when(adapter.platform()).thenReturn("wechat");
        MemoryFactRepository memoryFacts = Mockito.mock(MemoryFactRepository.class);
        Mockito.when(memoryFacts.findTop50ByAccountIdAndScopeTypeOrderByCreatedAtDesc(Mockito.any(), Mockito.any()))
                .thenReturn(java.util.List.of());
        ModelRegistry registry = TestProperties.modelRegistry(server.url("/").toString());
        ChatTools chatTools = new ChatTools(registry, new ImageGenerationClient(registry),
                new VideoGenerationClient(registry));
        return new AiChatService(new OpenAiChatCompletion(new ChatModelFactory(), registry,
                        Mockito.mock(ModelCallLogRepository.class)),
                aiMessages, memoryFacts, adapter,
                TestProperties.profileService(TestProperties.profile("test prompt", historyLimit,
                        "t1", true, false, 0, 0, 0)),
                chatTools,
                Mockito.mock(ContactRepository.class));
    }

    private static MockResponse gatewayReply(String content) {
        return new MockResponse().setBody("""
                {"id":"chatcmpl-1","object":"chat.completion","created":1794320000,"model":"chat-m","choices":[{"index":0,"message":{"role":"assistant","content":"%s"},"finish_reason":"stop"}]}
                """.formatted(content))
                .addHeader("Content-Type", "application/json");
    }

    @Test
    void sendsSystemPromptAndGrowsPerThreadHistory() throws Exception {
        AiChatService service = service(4);
        server.enqueue(gatewayReply("好呀"));
        server.enqueue(gatewayReply("在的"));

        assertThat(service.complete("t1", "你好").text()).isEqualTo("好呀");
        assertThat(service.complete("t1", "在吗").text()).isEqualTo("在的");

        RecordedRequest first = server.takeRequest();
        assertThat(first.getPath()).isEqualTo("/chat/completions");
        JsonNode firstBody = mapper.readTree(first.getBody().readUtf8());
        assertThat(firstBody.get("model").asString()).isEqualTo("chat-m");
        assertThat(firstBody.get("messages").size()).isEqualTo(2);
        assertThat(firstBody.get("messages").get(0).get("role").asString()).isEqualTo("system");
        assertThat(firstBody.get("messages").get(0).get("content").asString()).startsWith("test prompt");
        assertThat(firstBody.get("messages").get(1).get("role").asString()).isEqualTo("user");
        assertThat(firstBody.get("messages").get(1).get("content").asString()).isEqualTo("你好");

        RecordedRequest second = server.takeRequest();
        JsonNode secondBody = mapper.readTree(second.getBody().readUtf8());
        assertThat(secondBody.get("messages").size()).isEqualTo(4);
        assertThat(secondBody.get("messages").get(1).get("role").asString()).isEqualTo("user");
        assertThat(secondBody.get("messages").get(1).get("content").asString()).isEqualTo("你好");
        assertThat(secondBody.get("messages").get(2).get("role").asString()).isEqualTo("assistant");
        assertThat(secondBody.get("messages").get(2).get("content").asString()).isEqualTo("好呀");
        assertThat(secondBody.get("messages").get(3).get("role").asString()).isEqualTo("user");
        assertThat(secondBody.get("messages").get(3).get("content").asString()).isEqualTo("在吗");

        org.mockito.ArgumentCaptor<List<AiMessage>> savedRows =
                org.mockito.ArgumentCaptor.forClass(List.class);
        Mockito.verify(aiMessages, Mockito.times(2)).saveAll(savedRows.capture());
        assertThat(savedRows.getAllValues().get(0)).hasSize(2);
        assertThat(savedRows.getAllValues().get(1)).hasSize(2);
    }

    @Test
    void evictsOldestTurnsBeyondTheLimit() throws Exception {
        AiChatService service = service(2);
        server.enqueue(gatewayReply("一"));
        server.enqueue(gatewayReply("二"));
        server.enqueue(gatewayReply("三"));

        service.complete("t1", "一");
        service.complete("t1", "二");
        service.complete("t1", "三");

        server.takeRequest();
        server.takeRequest();
        JsonNode third = mapper.readTree(server.takeRequest().getBody().readUtf8());
        String body = third.toString();
        assertThat(body).contains("二").contains("三").doesNotContain("一");
    }

    @Test
    void keepsThreadsSeparate() throws Exception {
        AiChatService service = service(4);
        server.enqueue(gatewayReply("A"));
        server.enqueue(gatewayReply("B"));

        service.complete("t1", "对话一");
        service.complete("t2", "对话二");

        server.takeRequest();
        JsonNode body = mapper.readTree(server.takeRequest().getBody().readUtf8());
        assertThat(body.get("messages").size()).isEqualTo(2);
        assertThat(body.get("messages").get(1).get("content").asString()).isEqualTo("对话二");
        org.mockito.ArgumentCaptor<List<AiMessage>> savedRows =
                org.mockito.ArgumentCaptor.forClass(List.class);
        Mockito.verify(aiMessages, Mockito.times(2)).saveAll(savedRows.capture());
        assertThat(savedRows.getAllValues().stream().flatMap(List::stream).map(AiMessage::getThreadId))
                .containsExactly("t1", "t1", "t2", "t2");
    }

    @Test
    void parsesMediaMarkersOutOfTheReply() {
        server.enqueue(gatewayReply("好的，这就画 [IMAGE: 一只红色的猫] 再给你 [VIDEO: 猫在跑]"));

        AiChatService.ChatReply reply = service(4).complete("t1", "画一只猫");

        assertThat(reply.text()).isEqualTo("好的，这就画 再给你");
        assertThat(reply.imagePrompts()).containsExactly("一只红色的猫");
        assertThat(reply.videoPrompts()).containsExactly("猫在跑");
    }

    @Test
    void carriesVisionImageAsDataUri() throws Exception {
        AiChatService service = service(4);
        server.enqueue(gatewayReply("看到了"));

        service.complete("t1", "这是什么", new byte[]{1, 2, 3}, "image/jpeg");

        JsonNode body = mapper.readTree(server.takeRequest().getBody().readUtf8());
        JsonNode parts = body.get("messages").get(1).get("content");
        assertThat(parts.isArray()).isTrue();
        assertThat(parts.get(0).get("type").asString()).isEqualTo("text");
        assertThat(parts.get(1).get("type").asString()).isEqualTo("image_url");
        assertThat(parts.get(1).get("image_url").get("url").asString()).contains("data:image/jpeg;base64,");
    }
}
