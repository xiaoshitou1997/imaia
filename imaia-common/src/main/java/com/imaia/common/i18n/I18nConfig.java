package com.imaia.common.i18n;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import com.imaia.common.error.ErrorCode;

/**
 * 国际化装配：MessageSource + 请求 locale 解析。
 *
 * <p>文案统一维护在 {@code messages*.properties}，经 {@code ErrorCode.messageKey()}
 * 关联；新增键必须双语同批补齐。缺键时用键名兜底，而不是抛
 * {@code NoSuchMessageException}——缺一次译文不应让整个请求变成 500。
 */
@Configuration
public class I18nConfig {

    /** 语言资源基名；实际文件为 messages_zh_CN.properties 与 messages_en_US.properties。 */
    public static final String BASENAME = "classpath:messages";

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename(BASENAME);
        source.setDefaultEncoding("UTF-8");
        source.setDefaultLocale(RequestLocaleResolver.DEFAULT_LOCALE);
        // 缺键回落键名，便于在界面上一眼发现未翻译项。
        source.setUseCodeAsDefaultMessage(true);
        return source;
    }

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(RequestLocaleResolver.DEFAULT_LOCALE);
        resolver.setSupportedLocales(RequestLocaleResolver.SUPPORTED);
        return resolver;
    }

    /**
     * 按目标 locale 渲染错误码文案。
     *
     * <p>缺键时不抛 {@code NoSuchMessageException}：文案缺失是配置问题，
     * 不该把一个可解释的业务失败变成 500。回落到默认语言，再退到键名。
     *
     * @param messageSource Spring 装配的语言资源
     * @param code 错误码契约
     * @param args 占位符参数
     * @param locale 目标语言；为空时用系统默认
     */
    public static String render(MessageSource messageSource, ErrorCode code, Object[] args, Locale locale) {
        Locale target = locale == null ? RequestLocaleResolver.DEFAULT_LOCALE : locale;
        try {
            return messageSource.getMessage(code.messageKey(), args, target);
        } catch (NoSuchMessageException missing) {
            try {
                return messageSource.getMessage(code.messageKey(), args, RequestLocaleResolver.DEFAULT_LOCALE);
            } catch (NoSuchMessageException alsoMissing) {
                return code.messageKey();
            }
        }
    }
}
