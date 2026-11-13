package com.imaia.handler;

import com.imaia.adapter.ImAdapter;
import com.imaia.adapter.ImAdapters;
import com.imaia.adapter.ImContractException;
import com.imaia.adapter.ImEvent;
import com.imaia.adapter.ImEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Reference handler for {@code message.committed} events on any platform: it fetches
 * the committed message through the platform adapter, checks its identity against
 * the event, and logs a one-line summary. It never sends anything.
 */
@Component
@ConditionalOnProperty(prefix = "imaia.handlers.message-logging", name = "enabled", havingValue = "true")
public class LoggingMessageHandler implements ImEventHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingMessageHandler.class);
    private static final int BODY_PREVIEW_LIMIT = 160;

    private final ImAdapters adapters;

    public LoggingMessageHandler(ImAdapters adapters) {
        this.adapters = adapters;
    }

    @Override
    public boolean supports(String kind) {
        return KIND_MESSAGE_COMMITTED.equals(kind);
    }

    @Override
    public void handle(ImEvent event) {
        ImAdapter adapter = adapters.forPlatform(event.platform());
        ImAdapter.FetchedMessage message = adapter.fetchMessage(event.messageId());
        if (!event.messageId().equals(message.messageId())
                || !event.conversationId().equals(message.conversationExternalId())) {
            throw new ImContractException("Committed message identity mismatch: the event points at "
                    + event.platform() + "/" + event.conversationId() + "/" + event.messageId()
                    + " but the fetched message is " + message.conversationExternalId()
                    + "/" + message.messageId() + ".");
        }
        log.info("message platform={} conversation={} from={} category={} body={}",
                event.platform(),
                message.conversationExternalId(),
                message.senderId() == null ? "unknown" : message.senderId(),
                message.category(),
                preview(message.body()));
    }

    private static String preview(String body) {
        if (!StringUtils.hasText(body)) {
            return "(no text)";
        }
        return body.length() <= BODY_PREVIEW_LIMIT ? body : body.substring(0, BODY_PREVIEW_LIMIT) + "…";
    }
}
