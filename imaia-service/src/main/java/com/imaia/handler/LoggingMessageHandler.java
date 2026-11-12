package com.imaia.handler;

import com.imaia.adapter.ImAdapter;
import com.imaia.adapter.ImAdapters;
import com.imaia.adapter.ImContractException;
import com.imaia.adapter.ImEvent;
import com.imaia.adapter.ImEventHandler;
import com.imaia.domain.ImMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Reference handler for {@code message.committed} events on any platform: it fetches
 * the committed message through the platform adapter, checks its identity against the
 * event, and logs a one-line summary. It never sends anything.
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
        ImMessage message = adapter.fetchMessage(event.messageId());
        if (!message.getMessageId().equals(event.messageId())
                || !message.getPlatform().equals(event.platform())
                || !message.getConversationId().equals(event.conversationId())) {
            throw new ImContractException("Committed message identity mismatch: the event points at "
                    + event.platform() + "/" + event.conversationId() + "/" + event.messageId()
                    + " but the archived message is " + message.getPlatform() + "/"
                    + message.getConversationId() + "/" + message.getMessageId() + ".");
        }
        log.info("message platform={} thread={} from={} category={} body={}",
                message.getPlatform(),
                message.getConversationId(),
                message.getSenderId() == null ? "unknown" : message.getSenderId(),
                message.getCategory(),
                preview(message.getBody()));
    }

    private static String preview(String body) {
        if (body == null || body.isBlank()) {
            return "(no text)";
        }
        return body.length() <= BODY_PREVIEW_LIMIT ? body : body.substring(0, BODY_PREVIEW_LIMIT) + "…";
    }
}
