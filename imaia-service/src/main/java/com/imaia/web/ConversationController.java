package com.imaia.web;

import com.imaia.ai.MemberAnalyzer;
import com.imaia.domain.ImConnection;
import com.imaia.domain.ImConversation;
import com.imaia.domain.ImMessage;
import com.imaia.domain.MemberAnalysis;
import com.imaia.repository.AiMessageRepository;
import com.imaia.repository.GroupMemberRepository;
import com.imaia.repository.ImConversationRepository;
import com.imaia.repository.ImConnectionRepository;
import com.imaia.repository.ImMessageRepository;
import com.imaia.repository.MemberAnalysisRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 统一收件箱：会话目录、归档消息、上下文流、成员快照与画像、跨线检索。 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ImConversationRepository conversations;
    private final ImMessageRepository messages;
    private final AiMessageRepository aiMessages;
    private final GroupMemberRepository members;
    private final MemberAnalysisRepository analyses;
    private final ImConnectionRepository connections;
    private final ConversationLabels labels;
    private final MemberAnalyzer memberAnalyzer;

    public ConversationController(ImConversationRepository conversations, ImMessageRepository messages,
            AiMessageRepository aiMessages, GroupMemberRepository members, MemberAnalysisRepository analyses,
            ImConnectionRepository connections, ConversationLabels labels, MemberAnalyzer memberAnalyzer) {
        this.conversations = conversations;
        this.messages = messages;
        this.aiMessages = aiMessages;
        this.members = members;
        this.analyses = analyses;
        this.connections = connections;
        this.labels = labels;
        this.memberAnalyzer = memberAnalyzer;
    }

    public record ConversationRow(Long id, String externalId, String displayName, String kind, String persona,
            boolean autoreply, Instant lastSeenAt, Long connectionId, String platform, String connectionName,
            long messageCount) {
    }

    public record MessageRow(Long id, String messageId, String senderId, String senderName, String direction,
            String category, boolean mentioned, String origin, String body, Instant archivedAt) {
    }

    public record AiMessageRow(Long id, String role, String content, String toolName, Instant createdAt) {
    }

    public record ConversationUpdate(String displayName, String remark, String persona, Boolean autoreply) {
    }

    public record MemberRow(Long id, String memberExternalId, String displayName, Instant lastSeenAt,
            boolean analyzed) {
    }

    public record AnalysisRequest(String memberExternalId, String memberName) {
    }

    public record AnalysisRow(Long id, String memberExternalId, String memberName, String profile,
            Integer messageCount, Instant generatedAt) {
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String query) {
        List<ImConversation> list = query == null || query.isBlank()
                ? conversations.findTop200ByOrderByLastSeenAtDesc()
                : conversations.findTop50ByDisplayNameContainingIgnoreCaseOrExternalIdContainingIgnoreCaseOrderByLastSeenAtDesc(
                        query.strip(), query.strip());
        Map<Long, ImConnection> connectionById = labels.connectionsById();
        Map<Long, Long> counts = labels.messageCounts();
        return ApiEnvelope.ok(list.stream().map(conversation -> {
            ImConnection connection = connectionById.get(conversation.getConnectionId());
            return new ConversationRow(conversation.getId(), conversation.getExternalId(),
                    conversation.getDisplayName(), conversation.getKind(), conversation.getPersona(),
                    conversation.isAutoreply(), conversation.getLastSeenAt(), conversation.getConnectionId(),
                    connection == null ? null : connection.getPlatform(),
                    connection == null ? null : connection.getName(),
                    counts.getOrDefault(conversation.getId(), 0L));
        }).toList());
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody ConversationUpdate body) {
        ImConversation conversation = requireConversation(id);
        if (body.displayName() != null) {
            conversation.setDisplayName(body.displayName().isBlank() ? null : body.displayName().strip());
        }
        if (body.remark() != null) {
            conversation.setRemark(body.remark().isBlank() ? null : body.remark().strip());
        }
        if (body.persona() != null) {
            conversation.setPersona(body.persona().isBlank() ? null : body.persona().strip());
        }
        if (body.autoreply() != null) {
            conversation.setAutoreply(body.autoreply());
        }
        conversations.save(conversation);
        return ApiEnvelope.ok(conversation.getId());
    }

    @GetMapping("/{id}/messages")
    public Map<String, Object> messages(@PathVariable Long id,
            @RequestParam(required = false) String keyword) {
        requireConversation(id);
        List<ImMessage> recent = messages.findTop200ByConversationIdOrderByArchivedAtDesc(id);
        if (keyword != null && !keyword.isBlank()) {
            String needle = keyword.strip();
            recent = recent.stream().filter(message -> message.getBody() != null
                    && message.getBody().contains(needle)).toList();
        }
        return ApiEnvelope.ok(recent.stream().map(message -> new MessageRow(message.getId(),
                message.getMessageId(), message.getSenderId(), message.getSenderName(),
                message.getDirection(), message.getCategory().value(), message.isMentioned(),
                message.getOrigin(), message.getBody(), message.getArchivedAt())).toList());
    }

    @GetMapping("/{id}/ai-messages")
    public Map<String, Object> aiMessages(@PathVariable Long id) {
        requireConversation(id);
        return ApiEnvelope.ok(aiMessages.findByConversationIdInOrderByCreatedAtDesc(List.of(id)).stream()
                .map(message -> new AiMessageRow(message.getId(), message.getRole(), message.getContent(),
                        message.getToolName(), message.getCreatedAt()))
                .toList());
    }

    @GetMapping("/{id}/members")
    public Map<String, Object> members(@PathVariable Long id) {
        requireConversation(id);
        List<String> analyzedIds = analyses.findTop100ByConversationIdOrderByGeneratedAtDesc(id).stream()
                .map(MemberAnalysis::getMemberExternalId).toList();
        return ApiEnvelope.ok(members.findTop200ByConversationIdOrderByLastSeenAtDesc(id).stream()
                .map(member -> new MemberRow(member.getId(), member.getMemberExternalId(),
                        member.getDisplayName(), member.getLastSeenAt(),
                        analyzedIds.contains(member.getMemberExternalId())))
                .toList());
    }

    @PostMapping("/{id}/analysis")
    public Map<String, Object> runAnalysis(@PathVariable Long id, @RequestBody AnalysisRequest body) {
        ImConversation conversation = requireConversation(id);
        if (body.memberExternalId() == null || body.memberExternalId().isBlank()) {
            throw new IllegalArgumentException("memberExternalId 不能为空。");
        }
        MemberAnalysis analysis = memberAnalyzer.analyzeMember(conversation,
                body.memberExternalId().strip(), body.memberName());
        if (analysis == null) {
            throw new java.util.NoSuchElementException("归档里没有该成员的消息，无法分析。");
        }
        return ApiEnvelope.ok(toAnalysisRow(analysis));
    }

    @GetMapping("/{id}/analysis")
    public Map<String, Object> analysis(@PathVariable Long id) {
        requireConversation(id);
        return ApiEnvelope.ok(analyses.findTop100ByConversationIdOrderByGeneratedAtDesc(id).stream()
                .map(this::toAnalysisRow).toList());
    }

    @GetMapping("/search")
    public Map<String, Object> searchMessages(@RequestParam String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("keyword 不能为空。");
        }
        Map<Long, ImConnection> connectionById = labels.connectionsById();
        Map<Long, String> conversationLabels = labels.labels();
        return ApiEnvelope.ok(messages.searchByBody(keyword.strip(), PageRequest.of(0, 50)).stream()
                .map(message -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", message.getId());
                    row.put("conversationId", message.getConversationId());
                    row.put("conversationLabel", conversationLabels.getOrDefault(
                            message.getConversationId(), String.valueOf(message.getConversationId())));
                    row.put("senderId", message.getSenderId());
                    row.put("senderName", message.getSenderName());
                    row.put("category", message.getCategory().value());
                    row.put("body", message.getBody());
                    row.put("archivedAt", message.getArchivedAt());
                    return row;
                }).toList());
    }

    private ImConversation requireConversation(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("conversationId 不能为空。");
        }
        return conversations.findById(id).orElseThrow();
    }

    private AnalysisRow toAnalysisRow(MemberAnalysis analysis) {
        return new AnalysisRow(analysis.getId(), analysis.getMemberExternalId(), analysis.getMemberName(),
                analysis.getProfile(), analysis.getMessageCount(), analysis.getGeneratedAt());
    }
}
