package com.imaia.common.i18n;

import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.util.StringUtils;

/**
 * 请求语言解析：默认 {@code zh-CN}，支持 {@code en-US}。
 *
 * <p>优先级：显式覆盖头 {@code X-Locale}（用户手动切换语言）> {@code Accept-Language}
 * > 系统默认。解析失败一律回落 {@code zh-CN}，不抛错——语言协商不是业务失败。
 *
 * <p>locale 一经解析，本次请求的全部用户可见输出必须用同一语言，不混排。
 *
 * <p>类名不取 {@code LocaleResolver}：那是 Spring MVC 的接口名，同名会让
 * 装配用的 bean 类型产生歧义。
 */
public final class RequestLocaleResolver {

    /** 显式语言覆盖头：用户手动切换时优先于 Accept-Language。 */
    public static final String OVERRIDE_HEADER = "X-Locale";

    /** 默认语言。 */
    public static final Locale DEFAULT_LOCALE = Locale.SIMPLIFIED_CHINESE;

    /** 支持的语言，顺序即 Accept-Language 匹配优先级。 */
    public static final List<Locale> SUPPORTED = List.of(Locale.SIMPLIFIED_CHINESE, Locale.US);

    private RequestLocaleResolver() {
    }

    /**
     * 解析目标 locale；无法识别时回落默认语言。
     *
     * @param overrideValue 覆盖头原值，可为空
     * @param acceptLanguage Accept-Language 原值，可为空
     */
    public static Locale resolve(String overrideValue, String acceptLanguage) {
        Locale fromOverride = parseTag(overrideValue);
        if (fromOverride != null) {
            return fromOverride;
        }
        Locale fromHeader = matchAccepted(acceptLanguage);
        return fromHeader != null ? fromHeader : DEFAULT_LOCALE;
    }

    /** 解析请求并写入 {@link LocaleContextHolder}，供 MessageSource 使用。 */
    public static Locale resolveForRequest(HttpServletRequest request) {
        Locale locale = resolve(request.getHeader(OVERRIDE_HEADER), request.getHeader("Accept-Language"));
        LocaleContextHolder.setLocale(locale);
        return locale;
    }

    /** 把语言标签规范化到支持清单；不支持则返回 null。 */
    static Locale parseTag(String tag) {
        if (!StringUtils.hasText(tag)) {
            return null;
        }
        String normalized = tag.trim().replace('_', '-');
        int semi = normalized.indexOf(';');
        if (semi >= 0) {
            normalized = normalized.substring(0, semi);
        }
        try {
            Locale parsed = Locale.forLanguageTag(normalized);
            return matchSupported(parsed);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** 按 Accept-Language 的声明顺序找第一个受支持的语言。 */
    static Locale matchAccepted(String acceptLanguage) {
        if (!StringUtils.hasText(acceptLanguage)) {
            return null;
        }
        for (String part : acceptLanguage.split(",")) {
            Locale candidate = parseTag(part);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    /** 语言匹配：先精确，再按语言（忽略地区）回落。 */
    static Locale matchSupported(Locale candidate) {
        for (Locale supported : SUPPORTED) {
            if (supported.equals(candidate)) {
                return supported;
            }
        }
        for (Locale supported : SUPPORTED) {
            if (supported.getLanguage().equals(candidate.getLanguage())) {
                return supported;
            }
        }
        return null;
    }
}
