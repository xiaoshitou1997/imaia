package com.imaia;

import com.imaia.adapter.wechat.WechatAccountConfig;
import com.imaia.adapter.wechat.WechatConnection;
import com.imaia.ai.AssistantSettingsService;
import com.imaia.config.AiModelCatalog;
import com.imaia.config.DispatchProperties;
import com.imaia.domain.AiModel;
import com.imaia.domain.AiProvider;
import com.imaia.domain.AssistantSettings;
import com.imaia.domain.ImConnection;
import com.imaia.domain.ImConversation;
import com.imaia.repository.AiModelRepository;
import com.imaia.repository.AiProviderRepository;
import com.imaia.repository.AssistantSettingsRepository;
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

    public static AiProvider aiProvider(String name, String baseUrl) {
        AiProvider provider = new AiProvider(name, baseUrl, "key", Instant.now());
        provider.setId(IDS.incrementAndGet());
        return provider;
    }

    public static AiModel aiModel(AiProvider provider, String modelId, String name,
            List<String> input, List<String> output, boolean vision, boolean toolCall,
            Integer limitOutput, Map<String, String> params) {
        AiModel model = new AiModel(provider.getId(), modelId);
        model.setName(name);
        model.setModalitiesIn(json(input));
        model.setModalitiesOut(json(output));
        model.setVision(vision);
        model.setToolCall(toolCall);
        model.setLimitOutput(limitOutput);
        model.setParams(json(params));
        model.setSource(AiModel.SOURCE_SYNC);
        model.setAvailable(true);
        model.setEnabled(true);
        return model;
    }

    public static AiModelCatalog catalog(AiProvider provider, AiModel... models) {
        return catalog(List.of(provider), List.of(models));
    }

    public static AiModelCatalog catalog(List<AiProvider> providers, List<AiModel> models) {
        AiProviderRepository providerRepository = Mockito.mock(AiProviderRepository.class);
        AiModelRepository modelRepository = Mockito.mock(AiModelRepository.class);
        Mockito.when(providerRepository.findByEnabledTrue()).thenReturn(providers);
        Mockito.when(modelRepository.findByEnabledTrue()).thenReturn(models);
        return new AiModelCatalog(providerRepository, modelRepository);
    }

    /** The default test catalog: agnes image/video, zhipu vision chat. */
    public static AiModelCatalog catalog(String baseUrl) {
        AiProvider agnes = aiProvider("agnes", baseUrl);
        AiProvider zhipu = aiProvider("zhipu", baseUrl);
        return catalog(List.of(agnes, zhipu), List.of(
                aiModel(agnes, "img-m", "Agnes Image 2.5 Flash", List.of("text"), List.of("image"), false, false,
                        null, Map.of("size", "1024x768")),
                aiModel(agnes, "vid-m", "Agnes Video 2.5 Flash", List.of("text"), List.of("video"), false, false,
                        null, Map.of("seconds", "5", "aspect-ratio", "16:9")),
                aiModel(zhipu, "chat-m", "GLM-4.5V", List.of("text", "image"), List.of("text"), true, true,
                        4096, Map.of())));
    }

    /** The default catalog minus one capability slot (image | video | chat). */
    public static AiModelCatalog catalogWithout(String baseUrl, String slot) {
        AiProvider agnes = aiProvider("agnes", baseUrl);
        AiProvider zhipu = aiProvider("zhipu", baseUrl);
        List<AiModel> all = List.of(
                aiModel(agnes, "img-m", "Agnes Image 2.5 Flash", List.of("text"), List.of("image"), false, false,
                        null, Map.of("size", "1024x768")),
                aiModel(agnes, "vid-m", "Agnes Video 2.5 Flash", List.of("text"), List.of("video"), false, false,
                        null, Map.of("seconds", "5", "aspect-ratio", "16:9")),
                aiModel(zhipu, "chat-m", "GLM-4.5V", List.of("text", "image"), List.of("text"), true, true,
                        4096, Map.of()));
        String dropped = switch (slot) {
            case "image" -> "img-m";
            case "video" -> "vid-m";
            default -> "chat-m";
        };
        return catalog(List.of(agnes, zhipu),
                all.stream().filter(model -> !model.getModelId().equals(dropped)).toList());
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
