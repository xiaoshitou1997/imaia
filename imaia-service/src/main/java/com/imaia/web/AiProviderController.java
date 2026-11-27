package com.imaia.web;

import com.imaia.ai.ModelSyncService;
import com.imaia.config.AiModelCatalog;
import com.imaia.domain.AiModel;
import com.imaia.domain.AiProvider;
import com.imaia.domain.ModelSource;
import com.imaia.repository.AiModelRepository;
import com.imaia.repository.AiProviderRepository;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 接入商与模型目录：接入商 CRUD（api_key 掩码）、/v1/models 同步、
 * 模型标注（enabled/模态/params）与删除。目录改动 invalidate 后下一轮生效。
 */
@RestController
@RequestMapping("/api/ai-providers")
public class AiProviderController {

    private static final Logger log = LoggerFactory.getLogger(AiProviderController.class);
    private static final String MASK = "••••••••";

    private static final JsonMapper JSON = JsonMapper.builder().build();

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

    public record ModelRow(Long id, String modelKey, String name, String description, String modalitiesIn,
            String modalitiesOut, boolean supportsVision, boolean supportsReasoning, boolean supportsToolCalls, Integer limitContext,
            Integer limitOutput, BigDecimal costInput, BigDecimal costOutput, String params,
            boolean available, boolean enabled, ModelSource source, String syncedAt) {
    }

    public record ProviderRow(Long id, String name, String baseUrl, String apiKey,
            boolean enabled, String modelsDevId, List<ModelRow> models, String sync) {
    }

    public record ProviderCreate(String name, String baseUrl, String apiKey,
            Boolean enabled, String modelsDevId) {
    }

    /** Create result: the persisted row plus the human-readable sync note. */
    public record CreatedProvider(ProviderRow provider, String sync) {
    }

    public record ProviderUpdate(String baseUrl, String apiKey, Boolean enabled, String modelsDevId) {
    }

    public record ModelUpdate(String modelKey, String name, String modalitiesIn, String modalitiesOut,
            Boolean supportsVision, Boolean supportsReasoning, Boolean supportsToolCalls, Integer limitContext, Integer limitOutput,
            String params, Boolean enabled) {
    }


    @GetMapping
    public Result<List<ProviderRow>> list() {
        List<AiProvider> all = providers.findAllByOrderByIdAsc();
        Map<Long, List<ModelRow>> modelsByProvider = new HashMap<>();
        for (AiModel model : aiModels.findByProviderIdIn(all.stream().map(AiProvider::getId).toList())) {
            modelsByProvider.computeIfAbsent(model.getProviderId(), key -> new ArrayList<>())
                    .add(toModelRow(model));
        }
        List<ProviderRow> rows = all.stream()
                .map(provider -> new ProviderRow(provider.getId(), provider.getName(), 
                        provider.getBaseUrl(), maskKey(provider.getApiKey()), provider.isEnabled(),
                        provider.getModelsDevId(),
                        modelsByProvider.getOrDefault(provider.getId(), List.of()), null))
                .toList();
        return Result.ok(rows);
    }

    @PostMapping
    public Result<CreatedProvider> create(@RequestBody ProviderCreate body) {
        requireText(body.name(), "name");
        requireText(body.baseUrl(), "baseUrl");
        Instant now = Clock.now();
        AiProvider provider = AiProvider.builder()
                .name(body.name().strip())
                
                .baseUrl(body.baseUrl().strip())
                .apiKey(body.apiKey())
                .modelsDevId(blankToNull(body.modelsDevId()))
                .config("{}")
                .enabled(body.enabled() == null || body.enabled())
                .createdAt(now)
                .updatedAt(now)
                .build();
        AiProvider saved = providers.save(provider);
        String syncNote = "未同步";
        try {
            ModelSyncService.SyncResult result = syncService.sync(saved);
            syncNote = "已同步 " + result.listed() + " 个模型";
        } catch (RuntimeException failure) {
            log.warn("Provider {} created but model sync failed: {}", saved.getName(), failure.getMessage());
            syncNote = "保存成功，但同步失败：" + failure.getMessage();
        }
        catalog.invalidate();
        return Result.ok(new CreatedProvider(
                toRow(providers.findById(saved.getId()).orElseThrow(), syncNote), syncNote));
    }

