package com.imaia.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.imaia.config.AdminProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MVC 切片：只装 Web 层，不连数据库、不起外部进程。
 *
 * 这层验证的是 HTTP 契约本身——路由、请求体解析、Bean Validation 触发、
 * 统一错误包络——因此用 MockMvc 直接断言状态码与包络字段，
 * 不断言业务实现内部。
 */
@WebMvcTest(AuthController.class)
@Import(GlobalApiExceptionHandler.class)
class AuthControllerSliceTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AdminProperties admin;

    @Test
    void rejectsBlankUsernameBeforeReachingTheHandler() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void rejectsMissingBody() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()));
    }

    @Test
    void loginIsClosedWhenNoPasswordConfigured() throws Exception {
        when(admin.password()).thenReturn("");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"whatever\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_NOT_CONFIGURED.code()));
    }

    @Test
    void wrongCredentialsReportTheirOwnCode() throws Exception {
        when(admin.password()).thenReturn("secret");
        when(admin.username()).thenReturn("admin");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.AUTH_INVALID_CREDENTIALS.code()));
    }

    @Test
    void correctCredentialsReturnTheUsername() throws Exception {
        when(admin.password()).thenReturn("secret");
        when(admin.username()).thenReturn("admin");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("admin"));
    }
}
