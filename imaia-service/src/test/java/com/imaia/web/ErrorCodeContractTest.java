package com.imaia.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import com.imaia.common.error.CommonErrorCodeEnum;
import com.imaia.common.error.ErrorCode;

/**
 * 错误码契约：一场景一码、码不可重复、每码双语齐备。
 *
 * <p>这些是前端稳定分支的前提——码重复或缺失译文都会让前端映射失效。
 */
class ErrorCodeContractTest {

    private static final List<ErrorCode> ALL = Arrays.stream(ImaiaErrorCodeEnum.values())
            .map(ErrorCode.class::cast).toList();

    private static final List<ErrorCode> COMMON = Arrays.stream(CommonErrorCodeEnum.values())
            .map(ErrorCode.class::cast).toList();

    private ReloadableResourceBundleMessageSource messages(Locale locale) {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:messages");
        source.setDefaultEncoding("UTF-8");
        source.setDefaultLocale(locale);
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }

    @Test
    void codesAreUniqueAcrossAllEnums() {
        List<String> codes = ALL.stream().map(ErrorCode::code).toList();
        assertThat(codes).doesNotHaveDuplicates();

        List<String> commonCodes = COMMON.stream().map(ErrorCode::code).toList();
        assertThat(commonCodes).doesNotHaveDuplicates();

        // 业务码与公共码也不能撞，否则前端无法区分来源。
        assertThat(codes).doesNotContainAnyElementsOf(commonCodes);
    }

    @Test
    void messageKeyIsDerivedFromCode() {
        for (ErrorCode code : ALL) {
            assertThat(code.messageKey())
                    .as("%s 的文案键必须由 code 派生", code.code())
                    .isEqualTo("error." + code.code());
        }
    }

    @Test
    void everyCodeHasBothChineseAndEnglishCopy() {
        for (ErrorCode code : ALL) {
            assertThat(hasCopy(code, Locale.SIMPLIFIED_CHINESE))
                    .as("%s 缺中文文案", code.code()).isTrue();
            assertThat(hasCopy(code, Locale.US))
                    .as("%s 缺英文文案", code.code()).isTrue();
        }
    }

    @Test
    void everyCommonCodeHasBothChineseAndEnglishCopy() {
        for (ErrorCode code : COMMON) {
            assertThat(hasCopy(code, Locale.SIMPLIFIED_CHINESE))
                    .as("%s 缺中文文案", code.code()).isTrue();
            assertThat(hasCopy(code, Locale.US))
                    .as("%s 缺英文文案", code.code()).isTrue();
        }
    }

    @Test
    void statusIsStandardHttpStatus() {
        for (ErrorCode code : ALL) {
            assertThat(code.status()).as("%s 的 HTTP 状态不能为空", code.code()).isNotNull();
            assertThat(code.status().value()).as("%s 的状态码必须在合法区间", code.code())
                    .isBetween(400, 599);
        }
    }

    private boolean hasCopy(ErrorCode code, Locale locale) {
        String text = messages(locale).getMessage(code.messageKey(), null, locale);
        // 未命中时 useCodeAsDefaultMessage=false 会抛异常；此处用包含判断兜底。
        return text != null && !text.equals(code.messageKey());
    }

    /** 便于在别处复用：全部业务码。 */
    static List<String> allCodes() {
        return Arrays.stream(ImaiaErrorCodeEnum.values()).map(ErrorCode::code).toList();
    }
}
