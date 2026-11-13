package com.imaia.config;

import com.imaia.domain.AiModel;
import com.imaia.domain.AiProvider;
import com.imaia.repository.AiModelRepository;
import com.imaia.repository.AiProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;
import java.util.stream.Collectors;

/**
 * The AI model directory at runtime, loaded from the ai_provider/ai_model tables
 * into a short-lived snapshot. Capability slots derive from data, not columns:
 * chat = enabled + tool_call + text in/out (vision-capable variants preferred for
 * image turns), image generation = image in modalities_out, video = video out.
 * The WebUI saves directory rows and calls invalidate(), so an edit takes effect
 * on the next turn. Several candidates for one slot fail fast — trim the directory.
 */
@Component
public class AiModelCatalog {

    /** One model resolved from the directory together with its provider connection. */
    public record Resolved(String providerName, String baseUrl, String apiKey,
            String id, String name, Integer maxOutputTokens, Map<String, String> params) {

        public String modelId() {
            return id;
        }

        /** Single connection-time parameter from params JSON ("{}" when unset). */
        public String param(String key) {
            return params == null ? null : params.get(key);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(AiModelCatalog.class);

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final AiProviderRepository providers;
    private final AiModelRepository models;
    private volatile List<Entry> cache;

    public AiModelCatalog(AiProviderRepository providers, AiModelRepository models) {
        this.providers = providers;
        this.models = models;
    }

    /** Drops the cached snapshot; the next resolution reloads from the tables. */
    public synchronized void invalidate() {
        cache = null;
    }

    /** 对话模型：enabled + tool_call + 文本进出；带图轮偏好 vision（见附件/图片输入）。 */
    public Resolved chatFor(boolean withImage) {
        List<Entry> chats = chatEntries();
        if (chats.isEmpty()) {
            throw new IllegalStateException("No chat model in the directory: enable one with "
                + "modalities in/out containing text and tool_call on.");
        }
        if (withImage) {
            List<Entry> capable = chats.stream().filter(e -> in(e).contains("image")).toList();
            if (capable.size() == 1) {
                return resolved(capable.get(0));
            }
            if (capable.size() > 1) {
                throw ambiguous(capable, "vision-capable chat models");
            }
            log.warn("No enabled chat model declares image input; sending the image to a plain chat model anyway.");
        }
        if (chats.size() == 1) {
            return resolved(chats.get(0));
        }
        throw ambiguous(chats, "chat models");
    }

    /** Whether at least one enabled chat model exists. */
    public boolean hasChat() {
        return !chatEntries().isEmpty();
    }

    /** Whether the chat slot can see images (vision). */
    public boolean hasVisionChat() {
        return chatEntries().stream().anyMatch(e -> in(e).contains("image"));
    }

    /** Whether image generation is available (a model with image in modalities_out). */
    public boolean hasImageGeneration() {
        return !generationEntries("image").isEmpty();
    }

    /** Whether video generation is available. */
    public boolean hasVideoGeneration() {
        return !generationEntries("video").isEmpty();
    }

    /** The one enabled model generating images. */
    public Resolved imageGeneration() {
        return single(generationEntries("image"), "image-generation models");
    }

    /** The one enabled model generating videos. */
    public Resolved videoGeneration() {
        return single(generationEntries("video"), "video-generation models");
    }

    private List<Entry> chatEntries() {
        return entries().stream()
                .filter(e -> e.model().isEnabled() && e.model().isToolCall()
                        && out(e).contains("text") && in(e).contains("text"))
                .toList();
    }

    private List<Entry> generationEntries(String modality) {
        return entries().stream()
                .filter(e -> e.model().isEnabled() && out(e).contains(modality))
                .toList();
    }

    private Resolved single(List<Entry> matches, String what) {
        if (matches.isEmpty()) {
            throw new IllegalStateException("No enabled model for '" + what
                    + "' in the directory; sync a provider and enable one.");
        }
        if (matches.size() > 1) {
            throw ambiguous(matches, what);
        }
        return resolved(matches.get(0));
    }

    private IllegalStateException ambiguous(List<Entry> matches, String what) {
        String found = matches.stream()
                .map(e -> e.providerName() + "/" + e.model().getModelId())
                .collect(Collectors.joining(", "));
        return new IllegalStateException("Expected exactly one " + what + ", found "
                + matches.size() + ": " + found + ". Trim the directory.");
    }

    private static List<String> in(Entry e) {
        return parseList(e.model().getModalitiesIn());
    }

    private static List<String> out(Entry e) {
        return parseList(e.model().getModalitiesOut());
    }

    private Resolved resolved(Entry entry) {
        AiModel model = entry.model();
        return new Resolved(entry.providerName(),
                normalize(entry.provider().getBaseUrl()), entry.provider().getApiKey(),
                model.getModelId(), model.getName(), model.getLimitOutput(),
                parseMap(model.getParams()));
    }

    private List<Entry> entries() {
        List<Entry> snapshot = cache;
        if (snapshot == null) {
            snapshot = load();
            cache = snapshot;
        }
        return snapshot;
    }

    private List<Entry> load() {
        Map<Long, AiProvider> providerById = new LinkedHashMap<>();
        for (AiProvider provider : providers.findByEnabledTrue()) {
            providerById.put(provider.getId(), provider);
        }
        List<Entry> entries = new ArrayList<>();
        for (AiModel model : models.findByEnabledTrue()) {
            AiProvider provider = providerById.get(model.getProviderId());
            if (provider != null) {
                entries.add(new Entry(provider.getName(), provider, model));
            }
        }
        return List.copyOf(entries);
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
