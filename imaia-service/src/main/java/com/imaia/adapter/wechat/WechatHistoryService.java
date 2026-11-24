package com.imaia.adapter.wechat;

import com.imaia.domain.Platform;

import com.imaia.adapter.ImEvent;
import com.imaia.adapter.WechatAdapter;
import com.imaia.adapter.ImEventHandler;
import com.imaia.adapter.wechat.daemon.DaemonClient;
import com.imaia.handler.MessageArchiveHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pulls a chat's saved message history from the daemon into the archive.
 *
 * <p>The event stream only starts at the live tail, so messages sent while imaia was
 * offline are not replayed. This importer walks the daemon's newest-first message
 * pages and reuses {@link MessageArchiveHandler}, so dedupe, contact/member upsert and
 * attachment registration stay on the same path as live messages. A per-thread cursor
 * remembers how far back the console has walked, making repeated "load older" clicks
 * continue instead of rescanning the newest page.
 */
@Component
public class WechatHistoryService {

    private static final Logger log = LoggerFactory.getLogger(WechatHistoryService.class);

    public record Result(int scanned, int pages, int failed, boolean hasMore) {
    }

    private final DaemonClient daemonClient;
    private final MessageArchiveHandler archiveHandler;
    private final Map<String, String> nextCursorByThread = new ConcurrentHashMap<>();

    public WechatHistoryService(DaemonClient daemonClient, MessageArchiveHandler archiveHandler) {
        this.daemonClient = daemonClient;
        this.archiveHandler = archiveHandler;
    }

    public Result pull(String conversationPlatformId, int pages, int limit) {
        int maxPages = Math.min(50, Math.max(1, pages));
        int pageSize = Math.min(200, Math.max(1, limit));
        String cursor = nextCursorByThread.get(conversationPlatformId);
        int scanned = 0;
        int failed = 0;
        int pagesRead = 0;
        String next = null;

        for (int index = 0; index < maxPages; index += 1) {
            JsonNode page = daemonClient.listThreadMessages(conversationPlatformId, cursor, pageSize);
            JsonNode items = page.path("items");
            if (!items.isArray() || items.isEmpty()) {
                next = null;
                break;
            }
            pagesRead += 1;
            for (JsonNode item : items) {
                String platformMessageId = item.path("id").asString("");
                if (!StringUtils.hasText(platformMessageId)) {
                    continue;
                }
                scanned += 1;
                try {
                    archiveHandler.handle(new ImEvent(Platform.WECHAT,
                            ImEventHandler.KIND_MESSAGE_COMMITTED, platformMessageId, conversationPlatformId, null, item));
                } catch (RuntimeException failure) {
                    failed += 1;
                    log.warn("History import skipped message {} in {}: {}",
                            platformMessageId, conversationPlatformId, failure.getMessage());
                }
            }
            next = page.path("nextCursor").asString("");
            if (!StringUtils.hasText(next)) {
                break;
            }
            cursor = next;
        }

        if (StringUtils.hasText(next)) {
            nextCursorByThread.put(conversationPlatformId, next);
        } else {
            nextCursorByThread.remove(conversationPlatformId);
        }
        log.info("History pull for {}: scanned={}, pages={}, failed={}, hasMore={}",
                conversationPlatformId, scanned, pagesRead, failed, StringUtils.hasText(next));
        return new Result(scanned, pagesRead, failed, StringUtils.hasText(next));
    }
}
