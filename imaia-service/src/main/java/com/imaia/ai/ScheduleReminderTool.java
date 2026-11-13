package com.imaia.ai;

import com.imaia.domain.TaskKind;
import com.imaia.domain.TaskCreator;
import com.imaia.domain.ScheduledTask;
import com.imaia.repository.ScheduledTaskRepository;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * The schedule_reminder tool: the model books a one-shot reminder into the
 * conversation; the task runner delivers the content at fire time (quiet hours do
 * not apply — the owner asked for it).
 */
class ScheduleReminderTool implements ToolCallback {

    static final String NAME = "schedule_reminder";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final long conversationId;
    private final ScheduledTaskRepository tasks;

    ScheduleReminderTool(long conversationId, ScheduledTaskRepository tasks) {
        this.conversationId = conversationId;
        this.tasks = tasks;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Schedule a one-shot reminder message into this conversation at a given time.")
                .inputSchema("{\"type\":\"object\",\"properties\":{"
                        + "\"time\":{\"type\":\"string\",\"description\":\"提醒时间，格式 yyyy-MM-dd HH:mm（本地时间）\"},"
                        + "\"content\":{\"type\":\"string\",\"description\":\"到点要发送的提醒内容\"}},"
                        + "\"required\":[\"time\",\"content\"]}")
                .build();
    }

    @Override
    public String call(String toolInput) {
        JsonNode input = MAPPER.readTree(toolInput == null ? "{}" : toolInput);
        String time = input.path("time").asString("").strip();
        String content = input.path("content").asString("").strip();
        if (time.isEmpty() || content.isEmpty()) {
            return "schedule_reminder 失败：time 与 content 都不能为空。";
        }
        LocalDateTime remindAt;
        try {
            remindAt = LocalDateTime.parse(time, TIME_FORMAT);
        } catch (DateTimeParseException failure) {
            return "schedule_reminder 失败：时间格式应为 yyyy-MM-dd HH:mm。";
        }
        Instant fireAt = remindAt.atZone(ZoneId.systemDefault()).toInstant();
        tasks.save(new ScheduledTask(conversationId, TaskKind.REMINDER, content, null,
                fireAt, null, TaskCreator.AI, Instant.now()));
        return "提醒已设好：" + time + " 「" + content + "」。";
    }
}
