package com.imaia;

import com.imaia.adapter.wechat.WechatAccountConfig;
import com.imaia.adapter.wechat.WechatConnection;
import com.imaia.ai.AiSettingsService;
import com.imaia.config.AiModelCatalog;
import com.imaia.config.JsonSupport;
import com.imaia.config.DispatchProperties;
import com.imaia.domain.AiModel;
import com.imaia.domain.AiProvider;
import com.imaia.domain.AiSetting;
import com.imaia.domain.AiSettings;
import com.imaia.domain.ConversationKind;
import com.imaia.domain.ImAccount;
import com.imaia.domain.Platform;
import com.imaia.domain.ImConversation;
import com.imaia.domain.ModelSource;
import com.imaia.repository.AiModelRepository;
import com.imaia.repository.AiProviderRepository;
import com.imaia.repository.AiSettingRepository;
import com.imaia.repository.ImAccountRepository;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/** Test fixture: entity builders and stubbed-repository services. */
public final class TestProperties {

    private static final JsonMapper MAPPER =
            JsonMapper.builder().build();


    /**
     * 纯单元测试没有 Spring 容器。静态 JSON 工具经 JsonSupport.mapper() 取共享实例，
     * 这里预置一份与容器同源的配置，避免测试各自 builder().build() 复制一份。
     */
    static {
        JsonSupport.installForTesting(JsonMapper.builder().build());
    }

    private static final AtomicLong IDS = new AtomicLong();
    private static final char QC = '"';

    private TestProperties() {
    }

    public static WechatConnection wechat(String baseUrl, String account) {
        return WechatConnection.forTesting(wechatConfig(baseUrl, account), MAPPER);
    }

    public static WechatAccountConfig wechatConfig(String baseUrl, String account) {
        return new WechatAccountConfig(baseUrl, "test-token", account, 1000, 5,
                "imaia", true, 10, 50, 200, false);
    }

    public static ImAccount account(long id, String platform) {
        Instant now = Instant.now();
        ImAccount connection = ImAccount.builder()
                .platform(Platform.fromValue(platform)).name("default").enabled(true).config("{}")
                .build();
        connection.setId(id);
        return connection;
    }

    public static ImConversation imConversation(long id, String platformId) {
        Instant now = Instant.now();
        ImConversation conversation = ImConversation.builder()
                .accountId(1L).platformId(platformId).kind(ConversationKind.DIRECT)
                .firstSeenAt(now).lastSeenAt(now)
                .build();
        // 审计字段由 JPA 审计监听器填充，不在 builder 里设；未持久化的测试对象直接 set。
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversation.setId(id);
        return conversation;
    }

    public static DispatchProperties dispatch() {
        return new DispatchProperties(3);
    }

    public static AiProvider aiProvider(String name, String baseUrl) {
        Instant now = Instant.now();
        AiProvider provider = AiProvider.builder()
                .name(name).baseUrl(baseUrl).apiKey("key")
                .config("{}").enabled(true).build();
        provider.setId(IDS.incrementAndGet());
        return provider;
    }

    public static AiModel aiModel(AiProvider provider, String modelId, String name,
            List<String> input, List<String> output, boolean vision, boolean toolCall,
            Integer limitOutput, Map<String, String> params) {
        Instant now = Instant.now();
        AiModel model = AiModel.builder()
                .providerId(provider.getId())
                .modelKey(modelId)
                .name(name)
                .modalitiesIn(json(input))
                .modalitiesOut(json(output))
                .supportsVision(vision)
                .supportsToolCalls(toolCall)
                .limitOutput(limitOutput)
                .params(json(params))
                .source(ModelSource.SYNC)
                .available(true)
                .enabled(true)
                
                
                .build();
        model.setId(IDS.incrementAndGet());
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
        for (AiProvider provider : providers) {
            Mockito.when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
        }
        for (AiModel model : models) {
            Mockito.when(modelRepository.findById(model.getId())).thenReturn(Optional.of(model));
        }
        return new AiModelCatalog(providerRepository, modelRepository, MAPPER);
    }

    /** The default test catalog: zhipu vision chat. */
    public static AiModelCatalog catalog(String baseUrl) {
        AiProvider zhipu = aiProvider("zhipu", baseUrl);
        return catalog(List.of(zhipu), List.of(
                aiModel(zhipu, "chat-m", "GLM-4.5V", List.of("text", "image"), List.of("text"), true, true,
                        4096, Map.of())));
    }

