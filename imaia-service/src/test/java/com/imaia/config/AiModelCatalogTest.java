package com.imaia.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.imaia.domain.AiModel;
import com.imaia.domain.AiProvider;
import com.imaia.repository.AiModelRepository;
import com.imaia.repository.AiProviderRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * 模型目录的槽位推导：槽位由 modalities 与工具调用能力决定，而非单独的字段。
 *
 * <p>CHAT 需要 text 输入 + 支持工具调用；VISION 需要 image 输入。
 * 这些规则决定「AI 助手能不能用、能不能看图」，判断错了直接表现为
 * 用户发图没反应或整段对话不可用，属于必测场景。
 */
class AiModelCatalogTest {

    private static final JsonMapper TEST_MAPPER = JsonMapper.builder().build();

    static {
        JsonSupport.installForTesting(TEST_MAPPER);
    }

    private AiProviderRepository providers;
    private AiModelRepository models;
    private AiModelCatalog catalog;

    @BeforeEach
    void setUp() {
        providers = mock(AiProviderRepository.class);
        models = mock(AiModelRepository.class);
        catalog = new AiModelCatalog(providers, models, JsonMapper.builder().build());
    }

    private AiProvider provider(long id, boolean enabled) {
        AiProvider p = AiProvider.builder().name("p" + id).baseUrl("http://p" + id + "/").enabled(enabled).build();
        p.setId(id);
        return p;
    }

    /** Chat-capable: text input plus tool calling. */
    private AiModel chatModel(long id, long providerId, boolean vision) {
        AiModel m = AiModel.builder()
                .modelKey("chat" + id)
                .providerId(providerId)
                .modalitiesIn("[\"text\"" + (vision ? ",\"image\"" : "") + "]")
                .modalitiesOut("[\"text\"]")
                .supportsToolCalls(true)
                .supportsVision(vision)
                .enabled(true)
                .build();
        m.setId(id);
        return m;
    }

    /** Vision-only: accepts image input but is not the chat core. */
    private AiModel visionModel(long id, long providerId) {
        AiModel m = AiModel.builder()
                .modelKey("vision" + id)
                .providerId(providerId)
                .modalitiesIn("[\"image\"]")
                .modalitiesOut("[\"text\"]")
                .supportsToolCalls(false)
                .supportsVision(true)
                .enabled(true)
                .build();
        m.setId(id);
        return m;
    }

    @Test
    void noEnabledModelMeansNoChat() {
        when(models.findByEnabledTrue()).thenReturn(List.of());

        assertThat(catalog.hasChat()).isFalse();
        assertThat(catalog.hasVisionChat()).isFalse();
    }

    @Test
    void chatSlotModelIsSelected() {
        when(models.findByEnabledTrue()).thenReturn(List.of(chatModel(1L, 1L, false)));
        when(providers.findById(1L)).thenReturn(Optional.of(provider(1L, true)));

        assertThat(catalog.chatFor(false).modelKey()).isEqualTo("chat1");
        assertThat(catalog.hasChat()).isTrue();
    }

    @Test
    void baseUrlTrailingSlashIsNormalized() {
        when(models.findByEnabledTrue()).thenReturn(List.of(chatModel(1L, 1L, false)));
        when(providers.findById(1L)).thenReturn(Optional.of(provider(1L, true)));

        assertThat(catalog.chatFor(false).baseUrl())
                .as("拼接端点前必须去掉尾部斜杠，否则出现双斜杠")
                .isEqualTo("http://p1");
    }

    @Test
    void disabledProviderIsSkipped() {
        when(models.findByEnabledTrue()).thenReturn(List.of(chatModel(1L, 1L, false)));
        when(providers.findById(1L)).thenReturn(Optional.of(provider(1L, false)));

        assertThat(catalog.hasChat())
                .as("供应商停用后模型不应再被选中")
                .isFalse();
    }

