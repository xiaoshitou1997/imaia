package com.imaia.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.imaia.common.error.CommonErrorCodeEnum;
import com.imaia.common.web.GlobalExceptionHandler;
import com.imaia.common.web.Result;
import com.imaia.config.AdminProperties;

/**
 * MVC 切片：只装 Web 层，不连数据库、不起外部进程。
 *
 * 这层验证的是 HTTP 契约本身——路由、请求体解析、Bean Validation 触发、
 * 统一错误包络与错误码——因此用 MockMvc 直接断言状态码与包络字段，
 * 不断言业务实现内部。
 */
@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
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
                .andExpect(jsonPath("$.code").value(CommonErrorCodeEnum.COMMON_PARAM_INVALID.code()))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void rejectsMissingBody() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCodeEnum.COMMON_PARAM_INVALID.code()));
    }

    @Test
    void loginIsClosedWhenNoPasswordConfigured() throws Exception {
        when(admin.password()).thenReturn("");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"whatever\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ImaiaErrorCodeEnum.AUTH_NOT_CONFIGURED.code()));
    }

    @Test
    void wrongCredentialsReportTheirOwnCode() throws Exception {
        when(admin.password()).thenReturn("secret");
        when(admin.username()).thenReturn("admin");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ImaiaErrorCodeEnum.AUTH_INVALID_CREDENTIALS.code()));
    }

    @Test
    void correctCredentialsReturnTheUsername() throws Exception {
        when(admin.password()).thenReturn("secret");
        when(admin.username()).thenReturn("admin");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE))
                .andExpect(jsonPath("$.data.username").value("admin"));
    }
}
