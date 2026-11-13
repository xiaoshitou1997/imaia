package com.imaia.web;

import com.imaia.domain.AiArtifact;
import com.imaia.repository.AiArtifactRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** AI 生成的媒体产物（图/视频）：投递状态与平台存储引用。 */
@RestController
@RequestMapping("/api/artifacts")
public class AiArtifactController {

    public record ArtifactRow(Long id, Long conversationId, String kind, String prompt, String modelId,
            String storageRef, String storagePlatform, String filename, String status, Instant createdAt) {
    }

    private final AiArtifactRepository artifacts;

    public AiArtifactController(AiArtifactRepository artifacts) {
        this.artifacts = artifacts;
    }

    @GetMapping
    public Map<String, Object> list() {
        List<ArtifactRow> rows = artifacts.findTop50ByOrderByCreatedAtDesc().stream()
                .map(AiArtifactController::toRow).toList();
        return ApiEnvelope.ok(rows);
    }

    private static ArtifactRow toRow(AiArtifact artifact) {
        return new ArtifactRow(artifact.getId(), artifact.getConversationId(), artifact.getKind(),
                artifact.getPrompt(), artifact.getModelId(), artifact.getStorageRef(),
                artifact.getStoragePlatform(), artifact.getFilename(), artifact.getStatus(),
                artifact.getCreatedAt());
    }
}
