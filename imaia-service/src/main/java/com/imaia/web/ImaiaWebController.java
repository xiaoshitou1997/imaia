package com.imaia.web;

import com.imaia.ai.AiChatService;
import com.imaia.ai.AssistantProfileService;
import com.imaia.ai.PersonAnalyzer;
import com.imaia.adapter.ImAdapters;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * Local control endpoints (loopback only): manual AI calls, proactive sends and
 * per-contact analysis. No authentication because the server binds to 127.0.0.1 —
 * never expose this port beyond the machine.
 */
@RestController
@ConditionalOnProperty(prefix = "imaia.web", name = "enabled", havingValue = "true")
@RequestMapping("/imaia")
public class ImaiaWebController {

    private static final String LOCAL_CONSOLE_THREAD = "local-console";

    private final AiChatService chatService;
    private final ImAdapters adapters;
    private final PersonAnalyzer personAnalyzer;
    private final AssistantProfileService profiles;

    public ImaiaWebController(AiChatService chatService, ImAdapters adapters,
            PersonAnalyzer personAnalyzer, AssistantProfileService profiles) {
        this.chatService = chatService;
        this.adapters = adapters;
        this.personAnalyzer = personAnalyzer;
        this.profiles = profiles;
    }

    public record AskRequest(String question) {
    }

    public record SendRequest(String threadId, String text) {
    }

    /** Talks to the AI directly (local console conversation, persisted separately). */
    @PostMapping("/ask")
    public ResponseEntity<?> ask(@RequestBody AskRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "question is empty"));
        }
        String reply = chatService.complete(adapters.defaultAdapter(), LOCAL_CONSOLE_THREAD,
                request.question().trim()).text();
        return ResponseEntity.ok(java.util.Map.of("reply", reply));
    }

    /**
     * Proactively sends one message to a whitelist thread. Every manual send still
     * counts against the reply volume caps through the daemon's idempotency ledger
     * (a fresh key per call); quiet hours are not enforced here — the caller decides.
     */
    @PostMapping("/send")
    public ResponseEntity<?> send(@RequestBody SendRequest request) {
        String threadId = request.threadId() == null ? "" : request.threadId().trim();
        String text = request.text() == null ? "" : request.text().trim();
        if (threadId.isEmpty() || text.isEmpty()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "threadId and text are required"));
        }
        if (!java.util.Arrays.stream(profiles.current().getReplyThreads().split(","))
                .map(String::trim)
                .anyMatch(t -> t.equals(threadId))) {
            return ResponseEntity.status(403).body(java.util.Map.of(
                    "error", "thread is not in imaia.ai.reply-threads; add it first"));
        }
        String key = "imaia-manual-" + java.util.UUID.randomUUID();
        adapters.defaultAdapter().sendText(threadId, text, key);
        return ResponseEntity.ok(java.util.Map.of("idempotencyKey", key));
    }

    /** Analyzes one contact from their archived messages. */
    @GetMapping("/profile")
    public ResponseEntity<?> profile(@RequestParam("name") String name) {
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "name is required"));
        }
        String profile = personAnalyzer.analyze(name.trim());
        if (profile == null) {
            return ResponseEntity.ok(java.util.Map.of("profile",
                    "归档里没有找到与「" + name + "」相关的消息。"));
        }
        return ResponseEntity.ok(java.util.Map.of("profile", profile));
    }
}
