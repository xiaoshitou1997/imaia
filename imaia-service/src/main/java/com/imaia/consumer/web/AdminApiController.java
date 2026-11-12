package com.imaia.consumer.web;

import com.imaia.consumer.ai.AssistantProfileService;
import com.imaia.consumer.adapter.wechat.WechatConnection;
import com.imaia.consumer.ai.PersonAnalyzer;
import com.imaia.consumer.ai.RiskPolicyService;
import com.imaia.consumer.config.ModelRegistry;
import com.imaia.consumer.adapter.ImAdapter;
import com.imaia.consumer.adapter.ImAdapters;
import com.imaia.consumer.domain.AiArtifact;
import com.imaia.consumer.domain.AssistantProfile;
import com.imaia.consumer.domain.AiMessage;
import com.imaia.consumer.domain.Contact;
import com.imaia.consumer.domain.MemoryFact;
import com.imaia.consumer.domain.Model;
import com.imaia.consumer.domain.PersonAnalysis;
import com.imaia.consumer.domain.Provider;
import com.imaia.consumer.domain.RiskPolicy;
import com.imaia.consumer.domain.ScheduledAction;
import com.imaia.consumer.repository.AiArtifactRepository;
import com.imaia.consumer.repository.AiMessageRepository;
import com.imaia.consumer.repository.ContactRepository;
import com.imaia.consumer.repository.MemoryFactRepository;
import com.imaia.consumer.repository.ModelCallLogRepository;
import com.imaia.consumer.repository.ModelRepository;
import com.imaia.consumer.repository.PersonAnalysisRepository;
import com.imaia.consumer.repository.ProcessedMessageRepository;
import com.imaia.consumer.repository.ProviderRepository;
import com.imaia.consumer.repository.ScheduledActionRepository;
import com.imaia.consumer.repository.ImMessageRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The WebUI's control API (loopback only, no authentication — the server binds to
 * 127.0.0.1): directory browsing, message search, artifact and memory management,
 * scheduled-action control, the model directory, assistant/risk settings and
 * per-contact analysis. Every directory/settings mutation takes effect on the next
 * turn without a restart.
 */
@RestController
@RequestMapping("/api")
public class AdminApiController {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final ImAdapters adapters;
    private final ContactRepository contacts;
    private final ImMessageRepository imMessages;
    private final ProcessedMessageRepository processedMessages;
    private final AiMessageRepository aiMessages;
    private final AiArtifactRepository artifacts;
    private final ScheduledActionRepository actions;
    private final MemoryFactRepository memoryFacts;
    private final ModelCallLogRepository callLogs;
    private final PersonAnalysisRepository analyses;
    private final PersonAnalyzer personAnalyzer;
    private final ProviderRepository providers;
    private final ModelRepository models;
    private final ModelRegistry modelRegistry;
    private final AssistantProfileService profileService;
    private final RiskPolicyService riskService;
    private final WechatConnection connection;

    public AdminApiController(ImAdapters adapters, ContactRepository contacts,
            ImMessageRepository imMessages, ProcessedMessageRepository processedMessages,
            AiMessageRepository aiMessages, AiArtifactRepository artifacts,
            ScheduledActionRepository actions, MemoryFactRepository memoryFacts,
            ModelCallLogRepository callLogs, PersonAnalysisRepository analyses,
            PersonAnalyzer personAnalyzer, ProviderRepository providers, ModelRepository models,
            ModelRegistry modelRegistry, AssistantProfileService profileService,
            RiskPolicyService riskService, WechatConnection connection) {
        this.adapters = adapters;
        this.contacts = contacts;
        this.imMessages = imMessages;
        this.processedMessages = processedMessages;
        this.aiMessages = aiMessages;
        this.artifacts = artifacts;
        this.actions = actions;
        this.memoryFacts = memoryFacts;
        this.callLogs = callLogs;
        this.analyses = analyses;
        this.personAnalyzer = personAnalyzer;
        this.providers = providers;
        this.models = models;
        this.modelRegistry = modelRegistry;
        this.profileService = profileService;
        this.riskService = riskService;
        this.connection = connection;
    }

