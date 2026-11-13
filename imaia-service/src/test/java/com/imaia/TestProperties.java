package com.imaia;

import com.imaia.adapter.wechat.WechatAccountConfig;
import com.imaia.adapter.wechat.WechatConnection;
import com.imaia.ai.AssistantSettingsService;
import com.imaia.config.DispatchProperties;
import com.imaia.config.ModelRegistry;
import com.imaia.domain.AssistantSettings;
import com.imaia.domain.ImConnection;
import com.imaia.domain.ImConversation;
import com.imaia.domain.Model;
import com.imaia.domain.Provider;
import com.imaia.repository.AssistantSettingsRepository;
import com.imaia.repository.ModelRepository;
import com.imaia.repository.ProviderRepository;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/** Test fixture: entity builders and stubbed-repository services. */
public final class TestProperties {

    private static final AtomicLong IDS = new AtomicLong();
    private static final char QC = '"';

    private TestProperties() {
    }

    public static WechatConnection wechat(String baseUrl, String account) {
        return WechatConnection.forTesting(wechatConfig(baseUrl, account));
    }

    public static WechatAccountConfig wechatConfig(String baseUrl, String account) {
        return new WechatAccountConfig(baseUrl, "test-token", account, 1000, 5,
                "imaia", true, 10, 50, 200, false);
    }

    public static ImConnection connection(long id, String platform) {
        ImConnection connection = new ImConnection(platform, "default", true, "{}");
        connection.setId(id);
        return connection;
    }

    public static ImConversation imConversation(long id, String externalId) {
        ImConversation conversation = new ImConversation(1L, externalId, null,
                ImConversation.KIND_DIRECT, Instant.now(), Instant.now(), Instant.now());
        conversation.setId(id);
        return conversation;
    }

    public static DispatchProperties dispatch() {
        return new DispatchProperties(3);
    }

    public static Provider provider(String name, String baseUrl) {
        Provider provider = new Provider(name, baseUrl, "key", Instant.now());
        provider.setId(IDS.incrementAndGet());
        return provider;
    }

    public static Model model(Provider provider, String modelId, String name, String protocol,
            List<String> input, List<String> output, Integer maxOutputTokens,
            Map<String, String> params) {
        return new Model(provider.getId(), modelId, name, protocol, 8192,
                json(input), json(output), maxOutputTokens, json(params), Instant.now());
    }

    public static ModelRegistry registry(Provider provider, Model... models) {
        return registry(List.of(provider), List.of(models));
    }

    public static ModelRegistry registry(List<Provider> providers, List<Model> models) {
        ProviderRepository providerRepository = Mockito.mock(ProviderRepository.class);
        ModelRepository modelRepository = Mockito.mock(ModelRepository.class);
        Mockito.when(providerRepository.findByEnabledTrue()).thenReturn(providers);
        Mockito.when(modelRepository.findByEnabledTrue()).thenReturn(models);
        return new ModelRegistry(providerRepository, modelRepository);
    }

    /** The default test directory: agnes image/video, zhipu chat. Voice is cut in v0.2. */
    public static ModelRegistry modelRegistry(String baseUrl) {
        Provider agnes = provider("agnes", baseUrl);
        Provider zhipu = provider("zhipu", baseUrl);
        return registry(List.of(agnes, zhipu), List.of(
                model(agnes, "img-m", "Agnes Image 2.5 Flash", "openai-images",
                        List.of("text"), List.of("image"), null, Map.of("size", "1024x768")),
                model(agnes, "vid-m", "Agnes Video 2.5 Flash", "openai-video",
                        List.of("text"), List.of("video"), null,
                        Map.of("seconds", "5", "aspect-ratio", "16:9")),
                model(zhipu, "chat-m", "GLM-4.5V", "openai-completions",
                        List.of("text", "image"), List.of("text"), 4096, Map.of())));
    }

    /** The default directory minus every model of one protocol. */
    public static ModelRegistry modelRegistryWithout(String baseUrl, String protocol) {
        Provider agnes = provider("agnes", baseUrl);
        Provider zhipu = provider("zhipu", baseUrl);
        List<Model> all = List.of(
                model(agnes, "img-m", "Agnes Image 2.5 Flash", "openai-images",
                        List.of("text"), List.of("image"), null, Map.of("size", "1024x768")),
                model(agnes, "vid-m", "Agnes Video 2.5 Flash", "openai-video",
                        List.of("text"), List.of("video"), null,
                        Map.of("seconds", "5", "aspect-ratio", "16:9")),
                model(zhipu, "chat-m", "GLM-4.5V", "openai-completions",
                        List.of("text", "image"), List.of("text"), 4096, Map.of()));
        return registry(List.of(agnes, zhipu),
                all.stream().filter(model -> !protocol.equals(model.getProtocol())).toList());
    }

    public static AssistantSettings settings(String systemPrompt, int historyLimit,
            boolean requireGroupMention, boolean quietHoursEnabled, String quietHoursStart,
            String quietHoursEnd) {
        return new AssistantSettings(systemPrompt, historyLimit, requireGroupMention,
                quietHoursEnabled, quietHoursStart, quietHoursEnd, true);
    }

    public static AssistantSettingsService settingsService(AssistantSettings settings) {
        AssistantSettingsRepository repository = Mockito.mock(AssistantSettingsRepository.class);
        Mockito.when(repository.findById(1)).thenReturn(java.util.Optional.of(settings));
        Mockito.when(repository.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
        return new AssistantSettingsService(repository);
    }

    public static String json(List<String> list) {
        String inner = list.stream().map(value -> QC + value + QC).collect(Collectors.joining(","));
        return "[" + inner + "]";
    }

    public static String json(Map<String, String> map) {
        String inner = map.entrySet().stream()
                .map(entry -> QC + entry.getKey() + QC + ':' + QC + entry.getValue() + QC)
                .collect(Collectors.joining(","));
        return "{" + inner + "}";
    }
}
