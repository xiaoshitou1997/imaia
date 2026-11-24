package com.imaia.ai;

import com.imaia.repository.ImTaskRepository;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The chat model toolset for one reply. Chat is the core; everything else is a
 * tool call with its own config: generate_image / generate_video exist while
 * their endpoints are configured in the settings KV, web_search while the
 * search endpoint is configured, and the conversation-bound tool
 * (schedule_reminder) is always present. Tool side effects persist immediately.
 */
@Component
public class ChatTools {

    private final ImageGenerationClient imageClient;
    private final VideoGenerationClient videoClient;
    private final WebSearchClient webSearch;
    private final ImTaskRepository tasks;

    public ChatTools(ImageGenerationClient imageClient, VideoGenerationClient videoClient,
            WebSearchClient webSearch, ImTaskRepository tasks) {
        this.imageClient = imageClient;
        this.videoClient = videoClient;
        this.webSearch = webSearch;
        this.tasks = tasks;
    }

    /** The tools for one reply, bound to the conversation that triggered it. */
    public List<ToolCallback> tools(long conversationId, List<GeneratedMedia> collector) {
        List<ToolCallback> tools = new ArrayList<>();
        if (imageClient.available()) {
            tools.add(new ImageGenerationTool(imageClient, collector));
        }
        if (videoClient.available()) {
            tools.add(new VideoGenerationTool(videoClient, collector));
        }
        if (webSearch.available()) {
            tools.add(new WebSearchTool(webSearch));
        }
        tools.add(new ScheduleReminderTool(conversationId, tasks));
        return tools;
    }
}
