package com.imaia.config;

import com.imaia.config.JsonSupport;
import com.imaia.domain.AiModel;
import com.imaia.domain.AiProvider;
import com.imaia.domain.ModelSlot;
import com.imaia.repository.AiModelRepository;
import com.imaia.repository.AiProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AiModelCatalog.class);

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

    /** 共享 Jackson 配置：Spring 注入，不自建实例绕过 spring.jackson.* 定制。 */
    private final JsonMapper mapper;

    private final AiProviderRepository providers;
    private final AiModelRepository models;

    public AiModelCatalog(AiProviderRepository providers, AiModelRepository models, JsonMapper jsonMapper) {
        this.providers = providers;
        this.models = models;
        this.mapper = jsonMapper;
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

    /**
     * 解析 JSON 字符串数组。
     *
     * <p>字段为空是合法状态（返回空列表）；但「有内容却解析不了」是数据损坏，
     * 必须留痕——静默返回空列表会让模型被判定为「不支持该模态」，
     * 表现成用户发图没反应，而日志里什么线索都没有。
     */
    private static List<String> parseList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<String> out = new ArrayList<>();
            for (JsonNode node : JsonSupport.mapper().readTree(json)) {
                if (node.isString()) {
                    out.add(node.asString());
                }
            }
            return out;
        } catch (RuntimeException malformed) {
            log.warn("模型 modalities 字段不是合法 JSON 数组，按空处理: {}", json, malformed);
            return List.of();
        }
    }

    /** 解析 JSON 字符串映射；空值与非字符串值的行为同 {@link #parseList(String)}。 */
    private static Map<String, String> parseMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            Map<String, String> out = new LinkedHashMap<>();
            for (var field : JsonSupport.mapper().readTree(json).properties()) {
                out.put(field.getKey(), field.getValue().isString()
                        ? field.getValue().asString() : field.getValue().toString());
            }
            return out;
        } catch (RuntimeException malformed) {
            log.warn("模型 params 字段不是合法 JSON 对象，按空处理: {}", json, malformed);
            return Map.of();
        }
    }

    private record Entry(String providerName, AiProvider provider, AiModel model) {
    }
}
