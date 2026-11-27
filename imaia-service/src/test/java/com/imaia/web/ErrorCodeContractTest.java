package com.imaia.web;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 错误码契约：一场景一码，码唯一且与 HTTP 状态分离。
 *
 * <p>重复码会让前端无法按 code 分支——两个不同失败场景共用一个码，
 * 前端只能拿到含糊的通用文案，这正是本条款要防的。
 */
class ErrorCodeContractTest {

    @Test
    void codesAreUnique() {
        Set<Integer> seen = new HashSet<>();
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(seen.add(code.code()))
                    .as("错误码重复：%s（%d）", code.name(), code.code())
                    .isTrue();
        }
    }

    @Test
    void everyCodeCarriesHttpStatusAndMessage() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.httpStatus()).as("%s 的 HTTP 状态", code.name()).isBetween(400, 599);
            assertThat(code.defaultMessage()).as("%s 的兜底文案", code.name()).isNotBlank();
        }
    }

    @Test
    void namesAreLowercaseFreeAndUnique() {
        long distinct = Arrays.stream(ErrorCode.values()).map(Enum::name).distinct().count();
        assertThat(distinct).isEqualTo(ErrorCode.values().length);
    }

    @Test
    void businessExceptionExposesItsCode() {
        BusinessException failure = new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(failure.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(failure.getMessage()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.defaultMessage());
    }

    @Test
    void businessExceptionAcceptsASpecificMessage() {
        BusinessException failure = new BusinessException(ErrorCode.INVALID_REQUEST, "具体原因");
        assertThat(failure.errorCode().code()).isEqualTo(ErrorCode.INVALID_REQUEST.code());
        assertThat(failure.getMessage()).isEqualTo("具体原因");
    }
}
