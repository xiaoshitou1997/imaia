package com.imaia.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.imaia.common.error.CommonErrorCodeEnum;
import com.imaia.common.web.GlobalExceptionHandler;

/**
 * HTTP 入口的 Bean Validation 契约：注解声明的约束失败时，
 * 统一映射为公共参数错误码，字段级明细随包络 data 下发。
 */
class ValidationContractTest {

    private final ReloadableResourceBundleMessageSource messages = messages();
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(messages);

    private static ReloadableResourceBundleMessageSource messages() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:messages");
        source.setDefaultEncoding("UTF-8");
        source.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        source.setUseCodeAsDefaultMessage(true);
        return source;
    }

    @Test
    void fieldErrorsBecomeParameterInvalid() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "taskCreate");
        binding.addError(new FieldError("taskCreate", "conversationId", "不能为 null"));
        binding.addError(new FieldError("taskCreate", "timezone", "不能为空"));

        MethodArgumentNotValidException failure =
                new MethodArgumentNotValidException(null, binding);

        var response = handler.invalidBody(failure);

        assertThat(response.getStatusCode())
                .as("校验失败是客户端错误，不能返回 5xx")
                .isEqualTo(CommonErrorCodeEnum.COMMON_PARAM_INVALID.status());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code())
                .as("错误码走字符串契约，不用 HTTP 状态充当业务码")
                .isEqualTo(CommonErrorCodeEnum.COMMON_PARAM_INVALID.code());
        assertThat(response.getBody().data())
                .as("字段级明细要能定位到具体字段")
                .extracting(GlobalExceptionHandler.FieldViolation::field)
                .containsExactly("conversationId", "timezone");
    }

    @Test
    void validationFailureUsesItsOwnCode() {
        // 一个场景一个错误码：不能与其他参数类错误复用。
        List<String> codes = Arrays.stream(ImaiaErrorCodeEnum.values())
                .map(ImaiaErrorCodeEnum::code)
                .toList();
        assertThat(codes).doesNotHaveDuplicates();
    }
}
