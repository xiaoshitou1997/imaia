package com.imaia.ai;

import com.imaia.config.AiModelCatalog;
import com.imaia.domain.ImConversation;
import com.imaia.domain.ImMessage;
import com.imaia.domain.MessageKind;
import com.imaia.domain.MessageDirection;
import com.imaia.domain.MessageOrigin;
import com.imaia.domain.ReportPeriod;
import com.imaia.repository.ImMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conversation insights: aggregates the archive into per-period statistics and an
 * AI-written review, returned as plain digest text (never persisted). Two outlets
 * share this service — the in-group command and scheduled report tasks.
 */
@Component
public class InsightService {

    private static final Logger log = LoggerFactory.getLogger(InsightService.class);
    /** 共享 Jackson 配置：Spring 注入，不自建实例绕过 spring.jackson.* 定制。 */
    private final JsonMapper mapper;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final int SAMPLE_MESSAGES = 40;
    private static final int SAMPLE_CHARS = 8_000;

    private final ImMessageRepository messages;
    private final AiChatService chatService;
    private final AiSettingsService settings;
    private final AiModelCatalog catalog;

    public InsightService(ImMessageRepository messages,
            AiChatService chatService, AiSettingsService settings, AiModelCatalog catalog, JsonMapper jsonMapper) {
        this.messages = messages;
        this.chatService = chatService;
        this.settings = settings;
        this.catalog = catalog;
        this.mapper = jsonMapper;
    }

    /** In-group command dispatch: 「本周统计/周报」「本月统计/月报」「本年统计/年报」.
     * Returns the report digest, or null when the body is not a report command. */
    public String handleCommand(ImConversation conversation, String body) {
        String text = body.strip();
        ReportPeriod period = null;
        if (text.contains("本周统计") || text.contains("周报")) {
            period = ReportPeriod.WEEK;
        } else if (text.contains("本月统计") || text.contains("月报")) {
            period = ReportPeriod.MONTH;
        } else if (text.contains("本年统计") || text.contains("年报")) {
            period = ReportPeriod.YEAR;
        }
        if (period == null) {
            return null;
        }
        try {
            return generateDigest(conversation, period);
        } catch (RuntimeException failure) {
            log.warn("Report command failed on conversation {}: {}", conversation.getId(),
                    failure.getMessage());
            return "报告生成失败：" + failure.getMessage();
        }
    }

    /** Aggregates the period window, writes the AI review, and returns the digest text. */
    public String generateDigest(ImConversation conversation, ReportPeriod periodType) {
        ZoneId zone = ZoneId.of(settings.current().getTimezone());
        LocalDate today = LocalDate.now(zone);
        LocalDate start = periodStart(today, periodType);
        LocalDate endExclusive = periodEndExclusive(start, periodType);
        ZonedDateTime windowStart = start.atStartOfDay(zone);
        ZonedDateTime windowEnd = endExclusive.atStartOfDay(zone);

        List<ImMessage> window = new ArrayList<>(messages
                .findTop2000ByConversationIdAndArchivedAtAfterOrderByArchivedAtDesc(
                        conversation.getId(), windowStart.toInstant()));
        window.removeIf(message -> message.getArchivedAt() == null
                || !message.getArchivedAt().isBefore(windowEnd.toInstant()));

        String statsJson = buildStats(window);
        String aiReport = null;
        if (catalog.hasChat()) {
            try {
                aiReport = chatService.completeOnce(conversation,
                        buildReportPrompt(periodType, start, endExclusive, statsJson, window)).text();
            } catch (RuntimeException failure) {
                log.warn("AI report writing failed; stats-only digest: {}", failure.getMessage());
            }
        }
        return digest(periodType, start, aiReport);
    }

    private String digest(ReportPeriod periodType, LocalDate start, String aiReport) {
        String label = switch (periodType) {
            case MONTH -> "本月";
            case YEAR -> "本年";
            default -> "本周";
        };
        String head = "【" + label + "报告 " + start + " 起】\n";
        return StringUtils.hasText(aiReport) ? head + aiReport
                : head + "AI 评析暂不可用，统计已直接附上。";
    }

