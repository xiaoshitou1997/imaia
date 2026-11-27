package com.imaia.config;

import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 静态访问入口。
 *
 * <p>团队基线要求 JSON 序列化统一走 Spring 自动配置的 {@code JsonMapper} bean
 * （定制经 {@code spring.jackson.*} 集中调整），不在业务代码里散落
 * {@code JsonMapper.builder().build()}——那样会绕过全局配置。
 *
 * <p>纯静态工具类与静态辅助方法无法构造注入，因此由本类在启动时把容器里的
 * bean 装进来，静态代码经 {@link #mapper()} 取用。该类是全项目唯一的静态出口，
 * 新增静态 JSON 处理一律走它，不要再自建实例。
 */
@Component
public class JsonSupport {

    private static volatile JsonMapper instance;

    public JsonSupport(JsonMapper mapper) {
        instance = mapper;
    }

    /**
     * 仅供测试：在没有 Spring 容器的纯单元测试里预置共享实例。
     *
     * <p>生产代码不得调用——它存在只为让静态工具类可被单测覆盖，
     * 而不是为业务代码提供「自建 mapper」的后门。
     */
    static void installForTesting(JsonMapper mapper) {
        instance = mapper;
    }

    /**
     * 取共享的 Jackson 实例。
     *
     * @throws IllegalStateException 容器尚未装配时（如在构造期过早调用）
     */
    public static JsonMapper mapper() {
        JsonMapper current = instance;
        if (current == null) {
            throw new IllegalStateException(
                    "JsonMapper 尚未由 Spring 装配；静态 JSON 处理不能在容器启动前调用");
        }
        return current;
    }
}
