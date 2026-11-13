package com.imaia.web;

import com.imaia.domain.AiMemory;
import com.imaia.repository.AiMemoryRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 长期记忆（AI 自动沉淀、挂会话线）：查看、编辑、删除。 */
@RestController
@RequestMapping("/api/memories")
public class MemoryController {

    public record MemoryRow(Long id, Long conversationId, String conversationLabel, String fact,
            Instant createdAt) {
    }

    public record MemoryUpdate(String fact) {
    }

    private final AiMemoryRepository memories;
    private final ConversationLabels labels;

    public MemoryController(AiMemoryRepository memories, ConversationLabels labels) {
        this.memories = memories;
        this.labels = labels;
    }

    @GetMapping
    public ApiEnvelope<List<MemoryRow>> list() {
        Map<Long, String> labelMap = labels.labels();
        return ApiEnvelope.ok(memories.findTop200ByOrderByCreatedAtDesc().stream()
                .map(memory -> new MemoryRow(memory.getId(), memory.getConversationId(),
                        labelMap.getOrDefault(memory.getConversationId(),
                                String.valueOf(memory.getConversationId())),
                        memory.getFact(), memory.getCreatedAt()))
                .toList());
    }

    @PutMapping("/{id}")
    public ApiEnvelope<String> update(@PathVariable Long id, @RequestBody MemoryUpdate body) {
        AiMemory memory = memories.findById(id).orElseThrow();
        if (!StringUtils.hasText(body.fact())) {
            throw new IllegalArgumentException("fact 不能为空。");
        }
        memory.setFact(body.fact().strip());
        memories.save(memory);
        return ApiEnvelope.ok("已更新");
    }

    @DeleteMapping("/{id}")
    public ApiEnvelope<String> delete(@PathVariable Long id) {
        memories.findById(id).orElseThrow();
        memories.deleteById(id);
        return ApiEnvelope.ok("已删除");
    }
}
