package com.imaia.web;

import java.time.Instant;
import com.imaia.domain.ImAccount;
import com.imaia.domain.Platform;
import com.imaia.repository.ImAccountRepository;
import com.imaia.repository.ImConversationRepository;
import com.imaia.stream.StreamOrchestrator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.util.StringUtils;
import java.util.List;

/**
 * 平台账号生命周期：新建、编辑（凭据掩码读写还原）、启停、删除（已有会话数据拒绝）。
 * 配置改动即时生效——每次变更触发对应平台的流重接线（停止、重载配置、按需重启）。
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final JsonMapper jsonMapper;
    private static final String MASK = "••••••••";
    private static final List<String> SECRET_KEYS = List.of("token", "appSecret", "apiKey", "secret", "password");
    private final ImAccountRepository accounts;
    private final ImConversationRepository conversations;
    private final StreamOrchestrator streams;

    public AccountController(ImAccountRepository accounts, ImConversationRepository conversations,
            StreamOrchestrator streams, JsonMapper jsonMapper) {
        this.accounts = accounts;
        this.conversations = conversations;
        this.streams = streams;
        this.jsonMapper = jsonMapper;
    }

    public record AccountRow(Long id, Platform platformCode, String name, boolean enabled, String config,
            Instant updatedAt) {
    }

    /** platform 入参为 wire 字符串，fromValue 显式转换并校验值域。 */
    public record AccountCreate(@NotBlank @Size(max = 16) String platformCode, @NotBlank @Size(max = 64) String name, Boolean enabled, JsonNode config) {
    }

    public record AccountUpdate(@Size(max = 64) String name, Boolean enabled, JsonNode config) {
    }

    @GetMapping
    public Result<List<AccountRow>> list() {
        return Result.ok(accounts.findTop200ByOrderByIdAsc().stream()
                .map(this::toRow).toList());
    }

    @PostMapping
    public Result<AccountRow> create(@Valid @RequestBody AccountCreate body) {
        Platform platform = Platform.fromValue(body.platformCode());
        if (!StringUtils.hasText(body.name())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "name 不能为空。");
        }
        Instant now = Instant.now();
        ImAccount created = ImAccount.builder()
                .platform(platform)
                .name(body.name().strip())
                .enabled(body.enabled() == null || body.enabled())
                .config(body.config() == null ? "{}" : jsonMapper.writeValueAsString(body.config()))
                
                
                .build();
        ImAccount saved = accounts.save(created);
        streams.refreshPlatform(saved.getPlatform());
        return Result.ok(toRow(saved));
    }

    @PutMapping("/{id}")
    public Result<AccountRow> update(@PathVariable Long id, @Valid @RequestBody AccountUpdate body) {
        ImAccount account = accounts.findById(id).orElseThrow();
        if (StringUtils.hasText(body.name())) {
            account.setName(body.name().strip());
        }
        if (body.enabled() != null) {
            account.setEnabled(body.enabled());
        }
        if (body.config() != null) {
            account.setConfig(mergeConfig(account.getConfig(), body.config()));
        }
        ImAccount saved = accounts.save(account);
        streams.refreshPlatform(saved.getPlatform());
        return Result.ok(toRow(saved));
    }

    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        ImAccount account = accounts.findById(id).orElseThrow();
        if (conversations.existsByAccountId(id)) {
            return Result.<String>error(409, 409, "该账号已有会话数据，禁止删除；可改为停用。").getBody();
        }
        accounts.deleteById(account.getId());
        streams.refreshPlatform(account.getPlatform());
        return Result.ok("已删除");
    }

    private AccountRow toRow(ImAccount account) {
        return new AccountRow(account.getId(), account.getPlatform(), account.getName(),
                account.isEnabled(), maskConfig(account.getConfig()), account.getUpdatedAt());
    }

    /** Secret values become the mask; a masked value written back restores the stored one. */
    private String maskConfig(String configJson) {
        try {
            JsonNode config = jsonMapper.readTree(!StringUtils.hasText(configJson) ? "{}" : configJson);
            if (config.isObject()) {
                for (String key : SECRET_KEYS) {
                    if (config.has(key) && config.get(key).isString()
                            && !config.get(key).asString().isBlank()) {
                        ((ObjectNode) config).put(key, MASK);
                    }
                }
            }
            return jsonMapper.writeValueAsString(config);
        } catch (RuntimeException failure) {
            return "{}"; // 历史配置可能不是合法 JSON，掩码视图按空对象返回
        }
    }

    private String mergeConfig(String existingJson, JsonNode submitted) {
        if (!submitted.isObject()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "config 必须是 JSON 对象。");
        }
        ObjectNode merged = (ObjectNode) jsonMapper.readTree(
                !StringUtils.hasText(existingJson) ? "{}" : existingJson);
        for (var field : submitted.properties()) {
            String key = field.getKey();
            if (SECRET_KEYS.contains(key) && field.getValue().isString()
                    && MASK.equals(field.getValue().asString())) {
                continue;
            }
            merged.set(key, field.getValue());
        }
        return jsonMapper.writeValueAsString(merged);
    }
}
