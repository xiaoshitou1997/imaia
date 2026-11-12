package com.imaia.ai;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the intent markers the system prompt teaches the model to emit:
 * [IMAGE: 描述] [VIDEO: 描述] [REMINDER: yyyy-MM-dd HH:mm | 事项] [MEMORY: 事实]
 * [ANALYZE: 联系人]. Markers are case-insensitive; unknown markers stay as text.
 */
public final class IntentParser {

    public record ReminderIntent(LocalDateTime remindAt, String content) {
    }

    public record Intents(
            List<String> images,
            List<String> videos,
            List<ReminderIntent> reminders,
            List<String> memories,
            List<String> analyses,
            String textWithoutMarkers) {
    }

    private static final DateTimeFormatter REMINDER_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Pattern ANY =
            Pattern.compile("\\s*\\[(IMAGE|VIDEO|REMINDER|MEMORY|ANALYZE):\\s*([^\\]]+)\\]",
                    Pattern.CASE_INSENSITIVE);

    public static Intents parse(String reply) {
        List<String> images = new ArrayList<>();
        List<String> videos = new ArrayList<>();
        List<ReminderIntent> reminders = new ArrayList<>();
        List<String> memories = new ArrayList<>();
        List<String> analyses = new ArrayList<>();
        Matcher matcher = ANY.matcher(reply);
        StringBuilder text = new StringBuilder();
        while (matcher.find()) {
            String kind = matcher.group(1).toUpperCase();
            String value = matcher.group(2).strip();
            switch (kind) {
                case "IMAGE" -> images.add(value);
                case "VIDEO" -> videos.add(value);
                case "MEMORY" -> memories.add(value);
                case "ANALYZE" -> analyses.add(value);
                case "REMINDER" -> parseReminder(value).ifPresentOrElse(
                        reminders::add,
                        () -> text.append(' ').append('[').append(matcher.group(2)).append(']'));
            }
            matcher.appendReplacement(text, "");
        }
        matcher.appendTail(text);
        return new Intents(images, videos, List.copyOf(reminders), List.copyOf(memories),
                List.copyOf(analyses), text.toString().strip());
    }

    /** [REMINDER: yyyy-MM-dd HH:mm | 事项] — the pipe separates time from content. */
    private static java.util.Optional<ReminderIntent> parseReminder(String value) {
        int pipe = value.indexOf('|');
        if (pipe < 0) {
            return java.util.Optional.empty();
        }
        String timePart = value.substring(0, pipe).strip();
        String content = value.substring(pipe + 1).strip();
        if (content.isEmpty()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(new ReminderIntent(LocalDateTime.parse(timePart, REMINDER_TIME), content));
        } catch (DateTimeParseException failure) {
            return java.util.Optional.empty();
        }
    }

    private IntentParser() {
    }
}