    public static AiSettings settings(String systemPrompt, int historyLimit,
            boolean requireGroupMention, boolean quietHoursEnabled, String quietHoursStart,
            String quietHoursEnd) {
        return AiSettings.builder()
                .systemPrompt(systemPrompt)
                .historyLimit(historyLimit)
                .requireGroupMention(requireGroupMention)
                .quietHoursEnabled(quietHoursEnabled)
                .quietHoursStart(quietHoursStart)
                .quietHoursEnd(quietHoursEnd)
                .timezone("Asia/Shanghai")
                .enabled(true)
                .selfCommandEnabled(true)
                
                .build();
    }

    public static AiSettingsService settingsService(AiSettings settings) {
        return settingsService(settings, List.of());
    }

    public static AiSettingsService settingsService(AiSettings settings, List<AiSetting> conversationRows) {
        AiSettingRepository repository = Mockito.mock(AiSettingRepository.class);
        ImAccountRepository connections = Mockito.mock(ImAccountRepository.class);
        Mockito.when(repository.findByPlatformIsNullAndAccountIdIsNullAndConversationIdIsNull())
                .thenReturn(globalRows(settings));
        Mockito.when(repository.findByConversationId(Mockito.anyLong())).thenAnswer(inv ->
                conversationRows.stream()
                        .filter(row -> inv.getArgument(0).equals(row.getConversationId())).toList());
        Mockito.when(repository.findByConversationIdIn(Mockito.anyCollection())).thenAnswer(inv ->
                conversationRows.stream()
                        .filter(row -> ((Collection<?>) inv.getArgument(0)).contains(row.getConversationId()))
                        .toList());
        Mockito.when(repository.findByPlatformAndAccountIdIsNullAndConversationIdIsNull(Mockito.any()))
                .thenReturn(List.of());
        Mockito.when(repository.findByAccountIdAndConversationIdIsNull(Mockito.anyLong()))
                .thenReturn(List.of());
        Mockito.when(repository.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(repository.saveAll(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(connections.findById(Mockito.any(Long.class))).thenAnswer(inv ->
                Optional.of(account(inv.getArgument(0, Long.class), "wechat")));
        Mockito.when(connections.findAllById(Mockito.anyCollection())).thenAnswer(inv -> {
            Collection<?> ids = inv.getArgument(0);
            List<ImAccount> found = new ArrayList<>();
            for (Object id : ids) {
                if (id instanceof Long value) {
                    found.add(account(value, "wechat"));
                }
            }
            return found;
        });
        return new AiSettingsService(repository, connections, MAPPER);
    }

    private static List<AiSetting> globalRows(AiSettings settings) {
        List<AiSetting> rows = new ArrayList<>();
        rows.add(AiSetting.globalText("system_prompt", settings.getSystemPrompt()));
        rows.add(AiSetting.globalInt("history_limit", settings.getHistoryLimit()));
        rows.add(AiSetting.globalBool("require_group_mention", settings.isRequireGroupMention()));
        rows.add(AiSetting.globalBool("quiet_hours_enabled", settings.isQuietHoursEnabled()));
        rows.add(AiSetting.globalText("quiet_hours_start", settings.getQuietHoursStart()));
        rows.add(AiSetting.globalText("quiet_hours_end", settings.getQuietHoursEnd()));
        rows.add(AiSetting.globalText("timezone", settings.getTimezone()));
        rows.add(AiSetting.globalBool("enabled", settings.isEnabled()));
        textRow(rows, "self_command_alias", settings.getSelfCommandAlias());
        rows.add(AiSetting.globalBool("self_command_enabled", settings.isSelfCommandEnabled()));
        textRow(rows, "web_search.base_url", settings.getWebSearchBaseUrl());
        textRow(rows, "web_search.api_key", settings.getWebSearchApiKey());
        textRow(rows, "image.base_url", settings.getImageBaseUrl());
        textRow(rows, "image.api_key", settings.getImageApiKey());
        textRow(rows, "image.model", settings.getImageModel());
        textRow(rows, "video.base_url", settings.getVideoBaseUrl());
        textRow(rows, "video.api_key", settings.getVideoApiKey());
        textRow(rows, "video.model", settings.getVideoModel());
        return rows;
    }

    /** Tool endpoint keys have no code default: an unset value stays an absent row. */
    private static void textRow(List<AiSetting> rows, String key, String value) {
        if (value != null) {
            rows.add(AiSetting.globalText(key, value));
        }
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
