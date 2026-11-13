package com.imaia.web;

import com.imaia.domain.ModelSource;
import com.imaia.ai.ModelSyncService;
import com.imaia.ai.ModelSyncService;
import com.imaia.config.AiModelCatalog;
import com.imaia.domain.AiModel;
import com.imaia.domain.AiProvider;
import com.imaia.repository.AiModelRepository;
import com.imaia.repository.AiProviderRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.springframework.util.StringUtils;
import java.util.List;

/**
 * AI 接入商与模型目录：接入商 CRUD（api_key 掩码）、/v1/models 同步、
 * 模型标注（enabled/模态/params）与删除。目录改动 invalidate 后下一轮生效。
 */
@RestController
@RequestMapping("/api/ai-providers")
public class AiProviderController {

    private static final String MASK = "••••••••";

    private final AiProviderRepository providers;
    private final AiModelRepository aiModels;
    private final AiModelCatalog catalog;
    private final ModelSyncService syncService;

    public AiProviderController(AiProviderRepository providers, AiModelRepository aiModels,
            AiModelCatalog catalog, ModelSyncService syncService) {
        this.providers = providers;
        this.aiModels = aiModels;
        this.catalog = catalog;
        this.syncService = syncService;
    }

    public record ModelRow(Long id, String modelId, String name, String description, String modalitiesIn,
            String modalitiesOut, boolean vision, boolean reasoning, boolean toolCall, Integer limitContext,
            Integer limitOutput, java.math.BigDecimal costInput, java.math.BigDecimal costOutput, String params,
            boolean available, boolean enabled, ModelSource source, String syncedAt) {
    }

    public record ProviderRow(Long id, String name, String baseUrl, String apiKey, boolean enabled,
            String modelsDevId, List<ModelRow> models) {
    }

    public record ProviderCreate(String name, String baseUrl, String apiKey, Boolean enabled, String modelsDevId) {
    }

    /** Create result: the persisted row plus the human-readable sync note. */
    public record CreatedProvider(ProviderRow provider, String sync) {
    }

    public record ProviderUpdate(String baseUrl, String apiKey, Boolean enabled, String modelsDevId) {
    }

    public record ModelUpdate(String modelId, String name, String modalitiesIn, String modalitiesOut,
            Boolean vision, Boolean reasoning, Boolean toolCall, Integer limitContext, Integer limitOutput,
            String params, Boolean enabled) {
    }

    @GetMapping
    public ApiEnvelope<List<ProviderRow>> list() {
        Map<Long, List<ModelRow>> modelsByProvider = new HashMap<>();
        for (AiModel model : aiModels.findAll()) {
            modelsByProvider.computeIfAbsent(model.getProviderId(), key -> new ArrayList<>())
                    .add(toModelRow(model));
        }
        List<ProviderRow> rows = providers.findAllByOrderByIdAsc().stream()
                .map(provider -> new ProviderRow(provider.getId(), provider.getName(), provider.getBaseUrl(),
                        maskKey(provider.getApiKey()), provider.isEnabled(), provider.getModelsDevId(),
                        modelsByProvider.getOrDefault(provider.getId(), List.of())))
                .toList();
        return ApiEnvelope.ok(rows);
    }

    @PostMapping
    public ApiEnvelope<CreatedProvider> create(@RequestBody ProviderCreate body) {
        requireText(body.name(), "name");
        requireText(body.baseUrl(), "baseUrl");
        AiProvider provider = new AiProvider(body.name().strip(), body.baseUrl().strip(),
                body.apiKey(), Clock.now());
        provider.setModelsDevId(blankToNull(body.modelsDevId()));
        AiProvider saved = providers.save(provider);
        String syncNote = "未同步";
        try {
            ModelSyncService.SyncResult result = syncService.sync(saved);
            syncNote = "已同步 " + result.listed() + " 个模型";
        } catch (RuntimeException failure) {
            syncNote = "保存成功，但同步失败：" + failure.getMessage();
        }
        catalog.invalidate();
        return ApiEnvelope.ok(new CreatedProvider(
                toRow(providers.findById(saved.getId()).orElseThrow()), syncNote));
    }

    @PutMapping("/{id}")
    public ApiEnvelope<ProviderRow> update(@PathVariable Long id, @RequestBody ProviderUpdate body) {
        AiProvider provider = providers.findById(id).orElseThrow();
        if (StringUtils.hasText(body.baseUrl())) {
            provider.setBaseUrl(body.baseUrl().strip());
        }
        if (body.apiKey() != null && !MASK.equals(body.apiKey())) {
            provider.setApiKey(body.apiKey().isBlank() ? null : body.apiKey());
        }
        if (body.enabled() != null) {
            provider.setEnabled(body.enabled());
        }
        if (body.modelsDevId() != null) {
            provider.setModelsDevId(blankToNull(body.modelsDevId()));
        }
        AiProvider saved = providers.save(provider);
        catalog.invalidate();
        return ApiEnvelope.ok(toRow(saved));
    }