    /** Aggregate statistics of the window into a JSON object. */
    private String buildStats(List<ImMessage> window) {
        int total = window.size();
        int incoming = 0;
        int aiOutgoing = 0;
        int stickers = 0;
        int textChars = 0;
        Map<String, int[]> perMemberRaw = new LinkedHashMap<>();
        Map<String, int[]> perMemberChars = new LinkedHashMap<>();
        Map<String, Integer> byDay = new LinkedHashMap<>();
        for (ImMessage message : window) {
            if (message.getDirection() == MessageDirection.INCOMING) {
                incoming++;
            }
            if (message.getOrigin() == MessageOrigin.AI) {
                aiOutgoing++;
            }
            if (message.getKind() == MessageKind.STICKER) {
                stickers++;
            }
            String body = message.getBody();
            if (StringUtils.hasText(body)) {
                textChars += body.codePointCount(0, body.length());
            }
            String key = message.getContactId() == null ? "unknown"
                    : String.valueOf(message.getContactId());
            perMemberRaw.computeIfAbsent(key, k -> new int[1])[0]++;
            perMemberChars.computeIfAbsent(key, k -> new int[1])[0] += chars(body);
            String day = message.getArchivedAt().toString().substring(0, 10);
            byDay.merge(day, 1, Integer::sum);
        }
        ObjectNode stats = mapper.createObjectNode();
        stats.put("total", total);
        stats.put("incoming", incoming);
        stats.put("aiOutgoing", aiOutgoing);
        stats.put("stickers", stickers);
        stats.put("textChars", textChars);
        ArrayNode members = stats.putArray("members");
        perMemberRaw.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, int[]>>comparingInt(e -> e.getValue()[0]).reversed())
                .forEach(entry -> {
                    ObjectNode member = members.addObject();
                    member.put("contactId", entry.getKey());
                    member.put("messages", entry.getValue()[0]);
                    member.put("chars", perMemberChars.getOrDefault(entry.getKey(), new int[1])[0]);
                });
        ArrayNode days = stats.putArray("byDay");
        byDay.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> days.addObject().put(entry.getKey(), entry.getValue()));
        return mapper.writeValueAsString(stats);
    }

    /** The writing prompt: stats JSON plus a recent-message excerpt. */
    private String buildReportPrompt(ReportPeriod periodType, LocalDate start, LocalDate endExclusive,
            String statsJson, List<ImMessage> window) {
        StringBuilder excerpt = new StringBuilder();
        int collected = 0;
        List<ImMessage> chronological = new ArrayList<>(window);
        chronological.sort(Comparator.comparing(ImMessage::getArchivedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        ZoneId zone = ZoneId.of(settings.current().getTimezone());
        for (ImMessage message : chronological.reversed()) {
            if (!StringUtils.hasText(message.getBody())) {
                continue;
            }
            String who = message.getDirection() == MessageDirection.OUTGOING
                    ? "我" : (message.getContactId() == null ? "成员"
                    : "成员" + message.getContactId());
            String line = "[" + DAY.format(message.getArchivedAt().atZone(zone)) + " " + who + "] "
                    + message.getBody();
            if (excerpt.length() + line.length() > SAMPLE_CHARS) {
                break;
            }
            excerpt.insert(0, line + '\n');
            if (++collected >= SAMPLE_MESSAGES) {
                break;
            }
        }
        return "以下是某会话 " + start + " 起" + periodLabel(periodType) + "的统计 JSON 与近期消息摘录：\n\n"
                + "统计：\n" + statsJson + "\n\n摘录（新→旧）：\n" + excerpt + "\n\n"
                + "请写这份报告：整体活跃氛围、热点话题、成员活跃点评（结合统计中的 members 数据）、一句话总结。"
                + "简洁中文分段，不要罗列原始数字。";
    }

    private static String periodLabel(ReportPeriod periodType) {
        return switch (periodType) {
            case MONTH -> "一个月";
            case YEAR -> "一年";
            default -> "一周";
        };
    }

    /** The Monday / 1st / Jan-1st that contains the given date. */
    public static LocalDate periodStart(LocalDate today, ReportPeriod periodType) {
        return switch (periodType) {
            case MONTH -> today.withDayOfMonth(1);
            case YEAR -> today.withDayOfYear(1);
            default -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        };
    }

    public static LocalDate periodEndExclusive(LocalDate start, ReportPeriod periodType) {
        return switch (periodType) {
            case MONTH -> start.plusMonths(1);
            case YEAR -> start.plusYears(1);
            default -> start.plusWeeks(1);
        };
    }

    private static int chars(String body) {
        return !StringUtils.hasText(body) ? 0 : body.codePointCount(0, body.length());
    }
}
