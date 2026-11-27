package com.imaia.config;

import tools.jackson.databind.json.JsonMapper;

/**
 * 测试用的预置入口。
 *
 * <p>生产侧的 {@code JsonSupport.installForTesting} 是包私有，不让它成为对外 API；
 * 其他包的测试经本类预置共享实例，仍然只发生在 test 源集内。
 */
public final class JsonSupportTestSeeder {

    private JsonSupportTestSeeder() {
    }

    /** 用一份与容器同源的配置预置静态实例。 */
    public static void seed() {
        JsonSupport.installForTesting(JsonMapper.builder().build());
    }

    /** 用指定实例预置（需要断言特定配置时使用）。 */
    public static void seed(JsonMapper mapper) {
        JsonSupport.installForTesting(mapper);
    }
}