    @Test
    void modelWhoseProviderIsMissingIsSkipped() {
        when(models.findByEnabledTrue()).thenReturn(List.of(chatModel(1L, 1L, false)));
        when(providers.findById(1L)).thenReturn(Optional.empty());

        assertThat(catalog.hasChat()).isFalse();
    }

    @Test
    void modelWithoutToolCallingIsNotAChatCore() {
        AiModel noTools = AiModel.builder()
                .modelKey("plain")
                .providerId(1L)
                .modalitiesIn("[\"text\"]")
                .modalitiesOut("[\"text\"]")
                .supportsToolCalls(false)
                .enabled(true)
                .build();
        noTools.setId(5L);
        when(models.findByEnabledTrue()).thenReturn(List.of(noTools));
        when(providers.findById(1L)).thenReturn(Optional.of(provider(1L, true)));

        assertThat(catalog.hasChat())
                .as("聊天主轴要求支持工具调用，纯文本模型不算")
                .isFalse();
    }

    @Test
    void chatForWithoutUsableModelExplainsWhatToEnable() {
        when(models.findByEnabledTrue()).thenReturn(List.of());

        assertThatThrownBy(() -> catalog.chatFor(false))
                .as("报错要告诉运维该启用哪种模型槽位")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chat");
    }

    @Test
    void visionSlotIsPreferredForImageTurns() {
        when(models.findByEnabledTrue()).thenReturn(List.of(
                chatModel(1L, 1L, false),
                visionModel(2L, 1L)));
        when(providers.findById(1L)).thenReturn(Optional.of(provider(1L, true)));

        assertThat(catalog.chatFor(true).modelKey())
                .as("有独立 vision 槽位时优先用它")
                .isEqualTo("vision2");
        assertThat(catalog.hasVisionChat()).isTrue();
    }

    @Test
    void imageTurnWithoutVisionCapabilityFailsLoudly() {
        when(models.findByEnabledTrue()).thenReturn(List.of(chatModel(1L, 1L, false)));
        when(providers.findById(1L)).thenReturn(Optional.of(provider(1L, true)));

        assertThatThrownBy(() -> catalog.chatFor(true))
                .as("要求看图但只有纯文本模型时必须明确失败，不能静默降级")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("图片输入");
    }

    @Test
    void invalidateForcesReRead() {
        when(models.findByEnabledTrue()).thenReturn(List.of(chatModel(1L, 1L, false)));
        when(providers.findById(1L)).thenReturn(Optional.of(provider(1L, true)));

        assertThat(catalog.hasChat()).isTrue();
        catalog.invalidate();
        assertThat(catalog.hasChat()).isTrue();
    }

    @Test
    void malformedModalitiesJsonIsTreatedAsEmptyRatherThanCrashing() {
        AiModel broken = AiModel.builder()
                .modelKey("broken")
                .providerId(1L)
                .modalitiesIn("not-json")
                .modalitiesOut("[]")
                .supportsToolCalls(true)
                .enabled(true)
                .build();
        broken.setId(9L);
        when(models.findByEnabledTrue()).thenReturn(List.of(broken));
        when(providers.findById(1L)).thenReturn(Optional.of(provider(1L, true)));

        // 数据损坏时按「不具备该能力」处理而不是抛错打断整条链路，
        // 但必须在日志里留痕（parseList 内 warn），否则无从排查。
        assertThat(catalog.hasChat()).isFalse();
    }

    @Test
    void emptyModalitiesIsAValidStateNotAnError() {
        AiModel blank = AiModel.builder()
                .modelKey("blank")
                .providerId(1L)
                .modalitiesIn("")
                .modalitiesOut("")
                .supportsToolCalls(true)
                .enabled(true)
                .build();
        blank.setId(8L);
        when(models.findByEnabledTrue()).thenReturn(List.of(blank));
        when(providers.findById(1L)).thenReturn(Optional.of(provider(1L, true)));

        assertThat(catalog.hasChat()).isFalse();
    }

}
