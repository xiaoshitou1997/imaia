package com.imaia.web;

import com.imaia.domain.ImConnection;
import com.imaia.repository.ImConnectionRepository;
import com.imaia.repository.ImConversationRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared conversation/connection lookups for list views (batch reads, no N+1). */
@Component
public class ConversationLabels {

    private final ImConnectionRepository connections;
    private final ImConversationRepository conversations;
    private final com.imaia.repository.ImMessageRepository messages;

    public ConversationLabels(ImConnectionRepository connections, ImConversationRepository conversations,
            com.imaia.repository.ImMessageRepository messages) {
        this.connections = connections;
        this.conversations = conversations;
        this.messages = messages;
    }

    public Map<Long, ImConnection> connectionsById() {
        Map<Long, ImConnection> map = new LinkedHashMap<>();
        for (ImConnection connection : connections.findAllByOrderByIdAsc()) {
            map.put(connection.getId(), connection);
        }
        return map;
    }

    /** platform · display-name (fallback external id) per conversation id. */
    public Map<Long, String> labels() {
        Map<Long, ImConnection> connectionById = connectionsById();
        Map<Long, String> labels = new LinkedHashMap<>();
        for (var conversation : conversations.findTop200ByOrderByLastSeenAtDesc()) {
            ImConnection connection = connectionById.get(conversation.getConnectionId());
            String prefix = connection == null ? "" : connection.getPlatform() + " · ";
            labels.put(conversation.getId(), prefix + (conversation.getDisplayName() == null
                    ? conversation.getExternalId() : conversation.getDisplayName()));
        }
        return labels;
    }

    public Map<Long, Long> messageCounts() {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Object[] row : messages.countPerConversation()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    public List<ImConnection> allConnections() {
        return connections.findAllByOrderByIdAsc();
    }
}
