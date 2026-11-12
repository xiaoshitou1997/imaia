package com.imaia.config;

import com.imaia.domain.Model;
import com.imaia.domain.Provider;
import com.imaia.repository.ModelRepository;
import com.imaia.repository.ProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The model directory at runtime, loaded from the provider/model tables into a
 * short-lived snapshot: openai-images / openai-video / openai-audio-speech /
 * openai-audio-transcription pick the matching tool by protocol, and chat turns
 * pick among the openai-completions models by need — a turn with an image prefers
 * a model that declares image input; with several candidates the directory must be
 * trimmed until the choice is unambiguous. The WebUI saves directory rows and
 * calls invalidate(), so an edit takes effect on the next turn.
 */
@Component
public class ModelRegistry {

    public static final String PROTOCOL_CHAT = "openai-completions";
    public static final String PROTOCOL_IMAGES = "openai-images";
    public static final String PROTOCOL_VIDEO = "openai-video";
    public static final String PROTOCOL_SPEECH = "openai-audio-speech";
    public static final String PROTOCOL_TRANSCRIPTION = "openai-audio-transcription";

    /** A model resolved from the directory together with its provider connection. */
    public record Resolved(String providerName, String baseUrl, String apiKey,
            String id, String name, String protocol, Integer contextWindow,
            List<String> inputModalities, List<String> outputModalities,
            Integer maxOutputTokens, Map<String, String> params) {

        public boolean seesImages() {
            return inputModalities != null && inputModalities.contains("image");
        }

        public String param(String key) {
            return params == null ? null : params.get(key);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(ModelRegistry.class);

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final ProviderRepository providers;
    private final ModelRepository models;
    private volatile List<Entry> cache;

    public ModelRegistry(ProviderRepository providers, ModelRepository models) {
        this.providers = providers;
        this.models = models;
    }

    /** Drops the cached snapshot; the next resolution reloads from the tables. */
    public synchronized void invalidate() {
        cache = null;
    }

    /**
     * The chat model for one turn. Image turns prefer models declaring image input;
     * when no declaration covers the need, the only chat model is used anyway so the
     * message is still handled; ambiguity fails fast.
     */
    public Resolved chatFor(boolean withImage) {
        List<Entry> chats = entries().stream()
                .filter(e -> e.model().speaksProtocol(PROTOCOL_CHAT))
                .toList();
        if (chats.isEmpty()) {
            throw new IllegalStateException("No chat model in the directory: enable at least one "
                    + "model with protocol " + PROTOCOL_CHAT + ".");
        }
        if (withImage) {
            List<Entry> capable = chats.stream().filter(ModelRegistry::seesImages).toList();
            if (capable.size() == 1) {
                return resolved(capable.get(0));
            }
            if (capable.size() > 1) {
                throw ambiguous(capable, "image-capable chat models");
            }
            log.warn("No chat model declares image input; sending the image to the plain chat model anyway.");
        }
        if (chats.size() == 1) {
            return resolved(chats.get(0));
        }
        throw ambiguous(chats, "chat models");
    }

    /** Whether at least one enabled chat model declares image input. */
    public boolean chatSupportsImages() {
        return entries().stream()
                .anyMatch(e -> e.model().speaksProtocol(PROTOCOL_CHAT) && seesImages(e));
    }

    /** Whether any enabled model speaks the given protocol. */
    public boolean hasProtocol(String protocol) {
        return entries().stream().anyMatch(e -> e.model().speaksProtocol(protocol));
    }

    /** The one enabled model speaking the given protocol, across every provider. */
    public Resolved byProtocol(String protocol) {
        List<Entry> matches = entries().stream()
                .filter(e -> e.model().speaksProtocol(protocol))
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalStateException("No enabled model speaks protocol '" + protocol
                    + "' in the directory; declare one to enable this capability.");
        }
        if (matches.size() > 1) {
            throw ambiguous(matches, protocol + " models");
        }
        return resolved(matches.get(0));
    }

    private IllegalStateException ambiguous(List<Entry> matches, String what) {
        String found = matches.stream()
                .map(e -> e.providerName() + "/" + e.model().getModelId())
                .collect(Collectors.joining(", "));
        return new IllegalStateException("Expected exactly one " + what + ", found "
                + matches.size() + ": " + found + ". Trim the directory or fix input-modalities.");
    }

    private static boolean seesImages(Entry e) {
        return parseList(e.model().getInputModalities()).contains("image");
    }

    private Resolved resolved(Entry entry) {
        Model model = entry.model();
        return new Resolved(entry.providerName(),
                normalize(entry.provider().getBaseUrl()), entry.provider().getApiKey(),
                model.getModelId(), model.getName(), model.getProtocol(),
                model.getContextWindow(), parseList(model.getInputModalities()),
                parseList(model.getOutputModalities()), model.getMaxOutputTokens(),
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
        Map<Long, Provider> providerById = new LinkedHashMap<>();
        for (Provider provider : providers.findByEnabledTrue()) {
            providerById.put(provider.getId(), provider);
        }
        List<Entry> entries = new ArrayList<>();
        for (Model model : models.findByEnabledTrue()) {
            Provider provider = providerById.get(model.getProviderId());
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
            for (JsonNode node : MAPPER.readTree(json == null || json.isBlank() ? "[]" : json)) {
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
            for (var field : MAPPER.readTree(json == null || json.isBlank() ? "{}" : json).properties()) {
                out.put(field.getKey(), field.getValue().isString()
                        ? field.getValue().asString() : field.getValue().toString());
            }
            return out;
        } catch (RuntimeException failure) {
            return Map.of();
        }
    }

    private record Entry(String providerName, Provider provider, Model model) {
    }
}