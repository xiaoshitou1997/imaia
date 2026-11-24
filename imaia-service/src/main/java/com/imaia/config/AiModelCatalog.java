package com.imaia.config;

import com.imaia.domain.AiModel;
import com.imaia.domain.AiProvider;
import com.imaia.domain.ModelSlot;
import com.imaia.repository.AiModelRepository;
import com.imaia.repository.AiProviderRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the chat/vision slots from the model directory by derivation: enabled
 * models only, matched by modalities and tool support, lowest id wins. The
 * directory is LLMs only — image/video generation are standalone tools configured
 * in the settings KV, not catalog slots. To change who serves a slot, toggle the
 * model's enabled flag in the directory.
 */
@Component
public class AiModelCatalog {

    /** One model resolved from the directory together with its provider connection. */
    public record Resolved(Long providerId, Long aiModelId, String providerName, String baseUrl,
            String apiKey, String id, String name, Integer maxOutputTokens, Map<String, String> params) {

        public String modelKey() {
            return id;
        }

        /** Single connection-time parameter from params JSON ("{}" when unset). */
        public String param(String key) {
            return params == null ? null : params.get(key);
        }
    }

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final AiProviderRepository providers;
    private final AiModelRepository models;

    public AiModelCatalog(AiProviderRepository providers, AiModelRepository models) {
        this.providers = providers;
        this.models = models;
    }

    /** Kept for callers that invalidate after directory edits; resolution is uncached. */
    public synchronized void invalidate() {
        // no cache: every resolution reads the model/provider rows
    }

    /** 对话模型：带图轮优先 vision 推导，无则回退 chat。 */
    public Resolved chatFor(boolean withImage) {
        Entry entry = withImage ? derive(ModelSlot.VISION) : null;
        if (entry == null) {
            entry = derive(ModelSlot.CHAT);
        }
        if (entry == null) {
            throw new IllegalStateException("chat 槽位推导不到可用模型：请到模型目录启用 chat"
                    + (withImage ? " 或 vision" : "") + " 模型。");
        }
        if (withImage && !declaresImageInput(entry.model())) {
            throw new IllegalStateException("chat 槽位模型不支持图片输入："
                    + entry.model().getModelKey());
        }
        return resolved(entry);
    }

    /** Whether a usable chat model is enabled. */
    public boolean hasChat() {
        return derive(ModelSlot.CHAT) != null;
    }

    /** Whether image turns have a vision-capable model (vision rule or vision-capable chat). */
    public boolean hasVisionChat() {
        Entry vision = derive(ModelSlot.VISION);
        if (vision != null) {
            return declaresImageInput(vision.model());
        }
        Entry chat = derive(ModelSlot.CHAT);
        return chat != null && declaresImageInput(chat.model());
    }

    private Entry derive(ModelSlot slot) {
        List<AiModel> enabled = new ArrayList<>(models.findByEnabledTrue());
        enabled.sort((a, b) -> Long.compare(a.getId(), b.getId()));
        for (AiModel model : enabled) {
            if (!matches(model, slot)) {
                continue;
            }
            AiProvider provider = providers.findById(model.getProviderId()).orElse(null);
            if (provider == null || !provider.isEnabled()) {
                continue;
            }
            return new Entry(provider.getName(), provider, model);
        }
        return null;
    }

    private static boolean matches(AiModel model, ModelSlot slot) {
        List<String> in = parseList(model.getModalitiesIn());
        return switch (slot) {
            case CHAT -> in.contains("text") && model.isSupportsToolCalls();
            case VISION -> in.contains("image");
        };
    }

    private static boolean declaresImageInput(AiModel model) {
        return parseList(model.getModalitiesIn()).contains("image");
    }

    private Resolved resolved(Entry entry) {
        AiModel model = entry.model();
        return new Resolved(entry.provider().getId(), model.getId(), entry.providerName(),
                normalize(entry.provider().getBaseUrl()), entry.provider().getApiKey(),
                model.getModelKey(), model.getName(), model.getLimitOutput(),
                parseMap(model.getParams()));
    }

    private static String normalize(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static List<String> parseList(String json) {
        try {
            List<String> out = new ArrayList<>();
            for (JsonNode node : MAPPER.readTree(!StringUtils.hasText(json) ? "[]" : json)) {
                if (node.isString()) {
                    out.add(node.asString());
                }
            }
            return out;
        } catch (RuntimeException failure) {
            return List.of();
        }
    }

    private static Map<String, String> parseMap(String json) {
        try {
            Map<String, String> out = new LinkedHashMap<>();
            for (var field : MAPPER.readTree(!StringUtils.hasText(json) ? "{}" : json).properties()) {
                out.put(field.getKey(), field.getValue().isString()
                        ? field.getValue().asString() : field.getValue().toString());
            }
            return out;
        } catch (RuntimeException failure) {
            return Map.of();
        }
    }

    private record Entry(String providerName, AiProvider provider, AiModel model) {
    }
}
