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
        if (!settings.current().isEnabled() || adapter.hasProcessed(platformMessageId)) {
            log.debug("Self command skipped before resolution: message={} (master switch off or replay).",
                    platformMessageId);
            return;
        }
        ImConversation conversation = chatService.resolve(adapter, conversationPlatformId);
        String alias = aliasOf(settings.forConversation(conversation));
        if (alias == null) {
            return; // 该会话未开自指令：最常见路径，不打日志以免噪声
        }
        String prompt = resolvePrompt(adapter, conversation, platformMessageId, alias);
        if (prompt == null) {
            return;
        }
        answer(adapter, conversation, event, platformMessageId, prompt);
    }

    /**
     * 逐道门控之后把「@别名 内容」解析成待处理指令；任一条件不满足即返回 null。
     * 拆分自 handle：这一段全是判定，与后面的应答副作用不在同一抽象层次。
     */
    private String resolvePrompt(ImAdapter adapter, ImConversation conversation,
            String platformMessageId, String alias) {
        ImAdapter.FetchedMessage message = adapter.fetchMessage(platformMessageId);
        if (!conversation.getPlatformId().equals(message.conversationPlatformId())) {
            log.debug("Self command skipped: message identity drifted (message={}).", platformMessageId);
            return null;
        }
        if (!"outgoing".equals(message.direction()) || !"human".equals(message.origin())) {
            // 静默丢弃前留一行日志：origin 归因由发送后时间窗近似，被误判成 ai 时
            // 现场只能靠这行确认「命令收到但判成了回声」。
            log.debug("Self command skipped: not a human outgoing message (message={} direction={} origin={}).",
                    platformMessageId, message.direction(), message.origin());
            return null;
        }
        if (outboundLedger.findFirstByPlatformMessageId(platformMessageId).isPresent()) {
            log.debug("Self command skipped: message {} is our own send echoing back.", platformMessageId);
            return null;
        }
        if (message.kind() != MessageKind.TEXT || !StringUtils.hasText(message.body())) {
            return null;
        }
        return extractPrompt(message.body().strip(), alias);
    }

    /** 群内报告指令直出，否则走模型生成回复与媒体，最后统一记已处理。 */
    private void answer(ImAdapter adapter, ImConversation conversation, ImEvent event,
            String platformMessageId, String prompt) {
        String keyPrefix = "imaia-selfcmd-" + platformMessageId;
        boolean groupChat = adapter.isGroupConversation(event.conversationPlatformId());
        if (groupChat && answerReport(adapter, conversation, keyPrefix, prompt)) {
            adapter.markProcessed(platformMessageId);
            log.info("Self-command report sent on platform={} conversation={} for message={}.",
                    event.platform(), event.conversationPlatformId(), platformMessageId);
            return;
        }
        answerWithModel(adapter, conversation, keyPrefix, prompt, groupChat, platformMessageId);
        adapter.markProcessed(platformMessageId);
        log.info("Self-command reply sent on platform={} conversation={} for message={}.",
                event.platform(), event.conversationPlatformId(), platformMessageId);
    }

    /** 群内统计类指令直接回报告；不是报告指令则返回 false 交回模型链。 */
    private boolean answerReport(ImAdapter adapter, ImConversation conversation, String keyPrefix,
            String prompt) {
        String reportReply = insightService.handleCommand(conversation, prompt);
        if (reportReply == null) {
            return false;
        }
        outboundSender.sendText(adapter, conversation, keyPrefix, DeliverySource.SELF_COMMAND,
                reportReply);
        return true;
    }

    private void answerWithModel(ImAdapter adapter, ImConversation conversation, String keyPrefix,
            String prompt, boolean groupChat, String platformMessageId) {
        String context = groupChat
                ? groupContext.build(conversation.getId(), platformMessageId) : null;
        List<GeneratedMedia> generated = new ArrayList<>();
        AiChatService.ChatReply reply = chatService.complete(conversation, prompt, null, null,
                context, generated);
        outboundSender.sendText(adapter, conversation, keyPrefix, DeliverySource.SELF_COMMAND,
                reply.text());
        int index = 0;
        for (GeneratedMedia media : reply.generated()) {
            index++;
            outboundSender.sendMedia(adapter, conversation, keyPrefix + "-gen" + index,
                    DeliverySource.SELF_COMMAND, media);
        }
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
