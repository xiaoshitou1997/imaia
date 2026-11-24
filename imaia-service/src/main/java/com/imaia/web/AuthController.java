package com.imaia.web;

import com.imaia.config.AdminProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 控制台登录：校验环境变量 IMAIA_ADMIN_USERNAME / IMAIA_ADMIN_PASSWORD。
 * 凭据只存在于启动环境，不落库不落盘；密码未配置时端点整体拒绝（fail-closed）。
 * 比较用常量时间算法，避免时序侧信道；v0.2 控制台 API 不做会话强制，
 * 网络边界仍是 loopback 绑定（server.address=127.0.0.1）。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AdminProperties admin;

    public AuthController(AdminProperties admin) {
        this.admin = admin;
    }

    public record LoginRequest(String username, String password) {
    }

    public record LoginResponse(String username) {
    }

    @PostMapping("/login")
    public ResponseEntity<ApiEnvelope<LoginResponse>> login(@RequestBody LoginRequest body) {
        if (!StringUtils.hasText(admin.password())) {
            return ApiEnvelope.error(503, 503, "IMAIA_ADMIN_PASSWORD 未配置，控制台登录已禁用。");
        }
        boolean userOk = constantTimeEquals(body.username(), admin.username());
        boolean passOk = constantTimeEquals(body.password(), admin.password());
        if (!userOk || !passOk) {
            return ApiEnvelope.error(401, 401, "用户名或密码不正确。");
        }
        return ResponseEntity.ok(ApiEnvelope.ok(new LoginResponse(admin.username())));
    }

    /** 长度无关的常量时间比较。 */
    private static boolean constantTimeEquals(String left, String right) {
        byte[] a = (left == null ? "" : left).getBytes(StandardCharsets.UTF_8);
        byte[] b = (right == null ? "" : right).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
