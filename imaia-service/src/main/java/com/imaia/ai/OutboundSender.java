package com.imaia.ai;

import com.imaia.adapter.DuplicateSendException;
import com.imaia.adapter.ImAdapter;
import com.imaia.domain.DeliverySource;
import com.imaia.domain.DeliveryStatus;
import com.imaia.domain.ImConversation;
import com.imaia.domain.ImOutboundLedger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 出账发送：每条投递先落出账账本再上平台，同一幂等键重试不重发，平台回报
 * 「键已投递」记为 skipped。AI 回复与自指令两条链共用，保证账本口径一致。
 */
@Component
public class OutboundSender {

    private static final Logger log = LoggerFactory.getLogger(OutboundSender.class);

    private final DeliveryService deliveries;

    public OutboundSender(DeliveryService deliveries) {
        this.deliveries = deliveries;
    }

    /** One text delivery: ledger row first, platform receipt after. */
    public void sendText(ImAdapter adapter, ImConversation conversation, String key,
            DeliverySource source, String text) {
        ImOutboundLedger delivery = deliveries.begin(conversation.getId(), key, source, text);
        if (delivery.getStatus() == DeliveryStatus.SENT) {
            log.debug("Delivery {} already sent; skipping.", key);
            return;
        }
        try {
            String sentMessageId = adapter.sendText(conversation.getPlatformId(), text, key);
            deliveries.markSent(delivery, sentMessageId);
        } catch (DuplicateSendException conflict) {
            deliveries.markSkipped(delivery, "platform reports the key was already delivered");
        } catch (RuntimeException failure) {
            deliveries.markFailed(delivery, failure.getMessage());
            throw failure;
        }
    }

    /** One generated-media delivery: ledger row first, upload, send, mark sent. */
    public void sendMedia(ImAdapter adapter, ImConversation conversation, String key,
            DeliverySource source, GeneratedMedia media) {
        ImOutboundLedger delivery = deliveries.begin(conversation.getId(), key, source,
                media.filename());
        if (delivery.getStatus() == DeliveryStatus.SENT) {
            log.debug("Delivery {} already sent; skipping.", key);
            return;
        }
        try {
            String storageRef = adapter.uploadMedia(media.data(), media.filename(), media.mimeType());
            String sentMessageId = adapter.sendMedia(conversation.getPlatformId(), media.kind(),
                    storageRef, key);
            deliveries.markSent(delivery, sentMessageId);
        } catch (DuplicateSendException conflict) {
            deliveries.markSkipped(delivery, "platform reports the key was already delivered");
        } catch (RuntimeException failure) {
            deliveries.markFailed(delivery, failure.getMessage());
            log.warn("Delivery of generated {} failed: {}", media.kind(), failure.getMessage());
        }
    }
}
