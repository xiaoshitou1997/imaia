package com.imaia.consumer.adapter.wechat.handler;

import com.imaia.consumer.adapter.wechat.daemon.DaemonClient;
import com.imaia.consumer.adapter.wechat.daemon.DaemonContractException;
import com.imaia.consumer.adapter.wechat.daemon.dto.Message;
import com.imaia.consumer.adapter.wechat.DaemonEventHandler;
import com.imaia.consumer.adapter.wechat.sse.DaemonEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Reference handler for {@code message.committed} events: it fetches the committed
 * message, checks its identity against the event the way the daemon's own consumers
 * do, and logs a one-line summary. It never sends anything.
 */
@Component
@ConditionalOnProperty(prefix = "imaia.handlers.message-logging", name = "enabled", havingValue = "true")
public class LoggingMessageHandler implements DaemonEventHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingMessageHandler.class);
    private static final int BODY_PREVIEW_LIMIT = 160;

    private final DaemonClient daemonClient;

    public LoggingMessageHandler(DaemonClient daemonClient) {
        this.daemonClient = daemonClient;
    }

    @Override
    public boolean supports(String eventType) {
        return "message.committed".equals(eventType);
    }

    @Override
    public void handle(DaemonEvent event) {
        String messageId = requiredText(event.data(), "messageId");
        String threadId = requiredText(event.data(), "threadId");
        Message message = daemonClient.getMessage(messageId);
        String accountId = daemonClient.accountId();
        if (!message.id().equals(messageId) || !message.accountId().equals(accountId)
                || !message.threadId().equals(threadId)) {
            throw new DaemonContractException("Committed message identity mismatch: the event points at "
                    + accountId + "/" + threadId + "/" + messageId + " but the saved message is "
                    + message.accountId() + "/" + message.threadId() + "/" + message.id() + ".");
        }
        log.info("message thread={} from={} type={} body={}",
                message.threadId(),
                message.senderId() == null ? "unknown" : message.senderId(),
                message.type(),
                preview(message.body()));
    }

    private static String requiredText(JsonNode data, String field) {
        JsonNode value = data.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new DaemonContractException("Event data field '" + field + "' is missing or not a non-empty string.");
        }
        return value.asString();
    }

    private static String preview(String body) {
        if (body == null || body.isBlank()) {
            return "(no text)";
        }
        return body.length() <= BODY_PREVIEW_LIMIT ? body : body.substring(0, BODY_PREVIEW_LIMIT) + "…";
    }
}