    @DeleteMapping("/{id}")
    public ApiEnvelope<String> delete(@PathVariable Long id) {
        providers.findById(id).orElseThrow();
        aiModels.deleteAll(aiModels.findByProviderIdOrderByModelIdAsc(id));
        providers.deleteById(id);
        catalog.invalidate();
        return ApiEnvelope.ok("已删除");
    }

    /** 拉取 /v1/models 并同步目录；可用性以网关为准，本地标注不覆盖。 */
    @PostMapping("/{id}/sync-models")
    public ApiEnvelope<ModelSyncService.SyncResult> syncModels(@PathVariable Long id) {
        AiProvider provider = providers.findById(id).orElseThrow();
        ModelSyncService.SyncResult result = syncService.sync(provider);
        catalog.invalidate();
        return ApiEnvelope.ok(result);
    }

    /** 手动登记一个同步目录里没有的模型（source=manual）。 */
    @PostMapping("/{id}/ai-models")
    public ApiEnvelope<ModelRow> addModel(@PathVariable Long id, @RequestBody ModelUpdate body) {
        providers.findById(id).orElseThrow();
        if (!StringUtils.hasText(body.modelId())) {
            throw new IllegalArgumentException("modelId 不能为空。");
        }
        AiModel model = new AiModel(id, body.modelId().strip());
        model.setSource(ModelSource.MANUAL);
        applyModelUpdate(model, body);
        model.setEnabled(body.enabled() == null || body.enabled());
        model.setAvailable(true);
        AiModel saved = aiModels.save(model);
        catalog.invalidate();
        return ApiEnvelope.ok(toModelRow(saved));
    }

    @PutMapping("/ai-models/{id}")
    public ApiEnvelope<ModelRow> updateModel(@PathVariable Long id, @RequestBody ModelUpdate body) {
        AiModel model = aiModels.findById(id).orElseThrow();
        applyModelUpdate(model, body);
        if (body.enabled() != null) {
            model.setEnabled(body.enabled());
        }
        AiModel saved = aiModels.save(model);
        catalog.invalidate();
        return ApiEnvelope.ok(toModelRow(saved));
    }

    @DeleteMapping("/ai-models/{id}")
    public ApiEnvelope<String> deleteModel(@PathVariable Long id) {
        aiModels.findById(id).orElseThrow();
        aiModels.deleteById(id);
        catalog.invalidate();
        return ApiEnvelope.ok("已删除");
    }

    private void applyModelUpdate(AiModel model, ModelUpdate body) {
        if (body.name() != null) {
            model.setName(body.name().isBlank() ? null : body.name().strip());
        }
        if (body.modalitiesIn() != null) {
            model.setModalitiesIn(toJsonArray(body.modalitiesIn()));
        }
        if (body.modalitiesOut() != null) {
            model.setModalitiesOut(toJsonArray(body.modalitiesOut()));
        }
        if (body.vision() != null) {
            model.setVision(body.vision());
        }
        if (body.reasoning() != null) {
            model.setReasoning(body.reasoning());
        }
        if (body.toolCall() != null) {
            model.setToolCall(body.toolCall());
        }
        if (body.limitContext() != null) {
            model.setLimitContext(body.limitContext());
        }
        if (body.limitOutput() != null) {
            model.setLimitOutput(body.limitOutput());
        }
        if (StringUtils.hasText(body.params())) {
            model.setParams(body.params().strip());
        }
    }

    /** "text,image" → ["text","image"] 的 JSON 文本。 */
    private String toJsonArray(String csv) {
        List<String> items = Arrays.stream(csv.split(","))
                .map(String::strip).filter(s -> !s.isEmpty()).toList();
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append('"').append(items.get(i)).append('"');
        }
        return json.append(']').toString();
    }

    private ModelRow toModelRow(AiModel model) {
        return new ModelRow(model.getId(), model.getModelId(), model.getName(), model.getDescription(),
                model.getModalitiesIn(), model.getModalitiesOut(), model.isVision(), model.isReasoning(),
                model.isToolCall(), model.getLimitContext(), model.getLimitOutput(),
                model.getCostInput(), model.getCostOutput(), model.getParams(),
                model.isAvailable(), model.isEnabled(), model.getSource(),
                model.getSyncedAt() == null ? null : model.getSyncedAt().toString());
    }

    private ProviderRow toRow(AiProvider provider) {
        List<ModelRow> models = aiModels.findByProviderIdOrderByModelIdAsc(provider.getId()).stream()
                .map(this::toModelRow).toList();
        return new ProviderRow(provider.getId(), provider.getName(), provider.getBaseUrl(),
                maskKey(provider.getApiKey()), provider.isEnabled(), provider.getModelsDevId(), models);
    }

    private static String maskKey(String apiKey) {
        return !StringUtils.hasText(apiKey) ? "" : MASK;
    }

    private static String blankToNull(String value) {
        return !StringUtils.hasText(value) ? null : value.strip();
    }

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " 不能为空。");
        }
    }
}
