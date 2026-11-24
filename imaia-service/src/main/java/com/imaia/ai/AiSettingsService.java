package com.imaia.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.StringNode;
import com.imaia.domain.AiSetting;
import com.imaia.domain.AiSettings;
import com.imaia.domain.ImAccount;
import com.imaia.domain.Platform;
import com.imaia.domain.ImConversation;
import com.imaia.repository.AiSettingRepository;
import com.imaia.repository.ImAccountRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Capability settings over the {@code ai_setting} KV table. Each row carries
 * nullable scope identifiers — platform_code, account_id, conversation_id; all
 * three NULL is global. Scoped keys (persona, autoreply) resolve
 * conversation &gt; connection &gt; platform &gt; global &gt; code default;
 * global-only keys read global rows and ignore scoped rows by design. The reply
 * pipeline reads resolved views per event, the WebUI saves through here, and a
 * save takes effect on the very next turn.
 */
@Component
public class AiSettingsService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Seed prompt, reused as the code-side default when the key is missing. */
    static final String DEFAULT_SYSTEM_PROMPT = "你是用户的智能助理，用简洁口语化的中文回复，不要用列表。"
            + "需要画图、生成视频、搜索网络信息、设置提醒或记住重要事实时，直接调用对应的工具完成。";

    private enum Kind {
        STRING, INT, BOOL
    }

    /** Known keys; {@code globalOnly} keys ignore scoped rows by design. */
    private enum Key {
        PERSONA("persona", false, Kind.STRING, null),
        AUTOREPLY("autoreply", false, Kind.BOOL, Boolean.FALSE),
        SYSTEM_PROMPT("system_prompt", true, Kind.STRING, DEFAULT_SYSTEM_PROMPT),
        HISTORY_LIMIT("history_limit", true, Kind.INT, 20),
        REQUIRE_GROUP_MENTION("require_group_mention", true, Kind.BOOL, Boolean.TRUE),
        QUIET_HOURS_ENABLED("quiet_hours_enabled", true, Kind.BOOL, Boolean.TRUE),
        QUIET_HOURS_START("quiet_hours_start", true, Kind.STRING, "00:00"),
        QUIET_HOURS_END("quiet_hours_end", true, Kind.STRING, "07:00"),
        TIMEZONE("timezone", true, Kind.STRING, "Asia/Shanghai"),
        ENABLED("enabled", true, Kind.BOOL, Boolean.FALSE),
        WEB_SEARCH_BASE_URL("web_search.base_url", true, Kind.STRING, "https://api.tavily.com"),
        WEB_SEARCH_API_KEY("web_search.api_key", true, Kind.STRING, null),
        IMAGE_BASE_URL("image.base_url", true, Kind.STRING, null),
        IMAGE_API_KEY("image.api_key", true, Kind.STRING, null),
        IMAGE_MODEL("image.model", true, Kind.STRING, null),
        VIDEO_BASE_URL("video.base_url", true, Kind.STRING, null),
        VIDEO_API_KEY("video.api_key", true, Kind.STRING, null),
        VIDEO_MODEL("video.model", true, Kind.STRING, null);

        static final Map<String, Key> BY_NAME = new HashMap<>();

        static {
            for (Key key : values()) {
                BY_NAME.put(key.name, key);
            }
        }

        final String name;
        final boolean globalOnly;
        final Kind kind;
        final Object fallback;

        Key(String name, boolean globalOnly, Kind kind, Object fallback) {
            this.name = name;
            this.globalOnly = globalOnly;
            this.kind = kind;
            this.fallback = fallback;
        }
    }

    /** One conversation line's resolved capability caps, for the directory listing. */
    public record LineCaps(String persona, boolean autoreply) {
        public static final LineCaps DEFAULT = new LineCaps(null, false);
    }

    private final AiSettingRepository repository;
    private final ImAccountRepository accounts;
    private volatile AiSettings cachedGlobal;

    public AiSettingsService(AiSettingRepository repository, ImAccountRepository accounts) {
        this.repository = repository;
        this.accounts = accounts;
    }

    /** Global view: global rows + code defaults; no scoped keys applied. */
    public synchronized AiSettings current() {
        AiSettings current = cachedGlobal;
        if (current == null) {
            current = view(globalValues());
            cachedGlobal = current;
        }
        return current;
    }

    /**
     * Resolved view for one conversation line: global rows first, then platform,
     * connection and conversation rows overwrite with increasing specificity.
     */
    public AiSettings forConversation(ImConversation conversation) {
        Map<String, String> values = globalValues();
        Platform platform = accounts.findById(conversation.getAccountId())
                .map(ImAccount::getPlatform).orElse(null);
        if (platform != null) {
            collect(repository.findByPlatformAndAccountIdIsNullAndConversationIdIsNull(platform), values, false);
        }
        collect(repository.findByAccountIdAndConversationIdIsNull(conversation.getAccountId()), values, false);
        collect(repository.findByConversationId(conversation.getId()), values, false);
        return view(values);
    }

    /** Upsert the global keys; scoped rows and unknown keys are untouched. */
    public synchronized AiSettings save(AiSettings settings) {
        Map<String, AiSetting> rows = new HashMap<>();
        for (AiSetting row : repository.findByPlatformIsNullAndAccountIdIsNullAndConversationIdIsNull()) {
            rows.put(row.getKey(), row);
        }
        Instant now = Instant.now();
        List<AiSetting> out = new ArrayList<>();
        upsert(rows, out, Key.SYSTEM_PROMPT, StringNode.valueOf(settings.getSystemPrompt()), now);
        upsert(rows, out, Key.HISTORY_LIMIT, IntNode.valueOf(settings.getHistoryLimit()), now);
        upsert(rows, out, Key.REQUIRE_GROUP_MENTION, BooleanNode.valueOf(settings.isRequireGroupMention()), now);
        upsert(rows, out, Key.QUIET_HOURS_ENABLED, BooleanNode.valueOf(settings.isQuietHoursEnabled()), now);
        upsert(rows, out, Key.QUIET_HOURS_START, StringNode.valueOf(settings.getQuietHoursStart()), now);
        upsert(rows, out, Key.QUIET_HOURS_END, StringNode.valueOf(settings.getQuietHoursEnd()), now);
        upsert(rows, out, Key.TIMEZONE, StringNode.valueOf(settings.getTimezone()), now);
        upsert(rows, out, Key.ENABLED, BooleanNode.valueOf(settings.isEnabled()), now);
        upsert(rows, out, Key.WEB_SEARCH_BASE_URL, StringNode.valueOf(orEmpty(settings.getWebSearchBaseUrl())), now);
        upsert(rows, out, Key.WEB_SEARCH_API_KEY, StringNode.valueOf(orEmpty(settings.getWebSearchApiKey())), now);
        upsert(rows, out, Key.IMAGE_BASE_URL, StringNode.valueOf(orEmpty(settings.getImageBaseUrl())), now);
        upsert(rows, out, Key.IMAGE_API_KEY, StringNode.valueOf(orEmpty(settings.getImageApiKey())), now);
        upsert(rows, out, Key.IMAGE_MODEL, StringNode.valueOf(orEmpty(settings.getImageModel())), now);
        upsert(rows, out, Key.VIDEO_BASE_URL, StringNode.valueOf(orEmpty(settings.getVideoBaseUrl())), now);
        upsert(rows, out, Key.VIDEO_API_KEY, StringNode.valueOf(orEmpty(settings.getVideoApiKey())), now);
        upsert(rows, out, Key.VIDEO_MODEL, StringNode.valueOf(orEmpty(settings.getVideoModel())), now);
        repository.saveAll(out);
        Map<String, String> values = new LinkedHashMap<>();
        for (AiSetting row : out) {
            values.put(row.getKey(), decode(row));
        }
        AiSettings saved = view(values);
        cachedGlobal = saved;
        return saved;
    }

    /** Upsert the line-level persona; null/blank clears the row (falls back outward). */
    public void saveConversationPersona(Long conversationId, String persona) {
        AiSetting existing = rowOf(conversationId, Key.PERSONA);
        if (!StringUtils.hasText(persona)) {
            if (existing != null) {
                repository.delete(existing);
            }
            return;
        }
        writeLine(existing, Key.PERSONA, conversationId, StringNode.valueOf(persona.strip()).toString());
    }

    /** Upsert the line-level autoreply switch; explicit rows also override inheritance. */
    public void saveConversationAutoreply(Long conversationId, boolean autoreply) {
        writeLine(rowOf(conversationId, Key.AUTOREPLY), Key.AUTOREPLY, conversationId,
                BooleanNode.valueOf(autoreply).toString());
    }

    /** Resolved (persona, autoreply) per conversation id, for the directory listing. */
    public Map<Long, LineCaps> lineSettings(Collection<Long> conversationIds) {
        if (conversationIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, LineCaps> out = new HashMap<>();
        for (AiSetting row : repository.findByConversationIdIn(conversationIds)) {
            LineCaps caps = out.getOrDefault(row.getConversationId(), LineCaps.DEFAULT);
            if (Key.AUTOREPLY.name.equals(row.getKey())) {
                caps = new LineCaps(caps.persona(), readBool(row));
            } else if (Key.PERSONA.name.equals(row.getKey())) {
                caps = new LineCaps(readText(row), caps.autoreply());
            }
            out.put(row.getConversationId(), caps);
        }
        return out;
    }

    private Map<String, String> globalValues() {
        Map<String, String> values = new LinkedHashMap<>();
        collect(repository.findByPlatformIsNullAndAccountIdIsNullAndConversationIdIsNull(), values, true);
        return values;
    }

    /** Rows → decoded values; scoped collects skip global-only keys. */
    private static void collect(List<AiSetting> rows, Map<String, String> into, boolean globalLevel) {
        for (AiSetting row : rows) {
            Key key = Key.BY_NAME.get(row.getKey());
            if (key == null || (!globalLevel && key.globalOnly)) {
                continue; // unknown future keys and misplaced global-only rows are ignored
            }
            into.put(key.name, decode(row));
        }
    }

    private AiSettings view(Map<String, String> values) {
        return AiSettings.builder()
                .persona(values.get(Key.PERSONA.name))
                .autoreply(booleanOf(values, Key.AUTOREPLY))
                .systemPrompt(stringOf(values, Key.SYSTEM_PROMPT))
                .historyLimit(intOf(values, Key.HISTORY_LIMIT))
                .requireGroupMention(booleanOf(values, Key.REQUIRE_GROUP_MENTION))
                .quietHoursEnabled(booleanOf(values, Key.QUIET_HOURS_ENABLED))
                .quietHoursStart(stringOf(values, Key.QUIET_HOURS_START))
                .quietHoursEnd(stringOf(values, Key.QUIET_HOURS_END))
                .timezone(stringOf(values, Key.TIMEZONE))
                .enabled(booleanOf(values, Key.ENABLED))
                .webSearchBaseUrl(stringOf(values, Key.WEB_SEARCH_BASE_URL))
                .webSearchApiKey(stringOf(values, Key.WEB_SEARCH_API_KEY))
                .imageBaseUrl(stringOf(values, Key.IMAGE_BASE_URL))
                .imageApiKey(stringOf(values, Key.IMAGE_API_KEY))
                .imageModel(stringOf(values, Key.IMAGE_MODEL))
                .videoBaseUrl(stringOf(values, Key.VIDEO_BASE_URL))
                .videoApiKey(stringOf(values, Key.VIDEO_API_KEY))
                .videoModel(stringOf(values, Key.VIDEO_MODEL))
                .updatedAt(Instant.now())
                .build();
    }

    private static void upsert(Map<String, AiSetting> rows, List<AiSetting> out, Key key,
            JsonNode value, Instant now) {
        AiSetting row = rows.get(key.name);
        if (row == null) {
            row = AiSetting.builder().key(key.name).updatedAt(now).build();
        }
        row.setValue(value.toString());
        row.setUpdatedAt(now);
        out.add(row);
    }

    private AiSetting rowOf(Long conversationId, Key key) {
        return repository.findByConversationId(conversationId).stream()
                .filter(row -> key.name.equals(row.getKey())).findFirst().orElse(null);
    }

    private void writeLine(AiSetting existing, Key key, Long conversationId, String json) {
        if (existing == null) {
            existing = AiSetting.builder().key(key.name).conversationId(conversationId)
                    .updatedAt(Instant.now()).build();
        }
        existing.setValue(json);
        existing.setUpdatedAt(Instant.now());
        repository.save(existing);
    }

    private static String stringOf(Map<String, String> values, Key key) {
        String value = values.get(key.name);
        return value != null ? value : (String) key.fallback;
    }

    /** KV values are never null on disk: unconfigured tool keys persist as "". */
    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int intOf(Map<String, String> values, Key key) {
        String value = values.get(key.name);
        if (value == null) {
            return (Integer) key.fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException malformed) {
            return (Integer) key.fallback;
        }
    }

    private static boolean booleanOf(Map<String, String> values, Key key) {
        String value = values.get(key.name);
        return value != null ? Boolean.parseBoolean(value) : (Boolean) key.fallback;
    }

    /** JSON value → plain string (text as-is, numbers/booleans via lenient asText). */
    private static String decode(AiSetting row) {
        try {
            return JSON.readTree(row.getValue()).asString();
        } catch (Exception malformed) {
            return null;
        }
    }

    private static String readText(AiSetting row) {
        String value = decode(row);
        return StringUtils.hasText(value) ? value : null;
    }

    private static boolean readBool(AiSetting row) {
        String value = decode(row);
        return value != null && Boolean.parseBoolean(value);
    }
}
