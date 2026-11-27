package com.imaia.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * HTTP 入口的 Bean Validation 契约：注解声明的约束失败时，
 * 统一映射为 VALIDATION_FAILED 并带上可定位的字段信息。
 */
class ValidationContractTest {

    private final GlobalApiExceptionHandler handler = new GlobalApiExceptionHandler();

    @Test
    void fieldErrorsBecomeValidationFailed() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "taskCreate");
        binding.addError(new FieldError("taskCreate", "conversationId", "不能为 null"));
        binding.addError(new FieldError("taskCreate", "timezone", "不能为空"));

        MethodArgumentNotValidException failure =
                new MethodArgumentNotValidException(null, binding);

        var response = handler.invalidBody(failure);

        assertThat(response.getStatusCode().value())
                .as("校验失败是客户端错误，不能返回 5xx")
                .isEqualTo(ErrorCode.VALIDATION_FAILED.httpStatus());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code())
                .as("错误码走枚举，不用 HTTP 状态充当业务码")
                .isEqualTo(ErrorCode.VALIDATION_FAILED.code());
        assertThat(response.getBody().message())
                .as("字段级原因要能定位到具体字段")
                .contains("conversationId")
                .contains("timezone");
    }

    @Test
    void validationFailedHasItsOwnCode() {
        // 一个场景一个错误码：不能与其他参数类错误复用。
        List<ErrorCode> paramErrors = List.of(
                ErrorCode.INVALID_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                ErrorCode.INVALID_TIME_FORMAT,
                ErrorCode.SELF_COMMAND_ALIAS_TOO_LONG);
        assertThat(paramErrors).doesNotHaveDuplicates();
    }
}
