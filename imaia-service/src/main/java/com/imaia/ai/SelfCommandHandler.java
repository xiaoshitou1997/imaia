package com.imaia.ai;

import com.imaia.adapter.ImAdapter;
import com.imaia.adapter.ImAdapters;
import com.imaia.adapter.ImEvent;
import com.imaia.adapter.ImEventHandler;
import com.imaia.domain.AiSettings;
import com.imaia.domain.DeliverySource;
import com.imaia.domain.ImConversation;
import com.imaia.domain.MessageKind;
import com.imaia.repository.ImOutboundLedgerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 自指令（微信同号专属）：hub 主人自己在会话里手打「@别名 内容」，AI 以该会话为
 * 上下文生成并发回同一会话——解决手机端无法 @ 自己的入口问题。别名是纯文本
 * （微信 @ 选择器不含自己），因此判据与 mention 元数据无关：
 * <ol>
 * <li>{@code direction=outgoing}：daemon 按发送者标定，群友的消息永远是 incoming，
 *     进不了这条链；</li>
 * <li>{@code origin=human}：AI 自己回复的 echo 在发送后时间窗内归因为 ai；</li>
 * <li>出账账本按平台消息 id 精确拦截我们已发送的消息（覆盖重启重放的边缘）。</li>
 * </ol>
 * 行级开关为 {@code self_command_alias}（留空即关，四级作用域）。豁免静默时段与
 * @ 门控：指令是主人的显式主观行为；幂等键与投递账本照常生效，事件重放不重发。
 * 飞书侧机器人收不到本人消息，此链天然不生效。
 */
@Component
@Order(20)
@ConditionalOnProperty(prefix = "imaia.ai", name = "enabled", havingValue = "true")
public class SelfCommandHandler implements ImEventHandler {

    private static final Logger log = LoggerFactory.getLogger(SelfCommandHandler.class);

    private final ImAdapters adapters;
    private final AiChatService chatService;
    private final AiSettingsService settings;
    private final InsightService insightService;
    private final GroupContextBuilder groupContext;
    private final OutboundSender outboundSender;
    private final ImOutboundLedgerRepository outboundLedger;

    public SelfCommandHandler(ImAdapters adapters, AiChatService chatService,
            AiSettingsService settings, InsightService insightService,
            GroupContextBuilder groupContext, OutboundSender outboundSender,
            ImOutboundLedgerRepository outboundLedger) {
        this.adapters = adapters;
        this.chatService = chatService;
        this.settings = settings;
        this.insightService = insightService;
        this.groupContext = groupContext;
        this.outboundSender = outboundSender;
        this.outboundLedger = outboundLedger;
    }

    @Override
    public boolean supports(String kind) {
        return KIND_MESSAGE_COMMITTED.equals(kind);
    }

    @Override
    public void handle(ImEvent event) {
        String platformMessageId = event.platformMessageId();
        String conversationPlatformId = event.conversationPlatformId();
        ImAdapter adapter = adapters.forPlatform(event.platform());
        AiSettings config = settings.current();
        if (!config.isEnabled()) {
            return;
        }
        if (adapter.hasProcessed(platformMessageId)) {
            log.debug("Self command already handled; replay ignored: message={}.", platformMessageId);
            return;
        }
        ImConversation conversation = chatService.resolve(adapter, conversationPlatformId);
        config = settings.forConversation(conversation);
        String alias = aliasOf(config);
        if (alias == null) {
            return;
        }
        ImAdapter.FetchedMessage message = adapter.fetchMessage(platformMessageId);
        if (!conversationPlatformId.equals(message.conversationPlatformId())) {
            log.debug("Self command skipped: message identity drifted (message={}).", platformMessageId);
            return;
        }
        if (!"outgoing".equals(message.direction()) || !"human".equals(message.origin())) {
            // 静默丢弃前留一行日志：origin 归因由发送后时间窗近似，被误判成 ai 时
            // 现场只能靠这行确认「命令收到但判成了回声」。
            log.debug("Self command skipped: not a human outgoing message (message={} direction={} origin={}).",
                    platformMessageId, message.direction(), message.origin());
            return;
        }
        if (outboundLedger.findFirstByPlatformMessageId(platformMessageId).isPresent()) {
            log.debug("Self command skipped: message {} is our own send echoing back.", platformMessageId);
            return;
        }
        if (message.kind() != MessageKind.TEXT || !StringUtils.hasText(message.body())) {
            return;
        }
        String prompt = extractPrompt(message.body().strip(), alias);
        if (prompt == null) {
            return;
        }
        String keyPrefix = "imaia-selfcmd-" + platformMessageId;
        boolean groupChat = adapter.isGroupConversation(conversationPlatformId);
        if (groupChat) {
            String reportReply = insightService.handleCommand(conversation, prompt);
            if (reportReply != null) {
                outboundSender.sendText(adapter, conversation, keyPrefix,
                        DeliverySource.SELF_COMMAND, reportReply);
                adapter.markProcessed(platformMessageId);
                log.info("Self-command report sent on platform={} conversation={} for message={}.",
                        event.platform(), conversationPlatformId, platformMessageId);
                return;
            }
        }
        String context = groupChat
                ? groupContext.build(conversation.getId(), platformMessageId) : null;
        List<GeneratedMedia> generated = new ArrayList<>();
        AiChatService.ChatReply reply = chatService.complete(conversation, prompt, null, null,
                context, generated);
        outboundSender.sendText(adapter, conversation, keyPrefix,
                DeliverySource.SELF_COMMAND, reply.text());
        int index = 0;
        for (GeneratedMedia media : reply.generated()) {
            index++;
            outboundSender.sendMedia(adapter, conversation, keyPrefix + "-gen" + index,
                    DeliverySource.SELF_COMMAND, media);
        }
        adapter.markProcessed(platformMessageId);
        log.info("Self-command reply sent on platform={} conversation={} for message={}.",
                event.platform(), conversationPlatformId, platformMessageId);
    }

    /**
     * The line's alias, with a leading @ tolerated; null when the line is off. The
     * alias and the switch are separate keys on purpose: an inherited alias plus an
     * explicit {@code self_command_enabled=false} is how a single line opts out.
     */
    private static String aliasOf(AiSettings config) {
        if (!config.isSelfCommandEnabled()) {
            return null;
        }
        String alias = config.getSelfCommandAlias();
        if (!StringUtils.hasText(alias)) {
            return null;
        }
        alias = alias.strip();
        return alias.startsWith("@") ? alias.substring(1).strip() : alias;
    }

    /**
     * 「@别名 内容」→ 内容。别名后必须跟分隔符（空白或冒号）且内容非空；别名只是
     * 更长名字的前缀（如别名「小艾」、消息「@小艾斯」）不算命中。
     */
    static String extractPrompt(String body, String alias) {
        if (!body.startsWith("@")) {
            return null;
        }
        String rest = body.substring(1);
        if (!rest.startsWith(alias)) {
            return null;
        }
        String tail = rest.substring(alias.length());
        if (tail.isEmpty()) {
            return null;
        }
        char next = tail.charAt(0);
        if (!Character.isWhitespace(next) && next != '：' && next != ':') {
            return null;
        }
        tail = tail.strip();
        if (tail.startsWith("：") || tail.startsWith(":")) {
            tail = tail.substring(1).strip();
        }
        return tail.isEmpty() ? null : tail;
    }
}
