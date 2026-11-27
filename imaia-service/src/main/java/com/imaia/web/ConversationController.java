package com.imaia.web;

import com.imaia.adapter.ImAdapter;
import com.imaia.adapter.ImAdapters;
import com.imaia.adapter.wechat.WechatHistoryService;
import com.imaia.ai.ContactAnalyzer;
import com.imaia.domain.AttachmentKind;
import com.imaia.domain.Platform;
import com.imaia.domain.AttachmentStatus;
import com.imaia.domain.ConversationKind;
import com.imaia.domain.ImAttachment;
import com.imaia.domain.ImAccount;
import com.imaia.domain.ImContact;
import com.imaia.domain.ImConversationContactBind;
import com.imaia.domain.ImConversationContactProfile;
import com.imaia.domain.ImConversation;
import com.imaia.domain.ImMessage;
import com.imaia.domain.MessageDirection;
import com.imaia.domain.MessageOrigin;
import com.imaia.repository.ImAttachmentRepository;
import com.imaia.repository.ImContactRepository;
import com.imaia.repository.ImConversationRepository;
import com.imaia.repository.ImAccountRepository;
import com.imaia.repository.ImConversationContactBindRepository;
import com.imaia.repository.ImMessageRepository;
import com.imaia.repository.ImConversationContactProfileRepository;
import com.imaia.storage.S3MediaStorage;
import com.imaia.ai.AiSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** 统一收件箱：会话目录、归档消息、上下文流、成员快照与画像、跨线检索。 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ImConversationRepository conversations;
    private final ImMessageRepository messages;
    private final ImConversationContactBindRepository binds;
    private final ImContactRepository contacts;
    private final ImAttachmentRepository attachments;
    private final ImConversationContactProfileRepository contactProfiles;
    private final ImAccountRepository accounts;
    private final ConversationLabels labels;
    private final ContactAnalyzer contactAnalyzer;
    private final WechatHistoryService wechatHistory;
    private final ImAdapters adapters;
    private final S3MediaStorage mediaStorage;
    private final AiSettingsService settings;

    public ConversationController(ImConversationRepository conversations, ImMessageRepository messages,
            ImConversationContactBindRepository binds, ImContactRepository contacts, ImAttachmentRepository attachments,
            ImConversationContactProfileRepository contactProfiles, ImAccountRepository accounts,
            ConversationLabels labels, ContactAnalyzer contactAnalyzer,
            WechatHistoryService wechatHistory, ImAdapters adapters, S3MediaStorage mediaStorage,
            AiSettingsService settings) {
        this.conversations = conversations;
        this.messages = messages;
        this.binds = binds;
        this.contacts = contacts;
        this.attachments = attachments;
        this.contactProfiles = contactProfiles;
        this.accounts = accounts;
        this.labels = labels;
        this.contactAnalyzer = contactAnalyzer;
        this.wechatHistory = wechatHistory;
        this.adapters = adapters;
        this.mediaStorage = mediaStorage;
        this.settings = settings;
    }

    /**
     * 会话目录行。{@code displayName} 是给用户看的名字：优先用户改过的 title，
     * 否则按封闭值域 code 给「未命名群聊 / 未命名单聊 / 未命名频道」。
     * 不使用 platformId（wxid / xxx@chatroom / oc_xxx，平台内部标识），
     * 也不使用自增 id（存储实现细节，不进契约与文案）。{@code platformId}
     * 仍随行返回，供控制台排障与按平台 id 检索。
     */
    public record ConversationRow(Long id, String platformId, String displayName, String title,
            String remark, ConversationKind kind, String persona, boolean autoreply,
            String selfCommandAlias, boolean selfCommandEnabled, Instant lastSeenAt,
            Instant lastMessageAt, Long accountId, Platform platformCode, String connectionName,
            long messageCount) {
    }

    public record MessageRow(Long id, String platformId, Long contactId, String senderName,
            MessageDirection direction, String kind, boolean mentioned, MessageOrigin origin,
            String body, Instant occurredAt, Instant archivedAt, List<AttachmentRow> attachments) {
    }

    public record AttachmentRow(Long id, String kind, String filename, String mimeType,
            Long sizeBytes, Long durationMs, String status) {
    }

    public record ConversationUpdate(@Size(max = 100) String title,
            @Size(max = 200) String remark,
            @Size(max = 2000) String persona,
            Boolean autoreply,
            String selfCommandAlias, Boolean selfCommandEnabled) {
    }

    public record ContactBindRow(Long id, Long contactId, String platformId, String displayName,
            String role, Instant lastSeenAt, boolean analyzed) {
    }

    public record AnalysisRequest(Long contactId) {
    }

    public record HistorySyncResult(int scanned, int added, int pages, int failed, boolean hasMore) {
    }

    public record AnalysisRow(Long id, Long contactId, String contactName, String profile,
            Integer messageCount, Instant generatedAt) {
    }

    /** One cross-conversation search hit with its conversation label. */
    public record SearchHit(Long id, Long conversationId, String conversationLabel, Long contactId,
            String senderName, String kind, String body, Instant archivedAt) {
    }

    @GetMapping
    public Result<PageResult<ConversationRow>> list(@RequestParam(required = false) String query,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ImConversation> list = !StringUtils.hasText(query)
                ? conversations.findAllByOrderByLastSeenAtDesc(PageResult.pageable(page, size))
                : conversations
                        .findByTitleContainingIgnoreCaseOrPlatformIdContainingIgnoreCaseOrderByLastSeenAtDesc(
                                query.strip(), query.strip(), PageResult.pageable(page, size));
        Map<Long, ImAccount> accountById = labels.accountsById();
        Map<Long, Long> counts = labels.messageCounts();
        Map<Long, AiSettingsService.LineCaps> caps = settings.lineSettings(list.getContent());
        Page<ConversationRow> rows = list.map(conversation -> {
            ImAccount account = accountById.get(conversation.getAccountId());
            AiSettingsService.LineCaps line = caps.getOrDefault(conversation.getId(),
                    AiSettingsService.LineCaps.DEFAULT);
            return new ConversationRow(conversation.getId(), conversation.getPlatformId(),
                    displayNameOf(conversation), conversation.getTitle(),
                    conversation.getRemark(), conversation.getKind(),
                    line.persona(), line.autoreply(), line.selfCommandAlias(),
                    line.selfCommandEnabled(),
                    conversation.getLastSeenAt(), conversation.getLastMessageAt(),
                    conversation.getAccountId(),
                    account == null ? null : account.getPlatform(),
                    account == null ? null : account.getName(),
                    counts.getOrDefault(conversation.getId(), 0L));
        });
        return Result.ok(PageResult.of(rows));
    }

    /** 用户可见的会话名：改过的 title 优先，否则按封闭值域 code 给占位名。 */
    private static String displayNameOf(ImConversation conversation) {
        return ConversationLabels.displayNameOf(conversation);
    }

    /**
     * Updates the line. Validation runs to completion before the first write: the
     * fields land in separate tables (conversation row vs ai_setting rows) and a
     * late failure must not leave the earlier ones applied while the client is told
     * the save failed. {@code @Transactional} keeps the whole batch atomic.
     */
    @PutMapping("/{id}")
    @Transactional
    public Result<Long> update(@PathVariable Long id, @Valid @RequestBody ConversationUpdate body) {
        ImConversation conversation = requireConversation(id);
        String alias = validateSelfCommandAlias(body.selfCommandAlias());
        if (body.title() != null) {
            conversation.setTitle(body.title().isBlank() ? null : body.title().strip());
        }
        if (body.remark() != null) {
            conversation.setRemark(body.remark().isBlank() ? null : body.remark().strip());
        }
        if (body.persona() != null) {
            settings.saveConversationPersona(conversation.getId(), body.persona());
        }
        if (body.autoreply() != null) {
            settings.saveConversationAutoreply(conversation.getId(), body.autoreply());
        }
        if (alias != null) {
            settings.saveConversationSelfCommandAlias(conversation.getId(), alias);
        }
        if (body.selfCommandEnabled() != null) {
            settings.saveConversationSelfCommandEnabled(conversation.getId(), body.selfCommandEnabled());
        }
        conversations.save(conversation);
        return Result.ok(conversation.getId());
    }

    /** Null passthrough (field absent) is distinct from a present but blank value (clears). */
    private static String validateSelfCommandAlias(String raw) {
        if (raw == null) {
            return null;
        }
        String alias = raw.strip();
        if (alias.length() > AiSettingsService.SELF_COMMAND_ALIAS_MAX) {
            throw new BusinessException(ErrorCode.SELF_COMMAND_ALIAS_TOO_LONG,
                    "selfCommandAlias 最长 " + AiSettingsService.SELF_COMMAND_ALIAS_MAX + " 字符。");
        }
        return alias;
    }

    /** 从 daemon 拉取该会话更早的归档消息；只归档，不触发 AI 回复。 */
    @PostMapping("/{id}/sync-history")
    public Result<HistorySyncResult> syncHistory(@PathVariable Long id,
            @RequestParam(defaultValue = "5") int pages,
            @RequestParam(defaultValue = "100") int limit) {
        ImConversation conversation = requireConversation(id);
        ImAccount account = accounts.findById(conversation.getAccountId()).orElseThrow();
        if (account.getPlatform() != Platform.WECHAT) {
            throw new BusinessException(ErrorCode.HISTORY_SYNC_UNSUPPORTED_PLATFORM);
        }
        long before = messages.countByConversationId(id);
        WechatHistoryService.Result result = wechatHistory.pull(conversation.getPlatformId(), pages, limit);
        long after = messages.countByConversationId(id);
        return Result.ok(new HistorySyncResult(result.scanned(), (int) (after - before),
                result.pages(), result.failed(), result.hasMore()));
    }

    @GetMapping("/{id}/messages")
    public Result<List<MessageRow>> messages(@PathVariable Long id,
            @RequestParam(required = false) String keyword) {
        requireConversation(id);
        List<ImMessage> recent = messages.findTop200ByConversationIdOrderByOccurredAtDescIdDesc(id);
        if (StringUtils.hasText(keyword)) {
            String needle = keyword.strip();
            recent = recent.stream().filter(message -> message.getBody() != null
                    && message.getBody().contains(needle)).toList();
        }
        Map<Long, ImContact> contactById = contactMap(recent);
        Map<Long, List<AttachmentRow>> attachmentsByMessage = new HashMap<>();
        if (!recent.isEmpty()) {
            List<Long> messageIds = recent.stream().map(ImMessage::getId).toList();
            for (ImAttachment attachment : attachments.findByMessageIdInOrderByIdAsc(messageIds)) {
                attachmentsByMessage.computeIfAbsent(attachment.getMessageId(), key -> new java.util.ArrayList<>())
                        .add(new AttachmentRow(attachment.getId(), attachment.getKind().value(),
                                attachment.getFilename(), attachment.getMimeType(),
                                attachment.getSizeBytes(), attachment.getDurationMs(),
                                attachment.getStatus().value()));
            }
        }
        return Result.ok(recent.stream().map(message -> new MessageRow(message.getId(),
                message.getPlatformId(), message.getContactId(),
                contactName(contactById.get(message.getContactId())),
                message.getDirection(), message.getKind().value(), message.isMentioned(),
                message.getOrigin(), message.getBody(), message.getOccurredAt(), message.getArchivedAt(),
                attachmentsByMessage.getOrDefault(message.getId(), List.of()))).toList());
    }

    @GetMapping("/{id}/contacts")
    public Result<List<ContactBindRow>> contacts(@PathVariable Long id) {
        requireConversation(id);
        Map<Long, ImContact> contactById = new HashMap<>();
        for (ImContact contact : contacts.findAllById(binds
                .findByConversationIdOrderByJoinedAtAsc(id).stream()
                .map(ImConversationContactBind::getContactId).distinct().toList())) {
            contactById.put(contact.getId(), contact);
        }
        List<Long> analyzedContactIds = contactProfiles
                .findTop100ByConversationIdOrderByGeneratedAtDesc(id).stream()
                .map(ImConversationContactProfile::getContactId).toList();
        return Result.ok(binds
                .findTop200ByConversationIdAndLeftAtIsNullOrderByLastSeenAtDesc(id).stream()
                .map(bind -> {
                    ImContact contact = contactById.get(bind.getContactId());
                    return new ContactBindRow(bind.getId(), bind.getContactId(),
                            contact == null ? null : contact.getPlatformId(),
                            bind.getDisplayName() != null ? bind.getDisplayName()
                                    : contactName(contact),
                            bind.getRole().value(), bind.getLastSeenAt(),
                            analyzedContactIds.contains(bind.getContactId()));
                })
                .toList());
    }

    @PostMapping("/{id}/analysis")
    public Result<AnalysisRow> runAnalysis(@PathVariable Long id, @RequestBody AnalysisRequest body) {
        ImConversation conversation = requireConversation(id);
        if (body.contactId() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "contactId 不能为空。");
        }
        ImConversationContactProfile analysis = contactAnalyzer.analyzeContact(conversation, body.contactId());
        if (analysis == null) {
            throw new NoSuchElementException("归档里没有该成员的消息，无法分析。");
        }
        return Result.ok(toAnalysisRow(analysis));
    }

    @GetMapping("/{id}/analysis")
    public Result<List<AnalysisRow>> analysis(@PathVariable Long id) {
        requireConversation(id);
        return Result.ok(contactProfiles.findTop100ByConversationIdOrderByGeneratedAtDesc(id).stream()
                .map(this::toAnalysisRow).toList());
    }

    /** 附件内容下发：已落盘的直接回字节；只有平台引用的懒拉取一次并归档到本地媒体仓库。 */
    @GetMapping("/{id}/attachments/{attachmentId}/content")
    public ResponseEntity<Resource> attachmentContent(@PathVariable Long id,
            @PathVariable Long attachmentId) {
        ImConversation conversation = requireConversation(id);
        ImAttachment attachment = attachments.findById(attachmentId)
                .orElseThrow(() -> new NoSuchElementException("附件不存在：" + attachmentId + "。"));
        ImMessage message = messages.findById(attachment.getMessageId())
                .filter(stored -> stored.getConversationId().equals(conversation.getId()))
                .orElseThrow(() -> new NoSuchElementException("附件不属于会话：" + id + "。"));
        ImAttachment stored = StringUtils.hasText(attachment.getStorageRef())
                ? attachment
                : downloadToLocal(attachment, message, conversation);
        byte[] data = mediaStorage.read(stored.getStorageRef())
                .orElseThrow(() -> new NoSuchElementException(
                        "附件内容不存在（引用失效或未落仓库）：" + stored.getStorageRef() + "。"));
        return MediaContentResponses.inline(data, stored.getMimeType(), stored.getFilename());
    }

    /** 未落盘附件的一次性懒拉取：平台下载 → 本地媒体仓库 → 回填 storage_ref 与状态。 */
    private ImAttachment downloadToLocal(ImAttachment attachment, ImMessage message,
            ImConversation conversation) {
        if (!StringUtils.hasText(attachment.getPlatformRef())) {
            throw new NoSuchElementException("附件既没有本地文件也没有平台引用，无法拉取内容。");
        }
        ImAccount account = accounts.findById(conversation.getAccountId())
                .orElseThrow(() -> new NoSuchElementException("会话连接不存在，无法拉取附件内容。"));
        ImAdapter adapter = adapters.forPlatform(account.getPlatform());
        if (!adapter.configured()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "平台连接未启用（" + account.getPlatform() + "），无法拉取附件内容。");
        }
        ImAdapter.MediaBytes media = attachment.getKind() == AttachmentKind.PREVIEW
                ? adapter.downloadPreview(message.getPlatformId())
                : adapter.downloadAsset(message.getPlatformId(), message.getKind());
        if (media == null || media.data() == null || media.data().length == 0) {
            throw new NoSuchElementException("平台侧没有该附件的内容：" + attachment.getPlatformRef() + "。");
        }
        String storageRef = mediaStorage.store("attachments", attachment.getFilename(), media.data());
        attachment.setStorageRef(storageRef);
        if (!StringUtils.hasText(attachment.getMimeType()) && StringUtils.hasText(media.contentType())) {
            attachment.setMimeType(media.contentType());
        }
        if (attachment.getSizeBytes() == null) {
            attachment.setSizeBytes((long) media.data().length);
        }
        attachment.setStatus(AttachmentStatus.DOWNLOADED);
        attachment.setDownloadedAt(Instant.now());
        attachment.setError(null);
        return attachments.save(attachment);
    }

    @GetMapping("/search")
    public Result<List<SearchHit>> searchMessages(@RequestParam String keyword) {
        if (!StringUtils.hasText(keyword)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "keyword 不能为空。");
        }
        Map<Long, String> conversationLabels = labels.labels();
        List<ImMessage> found = messages.searchByBody(keyword.strip(),
                org.springframework.data.domain.PageRequest.of(0, 50));
        Map<Long, ImContact> contactById = contactMap(found);
        return Result.ok(found.stream()
                .map(message -> new SearchHit(message.getId(), message.getConversationId(),
                        // 兜底不用 id：labels() 已覆盖全部会话，查不到时给中性名即可。
                        conversationLabels.getOrDefault(message.getConversationId(), "未知会话"),
                        message.getContactId(),
                        contactName(contactById.get(message.getContactId())),
                        message.getKind().value(), message.getBody(), message.getArchivedAt()))
                .toList());
    }

    private Map<Long, ImContact> contactMap(List<ImMessage> list) {
        List<Long> ids = list.stream().map(ImMessage::getContactId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, ImContact> byId = new HashMap<>();
        for (ImContact contact : contacts.findAllById(ids)) {
            byId.put(contact.getId(), contact);
        }
        return byId;
    }

    private static String contactName(ImContact contact) {
        if (contact == null) {
            return null;
        }
        if (StringUtils.hasText(contact.getAlias())) {
            return contact.getAlias();
        }
        if (StringUtils.hasText(contact.getDisplayName())) {
            return contact.getDisplayName();
        }
        // 不用 platformId 兜底：那是平台内部标识，不该出现在界面文案里。
        return "未命名成员";
    }

    private ImConversation requireConversation(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "conversationId 不能为空。");
        }
        return conversations.findById(id).orElseThrow();
    }

    private AnalysisRow toAnalysisRow(ImConversationContactProfile analysis) {
        ImContact contact = contacts.findById(analysis.getContactId()).orElse(null);
        return new AnalysisRow(analysis.getId(), analysis.getContactId(),
                contactName(contact),
                analysis.getProfile(), analysis.getMessageCount(), analysis.getGeneratedAt());
    }
}
