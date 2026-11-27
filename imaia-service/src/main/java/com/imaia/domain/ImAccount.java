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

/**
 * One IM connection: the hub's link to an external platform instance. Platform
 * credentials and options live in {@code config} (JSONB) and are interpreted by
 * the matching adapter; the core never reads them.
 */
@Entity
@Table(name = "im_account")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ImAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 平台代码；CHECK 锁值域（wechat/feishu），枚举全链路承载。 */
    @Column(name = "platform_code", nullable = false)
    @Convert(converter = Platform.Converter.class)
    private Platform platform;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean enabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String config;

}
