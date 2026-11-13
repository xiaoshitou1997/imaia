package com.imaia.web;

import java.time.Instant;
import com.imaia.domain.ImConnection;
import com.imaia.repository.ImConnectionRepository;
import com.imaia.repository.ImConversationRepository;
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
 * 接入连接生命周期：新建、编辑（凭据掩码读写还原）、启停、删除（已有会话数据拒绝）。
 * 配置改动重启生效。
 */
@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String MASK = "••••••••";
    private static final List<String> SECRET_KEYS = List.of("token", "appSecret", "apiKey", "secret", "password");
    private static final List<String> PLATFORMS = List.of("wechat", "feishu");

    private final ImConnectionRepository connections;
    private final ImConversationRepository conversations;

    public ConnectionController(ImConnectionRepository connections, ImConversationRepository conversations) {
        this.connections = connections;
        this.conversations = conversations;
    }

    public record ConnectionRow(Long id, String platform, String name, boolean enabled, String config,
            Instant updatedAt) {
    }

    public record ConnectionCreate(String platform, String name, Boolean enabled, JsonNode config) {
    }

    public record ConnectionUpdate(String name, Boolean enabled, JsonNode config) {
    }

    @GetMapping
    public ApiEnvelope<List<ConnectionRow>> list() {
        return ApiEnvelope.ok(connections.findAllByOrderByIdAsc().stream()
                .map(this::toRow).toList());
    }

    @PostMapping
    public ApiEnvelope<ConnectionRow> create(@RequestBody ConnectionCreate body) {
        if (body.platform() == null || !PLATFORMS.contains(body.platform())) {
            throw new IllegalArgumentException("platform 必须是 " + PLATFORMS + " 之一。");
        }
        if (!StringUtils.hasText(body.name())) {
            throw new IllegalArgumentException("name 不能为空。");
        }
        ImConnection created = new ImConnection(body.platform(), body.name().strip(),
                body.enabled() == null || body.enabled(),
                body.config() == null ? "{}" : MAPPER.writeValueAsString(body.config()));
        return ApiEnvelope.ok(toRow(connections.save(created)));
    }

    @PutMapping("/{id}")
    public ApiEnvelope<ConnectionRow> update(@PathVariable Long id, @RequestBody ConnectionUpdate body) {
        ImConnection connection = connections.findById(id).orElseThrow();
        if (StringUtils.hasText(body.name())) {
            connection.setName(body.name().strip());
        }
        if (body.enabled() != null) {
            connection.setEnabled(body.enabled());
        }
        if (body.config() != null) {
            connection.setConfig(mergeConfig(connection.getConfig(), body.config()));
        }
        return ApiEnvelope.ok(toRow(connections.save(connection)));
    }

    @DeleteMapping("/{id}")
    public ApiEnvelope<String> delete(@PathVariable Long id) {
        connections.findById(id).orElseThrow();
        if (conversations.existsByConnectionId(id)) {
            return ApiEnvelope.<String>error(409, 409, "该连接已有会话数据，禁止删除；可改为停用。").getBody();
        }
        connections.deleteById(id);
        return ApiEnvelope.ok("已删除");
    }

    private ConnectionRow toRow(ImConnection connection) {
        return new ConnectionRow(connection.getId(), connection.getPlatform(), connection.getName(),
                connection.isEnabled(), maskConfig(connection.getConfig()), connection.getUpdatedAt());
    }

    /** Secret values become the mask; a masked value written back restores the stored one. */
    private String maskConfig(String configJson) {
        try {
            JsonNode config = MAPPER.readTree(!StringUtils.hasText(configJson) ? "{}" : configJson);
            if (config.isObject()) {
                for (String key : SECRET_KEYS) {
                    if (config.has(key) && config.get(key).isString()
                            && !config.get(key).asString().isBlank()) {
                        ((ObjectNode) config).put(key, MASK);
                    }
                }
            }
            return MAPPER.writeValueAsString(config);
        } catch (RuntimeException failure) {
            return "{}"; // 历史配置可能不是合法 JSON，掩码视图按空对象返回
        }
    }

    private String mergeConfig(String existingJson, JsonNode submitted) {
        if (!submitted.isObject()) {
            throw new IllegalArgumentException("config 必须是 JSON 对象。");
        }
        ObjectNode merged = (ObjectNode) MAPPER.readTree(
                !StringUtils.hasText(existingJson) ? "{}" : existingJson);
        for (var field : submitted.properties()) {
            String key = field.getKey();
            if (SECRET_KEYS.contains(key) && field.getValue().isString()
                    && MASK.equals(field.getValue().asString())) {
                continue;
            }
            merged.set(key, field.getValue());
        }
        return MAPPER.writeValueAsString(merged);
    }
}
