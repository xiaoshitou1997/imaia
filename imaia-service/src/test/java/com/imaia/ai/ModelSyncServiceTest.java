package com.imaia.ai;

import com.imaia.domain.AiModel;
import com.imaia.domain.AiProvider;
import com.imaia.domain.ModelSource;
import com.imaia.repository.AiModelRepository;
import com.imaia.repository.AiProviderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpRequest;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatRuntimeException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import tools.jackson.databind.json.JsonMapper;

/**
 * The gateway listing is allowed to contain nothing but model ids; the catalog must
 * still be usable because models.dev supplies names, modalities, capabilities and limits.
 */
class ModelSyncServiceTest {

    private static final JsonMapper MAPPER =
            JsonMapper.builder().build();


    private static final String GATEWAY_ONLY_IDS = """
            {"object":"list","data":[{"id":"glm-4.5v","object":"model"}]}
            """;

    private static final String MODELS_DEV = """
            {
              "zhipuai": {
                "id": "zhipuai",
                "models": {
                  "glm-4.5v": {
                    "id": "glm-4.5v",
                    "name": "GLM-4.5V",
                    "description": "GLM vision model",
                    "attachment": true,
                    "reasoning": true,
                    "tool_call": true,
                    "modalities": {"input": ["text", "image"], "output": ["text"]},
                    "limit": {"context": 64000, "output": 16384},
                    "cost": {"input": 0.29879, "output": 0.89637}
                  }
                }
              }
            }
            """;

    @Test
    void enrichesIdOnlyListingFromModelsDev() throws Exception {
        AiModelRepository models = mock(AiModelRepository.class);
        when(models.findByProviderId(7L)).thenReturn(List.of());
        when(models.save(any(AiModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ModelSyncService service = new ModelSyncService(mock(AiProviderRepository.class), models, MAPPER) {
            @Override
            JsonNode sendJson(HttpRequest request) {
                return request.uri().toString().contains("models.dev")
                        ? mapper.readTree(MODELS_DEV)
                        : mapper.readTree(GATEWAY_ONLY_IDS);
            }
        };

        AiProvider provider = AiProvider.builder()
                .id(7L)
                .name("zhipu")
                .baseUrl("https://gateway.example/v1")
                .apiKey("key")
                .modelsDevId("zhipu")
                .enabled(true)
                
                
                .build();

        ModelSyncService.SyncResult result = service.sync(provider);

        ArgumentCaptor<AiModel> saved = ArgumentCaptor.forClass(AiModel.class);
        verify(models).save(saved.capture());
        AiModel model = saved.getValue();

        assertThat(result.listed()).isEqualTo(1);
        assertThat(result.added()).isEqualTo(1);
        assertThat(model.getModelKey()).isEqualTo("glm-4.5v");
        assertThat(model.getName()).isEqualTo("GLM-4.5V");
        assertThat(model.getDescription()).isEqualTo("GLM vision model");
        assertThat(model.getModalitiesIn()).isEqualTo("[\"text\",\"image\"]");
        assertThat(model.getModalitiesOut()).isEqualTo("[\"text\"]");
        assertThat(model.isSupportsVision()).isTrue();
        assertThat(model.isSupportsReasoning()).isTrue();
        assertThat(model.isSupportsToolCalls()).isTrue();
        assertThat(model.getLimitContext()).isEqualTo(64000);
        assertThat(model.getLimitOutput()).isEqualTo(16384);
        assertThat(model.getCostInput()).isEqualByComparingTo("0.29879");
        assertThat(model.getCostOutput()).isEqualByComparingTo("0.89637");
        assertThat(model.isAvailable()).isTrue();
        assertThat(model.isEnabled()).isTrue();
    }

    @Test
    void gatewayFailurePropagatesInsteadOfSilentlyEmptyingCatalog() {
        AiModelRepository models = mock(AiModelRepository.class);
        when(models.findByProviderId(7L)).thenReturn(List.of());

        ModelSyncService service = new ModelSyncService(mock(AiProviderRepository.class), models, MAPPER) {
            @Override
            JsonNode sendJson(HttpRequest request) {
                // 网关列表失败是同步失败本身，必须抛出让调用方知晓（控制台回执报错），
                // 不能返回空列表把目录当「网关下架了所有模型」。
                throw new RuntimeException("gateway connection refused");
            }
        };

        AiProvider provider = AiProvider.builder()
                .id(7L)
                .name("zhipu")
                .baseUrl("https://gateway.example/v1")
                .apiKey("key")
                .enabled(true)
                .build();

        assertThatRuntimeException()
                .isThrownBy(() -> service.sync(provider))
                .withMessageContaining("gateway");
        verify(models, never()).save(any(AiModel.class));
    }

    @Test
    void enrichFailureDoesNotBlockSyncAndManualModelsKeepAnnotations() throws Exception {
        AiModelRepository models = mock(AiModelRepository.class);
        AiModel manual = AiModel.builder()
                .providerId(7L)
                .modelKey("glm-4.5v")
                .name("手工标注名")
                .source(ModelSource.MANUAL)
                .modalitiesIn("[]")
                .modalitiesOut("[]")
                .available(true)
                .enabled(true)
                .params("{}")
                .build();
        when(models.findByProviderId(7L)).thenReturn(List.of(manual));
        when(models.save(any(AiModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ModelSyncService service = new ModelSyncService(mock(AiProviderRepository.class), models, MAPPER) {
            @Override
            JsonNode sendJson(HttpRequest request) {
                if (request.uri().toString().contains("models.dev")) {
                    // 增强源失败不阻断同步（enrichmentIndexOrEmpty 降级为空）。
                    throw new RuntimeException("models.dev unreachable");
                }
                return mapper.readTree(GATEWAY_ONLY_IDS);
            }
        };

        AiProvider provider = AiProvider.builder()
                .id(7L)
                .name("zhipu")
                .baseUrl("https://gateway.example/v1")
                .apiKey("key")
                .modelsDevId("zhipu")
                .enabled(true)
                .build();

        ModelSyncService.SyncResult result = service.sync(provider);

        assertThat(result.listed()).isEqualTo(1);
        ArgumentCaptor<AiModel> saved = ArgumentCaptor.forClass(AiModel.class);
        verify(models).save(saved.capture());
        // 手工标注不被同步覆盖：名字保留，增强源失败也不会清掉。
        assertThat(saved.getValue().getName()).isEqualTo("手工标注名");
        assertThat(saved.getValue().isAvailable()).isTrue();
    }
}