    @PutMapping("/{id}")
    public Result<ProviderRow> update(@PathVariable Long id, @RequestBody ProviderUpdate body) {
        AiProvider provider = providers.findById(id).orElseThrow();
        String oldBaseUrl = provider.getBaseUrl();
        String oldApiKey = provider.getApiKey();
        String oldModelsDevId = provider.getModelsDevId();
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
        provider.setUpdatedAt(Instant.now());
        AiProvider saved = providers.save(provider);

        boolean modelConfigurationChanged =
                !Objects.equals(oldBaseUrl, saved.getBaseUrl())
                || !Objects.equals(oldApiKey, saved.getApiKey())
                || !Objects.equals(oldModelsDevId, saved.getModelsDevId());
        
        String syncNote = null;
        if (modelConfigurationChanged) {
            try {
                ModelSyncService.SyncResult result = syncService.sync(saved);
                syncNote = "已同步 " + result.listed() + " 个模型";
            } catch (RuntimeException failure) {
                log.warn("Provider {} updated but model sync failed: {}", saved.getName(), failure.getMessage());
                syncNote = "连接已保存，但同步失败：" + failure.getMessage();
            }
        }
        catalog.invalidate();
        return Result.ok(toRow(saved, syncNote));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Result<String> delete(@PathVariable Long id) {
        providers.findById(id).orElseThrow();
        List<AiModel> models = aiModels.findByProviderIdOrderByModelKeyAsc(id);
        List<Long> modelIds = models.stream().map(AiModel::getId).toList();
        aiModels.deleteAll(models);
        providers.deleteById(id);
        catalog.invalidate();
        return Result.ok("已删除接入商，并移除 " + models.size() + " 个模型");
    }

    /** 拉取 /v1/models 并同步目录；可用性以网关为准，本地标注不覆盖。 */
    @PostMapping("/{id}/sync-models")
    public Result<ModelSyncService.SyncResult> syncModels(@PathVariable Long id) {
        AiProvider provider = providers.findById(id).orElseThrow();
        ModelSyncService.SyncResult result = syncService.sync(provider);
        catalog.invalidate();
        return Result.ok(result);
    }

    @PutMapping("/ai-models/{id}")
    public Result<ModelRow> updateModel(@PathVariable Long id, @RequestBody ModelUpdate body) {
        AiModel model = aiModels.findById(id).orElseThrow();
        applyModelUpdate(model, body);
        if (body.enabled() != null) {
            model.setEnabled(body.enabled());
        }
        model.setUpdatedAt(Instant.now());
        AiModel saved = aiModels.save(model);
        catalog.invalidate();
        return Result.ok(toModelRow(saved));
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
        if (body.supportsVision() != null) {
            model.setSupportsVision(body.supportsVision());
        }
        if (body.supportsReasoning() != null) {
            model.setSupportsReasoning(body.supportsReasoning());
        }
        if (body.supportsToolCalls() != null) {
            model.setSupportsToolCalls(body.supportsToolCalls());
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

    /** "text,image" → ["text","image"] 的 JSON 文本（经序列化器，避免手拼注入）。 */
    private String toJsonArray(String csv) {
        List<String> items = Arrays.stream(csv.split(","))
                .map(String::strip).filter(s -> !s.isEmpty()).toList();
        return JSON.writeValueAsString(items);
    }

    private ModelRow toModelRow(AiModel model) {
        return new ModelRow(model.getId(), model.getModelKey(), model.getName(), model.getDescription(),
                model.getModalitiesIn(), model.getModalitiesOut(), model.isSupportsVision(), model.isSupportsReasoning(),
                model.isSupportsToolCalls(), model.getLimitContext(), model.getLimitOutput(),
                model.getCostInput(), model.getCostOutput(), model.getParams(),
                model.isAvailable(), model.isEnabled(), model.getSource(),
                model.getSyncedAt() == null ? null : model.getSyncedAt().toString());
    }

    private ProviderRow toRow(AiProvider provider) {
        return toRow(provider, null);
    }

    private ProviderRow toRow(AiProvider provider, String syncNote) {
        List<ModelRow> models = aiModels.findByProviderIdOrderByModelKeyAsc(provider.getId()).stream()
                .map(this::toModelRow).toList();
        return new ProviderRow(provider.getId(), provider.getName(), 
                provider.getBaseUrl(), maskKey(provider.getApiKey()), provider.isEnabled(),
                provider.getModelsDevId(), models, syncNote);
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