    // ---------- overview ----------

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Instant dayStart = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC);
        long messagesToday = 0;
        long pendingActions = 0;
        for (ImAdapter adapter : adapters.all()) {
            messagesToday += imMessages.countByAccountIdAndArchivedAtAfter(adapter.accountId(), dayStart);
            pendingActions += actions.findTop100ByAccountIdOrderByCreatedAtDesc(adapter.accountId()).stream()
                    .filter(a -> ScheduledAction.STATUS_PENDING.equals(a.getStatus())).count();
        }
        long aiRepliesToday = processedMessages.countByProcessedAtAfter(dayStart);
        long tokensToday = 0;
        long callsToday = 0;
        for (var call : callLogs.findByCreatedAtAfter(dayStart)) {
            callsToday++;
            if (call.getPromptTokens() != null) tokensToday += call.getPromptTokens();
            if (call.getCompletionTokens() != null) tokensToday += call.getCompletionTokens();
        }
        List<Map<String, Object>> recent = new ArrayList<>();
        for (ImAdapter adapter : adapters.all()) {
            for (var message : imMessages.findTop200ByAccountIdOrderByArchivedAtDesc(adapter.accountId())) {
                if (recent.size() >= 12) break;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("threadId", message.getThreadId());
                row.put("senderName", message.getSenderName());
                row.put("direction", message.getDirection());
                row.put("type", message.getProviderType());
                row.put("body", message.getBody() == null ? "" :
                        message.getBody().length() > 60 ? message.getBody().substring(0, 60) + "…" : message.getBody());
                row.put("time", message.getArchivedAt());
                recent.add(row);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("messagesToday", messagesToday);
        out.put("aiRepliesToday", aiRepliesToday);
        out.put("tokensToday", tokensToday);
        out.put("callsToday", callsToday);
        out.put("pendingActions", pendingActions);
        out.put("recent", recent);
        return out;
    }

    // ---------- contacts ----------

    @GetMapping("/contacts")
    public List<Map<String, Object>> contacts() {
        List<Contact> all = new ArrayList<>();
        for (ImAdapter adapter : adapters.all()) {
            all.addAll(contacts.findTop200ByAccountIdOrderByLastSeenAtDesc(adapter.accountId()));
        }
        all.sort(java.util.Comparator.comparing(Contact::getLastSeenAt,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Contact contact : all) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", contact.getId());
            row.put("externalId", contact.getExternalId());
            row.put("displayName", contact.getDisplayName() == null ? contact.getExternalId() : contact.getDisplayName());
            row.put("kind", contact.getKind());
            row.put("persona", contact.getPersona());
            row.put("remark", contact.getRemark());
            row.put("muted", contact.getMuted());
            row.put("lastSeenAt", contact.getLastSeenAt());
            out.add(row);
        }
        return out;
    }

    @PutMapping("/contacts/{id}")
    public ResponseEntity<?> updateContact(@PathVariable Long id, @RequestBody JsonNode body) {
        var contact = contacts.findById(id);
        if (contact.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Contact updated = contact.get();
        if (body.has("persona")) updated.setPersona(textOrNull(body.get("persona")));
        if (body.has("remark")) updated.setRemark(textOrNull(body.get("remark")));
        if (body.has("muted")) updated.setMuted(body.get("muted").asBoolean());
        contacts.save(updated);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asString();
    }

    // ---------- messages ----------

    @GetMapping("/threads/{threadId}/messages")
    public List<Map<String, Object>> threadMessages(@PathVariable String threadId,
            @RequestParam(defaultValue = "100") int limit) {
        List<MessageRow> rows = new ArrayList<>();
        for (ImAdapter adapter : adapters.all()) {
            String accountId = adapter.accountId();
            for (var m : imMessages.findTop200ByAccountIdAndThreadIdOrderByArchivedAtDesc(accountId, threadId)) {
                rows.add(new MessageRow(m.getArchivedAt(), m.getDirection(), m.getSenderId(),
                        m.getSenderName(), m.getProviderType(), m.getBody()));
            }
            for (var m : aiMessages.findTop100ByAccountIdAndThreadIdOrderByCreatedAtDesc(accountId, threadId)) {
                String body = AiMessage.ROLE_TOOL.equals(m.getRole())
                        ? "[工具 " + m.getToolName() + "] " + m.getContent()
                        : m.getContent();
                rows.add(new MessageRow(m.getCreatedAt(), AiMessage.ROLE_ASSISTANT.equals(m.getRole())
                        ? "ai-" + m.getRole() : m.getRole(), "imaia", null, null, body));
            }
        }
        int cap = Math.max(10, Math.min(limit, 300));
        return rows.stream()
                .sorted((a, b) -> a.at().compareTo(b.at()))
                .skip(Math.max(0, rows.size() - cap))
                .map(row -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("time", row.at());
                    item.put("direction", row.direction());
                    item.put("senderId", row.senderId());
                    item.put("senderName", row.senderName());
                    item.put("type", row.type());
                    item.put("body", row.body());
                    return item;
                })
                .toList();
    }

    private record MessageRow(Instant at, String direction, String senderId, String senderName,
            Integer type, String body) {
    }

    // ---------- artifacts ----------

    @GetMapping("/artifacts")
    public List<Map<String, Object>> artifacts() {
        List<AiArtifact> all = new ArrayList<>();
        for (ImAdapter adapter : adapters.all()) {
            all.addAll(artifacts.findTop50ByAccountIdOrderByCreatedAtDesc(adapter.accountId()));
        }
        all.sort(java.util.Comparator.comparing(AiArtifact::getCreatedAt,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiArtifact artifact : all) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", artifact.getId());
            row.put("threadId", artifact.getThreadId());
            row.put("kind", artifact.getKind());
            row.put("prompt", artifact.getPrompt());
            row.put("modelId", artifact.getModelId());
            row.put("filename", artifact.getFilename());
            row.put("status", artifact.getStatus());
            row.put("createdAt", artifact.getCreatedAt());
            out.add(row);
        }
        return out;
    }

    // ---------- scheduled actions ----------

    @GetMapping("/actions")
    public List<Map<String, Object>> actions() {
        List<ScheduledAction> all = new ArrayList<>();
        for (ImAdapter adapter : adapters.all()) {
            all.addAll(actions.findTop100ByAccountIdOrderByCreatedAtDesc(adapter.accountId()));
        }
        all.sort(java.util.Comparator.comparing(ScheduledAction::getCreatedAt,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        List<Map<String, Object>> out = new ArrayList<>();
        for (ScheduledAction action : all) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", action.getId());
            row.put("threadId", action.getThreadId());
            row.put("action", action.getAction());
            row.put("content", action.getContent());
            row.put("prompt", action.getPrompt());
            row.put("fireAt", action.getFireAt());
            row.put("cron", action.getCron());
            row.put("nextFireAt", action.getNextFireAt());
            row.put("status", action.getStatus());
            row.put("createdBy", action.getCreatedBy());
            row.put("lastFiredAt", action.getLastFiredAt());
            out.add(row);
        }
        return out;
    }

    public record NewAction(String platform, String threadId, String action, String content, String prompt,
            Instant fireAt, String cron) {
    }

    @PostMapping("/actions")
    public ResponseEntity<?> createAction(@RequestBody NewAction request) {
        if (request.threadId() == null || request.threadId().isBlank()) {
            return badRequest("threadId is required");
        }
        ImAdapter adapter = request.platform() == null || request.platform().isBlank()
                ? adapters.defaultAdapter()
                : adapters.forPlatform(request.platform());
        String action = request.action() == null ? ScheduledAction.ACTION_SEND_TEXT : request.action();
        if (!ScheduledAction.ACTION_SEND_TEXT.equals(action)
                && !ScheduledAction.ACTION_SEND_AI_PROMPT.equals(action)) {
            return badRequest("action must be send_text or send_ai_prompt");
        }
        if (request.fireAt() == null && (request.cron() == null || request.cron().isBlank())) {
            return badRequest("fireAt or cron is required");
        }
        ScheduledAction saved = actions.save(new ScheduledAction(adapter.accountId(), adapter.platform(),
                request.threadId(), action, request.content(), request.prompt(),
                request.fireAt(), request.cron(), "user", Instant.now()));
        return ResponseEntity.ok(Map.of("id", saved.getId()));
    }

    @PostMapping("/actions/{id}/cancel")
    public ResponseEntity<?> cancelAction(@PathVariable Long id) {
        var action = actions.findById(id);
        if (action.isEmpty()) return ResponseEntity.notFound().build();
        action.get().setStatus(ScheduledAction.STATUS_CANCELLED);
        actions.save(action.get());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/actions/{id}/fire")
    public ResponseEntity<?> fireAction(@PathVariable Long id) {
        var action = actions.findById(id);
        if (action.isEmpty()) return ResponseEntity.notFound().build();
        action.get().setFireAt(Instant.now().minusSeconds(1));
        action.get().setStatus(ScheduledAction.STATUS_PENDING);
        actions.save(action.get());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ---------- memories ----------

    @GetMapping("/memories")
    public List<Map<String, Object>> memories() {
        List<MemoryFact> all = new ArrayList<>();
        for (ImAdapter adapter : adapters.all()) {
            all.addAll(memoryFacts.findTop200ByAccountIdOrderByCreatedAtDesc(adapter.accountId()));
        }
        all.sort(java.util.Comparator.comparing(MemoryFact::getCreatedAt,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        List<Map<String, Object>> out = new ArrayList<>();
        for (MemoryFact fact : all) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", fact.getId());
            row.put("scopeType", fact.getScopeType());
            row.put("scopeId", fact.getScopeId());
            row.put("kind", fact.getKind());
            row.put("fact", fact.getFact());
            row.put("status", fact.getStatus());
            row.put("supersedesId", fact.getSupersedesId());
            row.put("createdAt", fact.getCreatedAt());
            out.add(row);
        }
        return out;
    }

    @PutMapping("/memories/{id}")
    public ResponseEntity<?> updateMemory(@PathVariable Long id, @RequestBody JsonNode body) {
        var fact = memoryFacts.findById(id);
        if (fact.isEmpty()) return ResponseEntity.notFound().build();
        if (body.has("fact") && !body.get("fact").asString().isBlank()) {
            fact.get().setFact(body.get("fact").asString());
        }
        memoryFacts.save(fact.get());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/memories/{id}")
    public ResponseEntity<?> deleteMemory(@PathVariable Long id) {
        if (!memoryFacts.existsById(id)) return ResponseEntity.notFound().build();
        memoryFacts.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/memories/{id}/promote")
    public ResponseEntity<?> promoteMemory(@PathVariable Long id) {
        var fact = memoryFacts.findById(id);
        if (fact.isEmpty()) return ResponseEntity.notFound().build();
        fact.get().setScopeType("global");
        fact.get().setScopeId(null);
        memoryFacts.save(fact.get());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ---------- model call log ----------

    @GetMapping("/calls")
    public List<Map<String, Object>> calls() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var call : callLogs.findTop100ByOrderByCreatedAtDesc()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("threadId", call.getThreadId());
            row.put("purpose", call.getPurpose());
            row.put("providerName", call.getProviderName());
            row.put("modelId", call.getModelId());
            row.put("promptTokens", call.getPromptTokens());
            row.put("completionTokens", call.getCompletionTokens());
            row.put("durationMs", call.getDurationMs());
            row.put("ok", call.getOk());
            row.put("error", call.getError());
            row.put("createdAt", call.getCreatedAt());
            out.add(row);
        }
        return out;
    }

    // ---------- model directory ----------

    @GetMapping("/providers")
    public List<Map<String, Object>> providers() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Provider provider : providers.findAll()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", provider.getId());
            row.put("name", provider.getName());
            row.put("baseUrl", provider.getBaseUrl());
            row.put("apiKeyMasked", mask(provider.getApiKey()));
            row.put("enabled", provider.getEnabled());
            row.put("models", models.findByProviderId(provider.getId()).stream()
                    .map(this::modelView).toList());
            out.add(row);
        }
        return out;
    }

    private Map<String, Object> modelView(Model model) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", model.getId());
        row.put("providerId", model.getProviderId());
        row.put("modelId", model.getModelId());
        row.put("name", model.getName());
        row.put("protocol", model.getProtocol());
        row.put("contextWindow", model.getContextWindow());
        row.put("inputModalities", ModelRegistryAccess.parseList(model.getInputModalities()));
        row.put("outputModalities", ModelRegistryAccess.parseList(model.getOutputModalities()));
        row.put("maxOutputTokens", model.getMaxOutputTokens());
        row.put("params", ModelRegistryAccess.parseMap(model.getParams()));
        row.put("enabled", model.getEnabled());
        return row;
    }

    /** Read access to the model JSON helpers without widening the registry API. */
    static final class ModelRegistryAccess {
        private static final JsonMapper MAPPER = JsonMapper.builder().build();

        static List<String> parseList(String json) {
            List<String> out = new ArrayList<>();
            for (JsonNode node : MAPPER.readTree(json == null || json.isBlank() ? "[]" : json)) {
                if (node.isString()) out.add(node.asString());
            }
            return out;
        }

        static Map<String, String> parseMap(String json) {
            Map<String, String> out = new LinkedHashMap<>();
            for (var field : MAPPER.readTree(json == null || json.isBlank() ? "{}" : json).properties()) {
                out.put(field.getKey(), field.getValue().isString()
                        ? field.getValue().asString() : field.getValue().toString());
            }
            return out;
        }

        static String toJson(List<String> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append('"').append(list.get(i)).append('"');
            }
            return sb.append(']').toString();
        }

        static String toJson(Map<String, String> map) {
            StringBuilder sb = new StringBuilder("{");
            int i = 0;
            for (var entry : map.entrySet()) {
                if (i++ > 0) sb.append(',');
                sb.append('"').append(entry.getKey()).append("\":\"")
                        .append(entry.getValue()).append('"');
            }
            return sb.append('}').toString();
        }

        private ModelRegistryAccess() {
        }
    }

    public record NewProvider(String name, String baseUrl, String apiKey, Boolean enabled) {
    }

    @PostMapping("/providers")
    public ResponseEntity<?> createProvider(@RequestBody NewProvider request) {
        if (request.name() == null || request.name().isBlank()
                || request.baseUrl() == null || request.baseUrl().isBlank()) {
            return badRequest("name and baseUrl are required");
        }
        if (providers.findByName(request.name()).isPresent()) {
            return badRequest("provider '" + request.name() + "' already exists");
        }
        Instant now = Instant.now();
        Provider provider = new Provider(request.name().trim(), request.baseUrl().trim(),
                request.apiKey() == null ? "" : request.apiKey(), now);
        provider.setEnabled(request.enabled() == null || request.enabled());
        Provider saved = providers.save(provider);
        modelRegistry.invalidate();
        return ResponseEntity.ok(Map.of("id", saved.getId()));
    }

    public record UpdateProvider(String baseUrl, String apiKey, Boolean enabled) {
    }

    @PutMapping("/providers/{id}")
    public ResponseEntity<?> updateProvider(@PathVariable Long id, @RequestBody UpdateProvider request) {
        var provider = providers.findById(id);
        if (provider.isEmpty()) return ResponseEntity.notFound().build();
        Provider updated = provider.get();
        if (request.baseUrl() != null && !request.baseUrl().isBlank()) updated.setBaseUrl(request.baseUrl().trim());
        if (request.apiKey() != null && !request.apiKey().isBlank()) updated.setApiKey(request.apiKey());
        if (request.enabled() != null) updated.setEnabled(request.enabled());
        updated.setUpdatedAt(Instant.now());
        providers.save(updated);
        modelRegistry.invalidate();
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/providers/{id}")
    public ResponseEntity<?> deleteProvider(@PathVariable Long id) {
        if (!models.findByProviderId(id).isEmpty()) {
            return badRequest("provider still has models; delete them first");
        }
        providers.deleteById(id);
        modelRegistry.invalidate();
        return ResponseEntity.ok(Map.of("ok", true));
    }

    public record NewModel(String modelId, String name, String protocol, Integer contextWindow,
            List<String> inputModalities, List<String> outputModalities, Integer maxOutputTokens,
            Map<String, String> params, Boolean enabled) {
    }

    @PostMapping("/providers/{id}/models")
    public ResponseEntity<?> createModel(@PathVariable Long id, @RequestBody NewModel request) {
        if (providers.findById(id).isEmpty()) return ResponseEntity.notFound().build();
        if (request.modelId() == null || request.modelId().isBlank()
                || request.protocol() == null || request.protocol().isBlank()) {
            return badRequest("modelId and protocol are required");
        }
        Model saved = models.save(new Model(id, request.modelId().trim(), request.name(),
                request.protocol().trim(), request.contextWindow(),
                ModelRegistryAccess.toJson(request.inputModalities() == null ? List.of() : request.inputModalities()),
                ModelRegistryAccess.toJson(request.outputModalities() == null ? List.of() : request.outputModalities()),
                request.maxOutputTokens(),
                ModelRegistryAccess.toJson(request.params() == null ? Map.of() : request.params()),
                Instant.now()));
        modelRegistry.invalidate();
        return ResponseEntity.ok(Map.of("id", saved.getId()));
    }

    @DeleteMapping("/models/{id}")
    public ResponseEntity<?> deleteModel(@PathVariable Long id) {
        if (!models.existsById(id)) return ResponseEntity.notFound().build();
        models.deleteById(id);
        modelRegistry.invalidate();
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ---------- settings ----------

    @GetMapping("/settings/profile")
    public AssistantProfile profile() {
        return profileService.current();
    }

    public record UpdateProfile(String systemPrompt, Integer historyLimit, String replyThreads,
            Boolean requireGroupMention, Boolean ttsReply, Integer minMessageLength,
            Long replyDelayMinMs, Long replyDelayMaxMs, Boolean enabled) {
    }

    @PutMapping("/settings/profile")
    public Map<String, Object> updateProfile(@RequestBody UpdateProfile request) {
        AssistantProfile profile = profileService.current();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            profile.setSystemPrompt(request.systemPrompt());
        }
        if (request.historyLimit() != null && request.historyLimit() >= 2) profile.setHistoryLimit(request.historyLimit());
        if (request.replyThreads() != null) profile.setReplyThreads(request.replyThreads());
        if (request.requireGroupMention() != null) profile.setRequireGroupMention(request.requireGroupMention());
        if (request.ttsReply() != null) profile.setTtsReply(request.ttsReply());
        if (request.minMessageLength() != null && request.minMessageLength() >= 0) {
            profile.setMinMessageLength(request.minMessageLength());
        }
        if (request.replyDelayMinMs() != null && request.replyDelayMinMs() >= 0) {
            profile.setReplyDelayMinMs(request.replyDelayMinMs());
        }
        if (request.replyDelayMaxMs() != null && request.replyDelayMaxMs() >= 0) {
            profile.setReplyDelayMaxMs(request.replyDelayMaxMs());
        }
        if (request.enabled() != null) profile.setEnabled(request.enabled());
        profileService.save(profile);
        return Map.of("ok", true);
    }

    @GetMapping("/settings/risk")
    public RiskPolicy risk() {
        return riskService.current();
    }

    public record UpdateRisk(Integer repliesPerThreadPerMinute, Integer maxRepliesPerHour,
            Integer maxRepliesPerDay, String quietHoursStart, String quietHoursEnd) {
    }

    @PutMapping("/settings/risk")
    public Map<String, Object> updateRisk(@RequestBody UpdateRisk request) {
        RiskPolicy policy = riskService.current();
        if (request.repliesPerThreadPerMinute() != null && request.repliesPerThreadPerMinute() >= 1) {
            policy.setRepliesPerThreadPerMinute(request.repliesPerThreadPerMinute());
        }
        if (request.maxRepliesPerHour() != null && request.maxRepliesPerHour() >= 1) {
            policy.setMaxRepliesPerHour(request.maxRepliesPerHour());
        }
        if (request.maxRepliesPerDay() != null && request.maxRepliesPerDay() >= 1) {
            policy.setMaxRepliesPerDay(request.maxRepliesPerDay());
        }
        if (request.quietHoursStart() != null && !request.quietHoursStart().isBlank()) {
            policy.setQuietHoursStart(request.quietHoursStart());
        }
        if (request.quietHoursEnd() != null && !request.quietHoursEnd().isBlank()) {
            policy.setQuietHoursEnd(request.quietHoursEnd());
        }
        riskService.save(policy);
        return Map.of("ok", true);
    }

    @GetMapping("/settings/runtime")
    public Map<String, Object> runtime() {
        var daemon = connection.config();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("daemonBaseUrl", daemon.baseUrl());
        out.put("daemonAccount", daemon.account());
        out.put("daemonTokenMasked", mask(daemon.token()));
        out.put("note", "接入配置存于 platform_account 表（config JSONB，含密钥），修改后需重启生效");
        return out;
    }

    // ---------- analysis ----------

    @GetMapping("/analysis/{contactId}")
    public ResponseEntity<?> analysis(@PathVariable Long contactId) {
        var contact = contacts.findById(contactId);
        if (contact.isEmpty()) return ResponseEntity.notFound().build();
        var cached = analyses.findByAccountIdAndContactId(contact.get().getAccountId(), contactId);
        return cached.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/analysis/{contactId}")
    public ResponseEntity<?> runAnalysis(@PathVariable Long contactId) {
        var contact = contacts.findById(contactId);
        if (contact.isEmpty()) return ResponseEntity.notFound().build();
        String accountId = contact.get().getAccountId();
        String target = contact.get().getDisplayName() == null || contact.get().getDisplayName().isBlank()
                ? contact.get().getExternalId() : contact.get().getDisplayName();
        String profile = personAnalyzer.analyze(target);
        if (profile == null) {
            return ResponseEntity.ok(Map.of("profile", "归档里没有找到与「" + target + "」相关的消息。"));
        }
        Instant now = Instant.now();
        PersonAnalysis analysis = analyses.findByAccountIdAndContactId(accountId, contactId)
                .orElseGet(() -> new PersonAnalysis(accountId, contactId, profile,
                        null, null, now));
        analysis.setProfile(profile);
        analysis.setGeneratedAt(now);
        analyses.save(analysis);
        return ResponseEntity.ok(Map.of("profile", profile));
    }

    // ---------- helpers ----------

    private static String mask(String secret) {
        if (secret == null || secret.isBlank()) return "";
        String tail = secret.length() <= 4 ? secret : secret.substring(secret.length() - 4);
        return "•••" + tail;
    }

    private static ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

}
