package com.imaia.web;

import com.imaia.common.web.Result;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 内容下发端点的公共响应组装：inline 的 Content-Disposition、内容类型兜底与私有缓存。
 * 错误一律走 GlobalApiExceptionHandler 的 Result，成功才回字节流。
 */
final class MediaContentResponses {

    private MediaContentResponses() {
    }

    /** 把仓库读回的媒体字节组成 inline 友好的响应；媒体按 id 寻址、不可变，允许私有缓存。 */
    static ResponseEntity<Resource> inline(byte[] data, String mimeType, String filename) {
        Resource resource = new ByteArrayResource(data == null ? new byte[0] : data);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (StringUtils.hasText(mimeType)) {
            try {
                mediaType = MediaType.parseMediaType(mimeType.strip());
            } catch (Exception invalid) {
                // 库里可能存了非标准串，兜底为二进制流
            }
        }
        // SVG 可携带脚本，不能 inline 进控制台 origin，按附件下发
        boolean inlineSafe = !mediaType.toString().toLowerCase().contains("svg");
        String safeName = StringUtils.hasText(filename) ? filename.strip() : "media";
        ContentDisposition disposition = ContentDisposition.builder(inlineSafe ? "inline" : "attachment")
                .filename(safeName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .contentType(mediaType)
                .body(resource);
    }
}
