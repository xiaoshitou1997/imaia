package com.imaia.ai;

import com.imaia.domain.AiModel;
import com.imaia.domain.AiProvider;
import com.imaia.repository.AiModelRepository;
import com.imaia.repository.AiProviderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpRequest;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The gateway listing is allowed to contain nothing but model ids; the catalog must
 * still be usable because models.dev supplies names, modalities, capabilities and limits.
 */
class ModelSyncServiceTest {

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

        ModelSyncService service = new ModelSyncService(mock(AiProviderRepository.class), models) {
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
}
