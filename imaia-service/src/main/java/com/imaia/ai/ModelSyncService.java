package com.imaia.ai;

import com.imaia.domain.ModelSource;
import com.imaia.domain.AiModel;
import com.imaia.domain.AiProvider;
import com.imaia.repository.AiModelRepository;
import com.imaia.repository.AiProviderRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;
import java.util.Set;

/**
 * Pulls the provider's /v1/models listing and upserts ai_model rows — availability
 * is the source of truth. Metadata is enriched from models.dev when the model id
 * matches there (models_dev_id narrows the provider first). Local annotations are
 * never overwritten: enabled/params stay untouched, and manual rows only get
 * availability refreshed. Models that vanished from the listing flip available=false.
 */
@Component
public class ModelSyncService extends AbstractAiHttpClient {

    /** What one sync pass did, for the console toast. */
    public record SyncResult(int listed, int added, int updated, int retired) {
    }

    private static final Duration GATEWAY_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration MODELS_DEV_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration MODELS_DEV_CACHE = Duration.ofMinutes(10);
    private static final String MODELS_DEV_API = "https://models.dev/api.json";

    private final AiProviderRepository providers;
    private final AiModelRepository aiModels;
    private volatile JsonNode modelsDevCache;
    private volatile Instant modelsDevFetchedAt;

    public ModelSyncService(AiProviderRepository providers, AiModelRepository aiModels) {
        this.providers = providers;
        this.aiModels = aiModels;
    }

    public SyncResult sync(AiProvider provider) {
        JsonNode listing = fetchGatewayModels(provider);
        Map<String, JsonNode> devModels = modelsDevIndex(provider.getModelsDevId());

        List<AiModel> existing = aiModels.findByProviderId(provider.getId());
        Map<String, AiModel> byModelId = new LinkedHashMap<>();
        for (AiModel model : existing) {
            byModelId.put(model.getModelId(), model);
        }

        int added = 0;
        int updated = 0;
        Set<String> seen = new HashSet<>();
        for (JsonNode entry : listing.path("data")) {
            String modelId = entry.path("id").asString("");
            if (modelId.isBlank()) {
                continue;
            }
            seen.add(modelId);
            AiModel model = byModelId.get(modelId);
            if (model == null) {
                model = new AiModel(provider.getId(), modelId);
                added++;
            } else {
                updated++;
            }
            JsonNode dev = devModels.get(modelId);
            if (dev != null && model.getSource() != ModelSource.MANUAL) {
                applyDevMetadata(model, dev);
            }
            if ((!StringUtils.hasText(model.getName()))
                    && entry.has("name") && !entry.path("name").asString().isBlank()) {
                model.setName(entry.path("name").asString());
            }
            model.setAvailable(true);
            model.setSyncedAt(Instant.now());
            aiModels.save(model);
        }

        int retired = 0;
        for (AiModel model : existing) {
            if (!seen.contains(model.getModelId()) && model.isAvailable()) {
                model.setAvailable(false);
                aiModels.save(model);
                retired++;
            }
        }
        log.info("Synced provider {}: {} listed, {} added, {} updated, {} retired.",
                provider.getName(), seen.size(), added, updated, retired);
        return new SyncResult(seen.size(), added, updated, retired);
    }

    private JsonNode fetchGatewayModels(AiProvider provider) {
        HttpRequest request = baseRequest(
                URI.create(stripTrailingSlash(provider.getBaseUrl()) + "/models"),
                provider.getApiKey(), GATEWAY_TIMEOUT)
                .GET()
                .build();
        return sendJson(request);
    }

    /** models.dev models indexed by id; models_dev_id matches first, global scan fills the rest. */
    private Map<String, JsonNode> modelsDevIndex(String providerKey) {
        JsonNode root = modelsDev();
        Map<String, JsonNode> index = new HashMap<>();
        if (StringUtils.hasText(providerKey)) {
            for (var field : root.path(providerKey).path("models").properties()) {
                index.put(field.getKey(), field.getValue());
            }
        }
        for (var providerEntry : root.properties()) {
            for (var modelEntry : providerEntry.getValue().path("models").properties()) {
                index.putIfAbsent(modelEntry.getKey(), modelEntry.getValue());
            }
        }
        return index;
    }

    /** The models.dev catalog, cached for ten minutes (it is a 6 MB document). */
    private JsonNode modelsDev() {
        JsonNode cached = modelsDevCache;
        if (cached != null && modelsDevFetchedAt != null
                && Instant.now().minus(MODELS_DEV_CACHE).isBefore(modelsDevFetchedAt)) {
            return cached;
        }
        synchronized (this) {
            if (modelsDevCache != null && modelsDevFetchedAt != null
                    && Instant.now().minus(MODELS_DEV_CACHE).isBefore(modelsDevFetchedAt)) {
                return modelsDevCache;
            }
            HttpRequest request = baseRequest(URI.create(MODELS_DEV_API), "", MODELS_DEV_TIMEOUT).GET().build();
            JsonNode root = sendJson(request);
            modelsDevCache = root;
            modelsDevFetchedAt = Instant.now();
            return root;
        }
    }

    /** models.dev metadata onto a sync-row; local annotations stay untouched. */
    private void applyDevMetadata(AiModel model, JsonNode dev) {
        if ((!StringUtils.hasText(model.getName()))
                && !dev.path("name").asString().isBlank()) {
            model.setName(dev.path("name").asString());
        }
        if (dev.has("description")) {
            model.setDescription(dev.path("description").asString(null));
        }
        JsonNode modalities = dev.path("modalities");
        if (modalities.path("input").isArray()) {
            model.setModalitiesIn(modalities.path("input").toString());
        }
        if (modalities.path("output").isArray()) {
            model.setModalitiesOut(modalities.path("output").toString());
        }
        model.setVision(dev.path("attachment").asBoolean(false));
        model.setReasoning(dev.path("reasoning").asBoolean(false));
        model.setToolCall(dev.path("tool_call").asBoolean(true));
        model.setLimitContext(intOrNull(dev.path("limit").path("context")));
        model.setLimitOutput(intOrNull(dev.path("limit").path("output")));
        model.setCostInput(costOrNull(dev.path("cost").path("input")));
        model.setCostOutput(costOrNull(dev.path("cost").path("output")));
    }

    private static Integer intOrNull(JsonNode node) {
        return node.isNumber() ? node.asInt() : null;
    }

    private static java.math.BigDecimal costOrNull(JsonNode node) {
        return node.isNumber() ? node.decimalValue() : null;
    }

    private static String stripTrailingSlash(String url) {
        String base = url == null ? "" : url.trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
