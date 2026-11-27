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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * One platform account inside one connection: the stable identity every archived
 * message and membership row points at. `alias` is the owner's remark and wins over
 * the platform nickname for display; `metadata` keeps adapter-specific extras.
 */
@Entity
@Table(name = "im_contact")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ImContact extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "platform_id", nullable = false)
    private String platformId;

    @Column(name = "display_name")
    private String displayName;

    @Column
    private String alias;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Convert(converter = ContactKind.Converter.class)
    @Column(nullable = false)
    private ContactKind kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private String metadata;

    @Column(name = "first_seen_at")
    private Instant firstSeenAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

}
