package com.imaia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One attachment of an archived message. `platformRef` is the platform-side key
 * (daemon media key / feishu image_key) used for a later lazy download; the binary
 * lands under `storageRef` once fetched.
 */
@Entity
@Table(name = "im_attachment")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ImAttachment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Convert(converter = AttachmentKind.Converter.class)
    @Column(nullable = false)
    private AttachmentKind kind;

    @Column(name = "platform_ref")
    private String platformRef;

    @Column(name = "storage_ref")
    private String storageRef;

    @Column(name = "mime_type")
    private String mimeType;

    @Column
    private String filename;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column
    private Integer width;

    @Column
    private Integer height;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column
    private String sha256;

    @Convert(converter = AttachmentStatus.Converter.class)
    @Column(nullable = false)
    private AttachmentStatus status;

    @Column
    private String error;

    @Column(name = "downloaded_at")
    private Instant downloadedAt;
}
