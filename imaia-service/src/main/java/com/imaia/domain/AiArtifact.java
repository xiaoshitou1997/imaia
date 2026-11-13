package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Media the AI generated through its tools: prompt, delivery state and storage ref. */
@Entity
@Table(name = "ai_artifact")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiArtifact {

    public static final String KIND_IMAGE = "image";
    public static final String KIND_VIDEO = "video";
    public static final String STATUS_GENERATED = "generated";
    public static final String STATUS_DELIVERED = "delivered";
    public static final String STATUS_FAILED = "failed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    /** image | video (MediaKind value). */
    @Column(nullable = false)
    private String kind;

    @Column
    private String prompt;

    /** Logical reference to ai_model.model_id (no FK — the model may later vanish). */
    @Column(name = "model_id")
    private String modelId;

    @Column(name = "ai_message_id")
    private Long aiMessageId;

    /** The platform storage reference the media was uploaded to (daemon id / feishu key). */
    @Column(name = "storage_ref")
    private String storageRef;

    /** Which platform the storage ref belongs to. */
    @Column(name = "storage_platform", nullable = false)
    private String storagePlatform;

    @Column
    private String filename;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AiArtifact(Long conversationId, String kind, String prompt, String filename,
            String status, Instant createdAt) {
        this.conversationId = conversationId;
        this.kind = kind;
        this.prompt = prompt;
        this.filename = filename;
        this.status = status;
        this.createdAt = createdAt;
    }
}
