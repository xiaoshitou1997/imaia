package com.imaia;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.MutablePropertySources;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 探针契约：readiness/liveness 显式开启，且暴露面最小化。
 *
 * <p>用真实 YAML 加载 + Spring Binder 绑定校验，而不是扫文本——文本存在不代表
 * Spring 能绑定成功，也不代表 key 拼写正确。不启动完整上下文，因为它会被
 * S3MediaStorage 的构造期连通性检查拦下，而本测试要证明的只是配置契约。
 */
class HealthProbesConfigTest {

    private static Binder binder() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("test", new ClassPathResource("application.yml"));
        MutablePropertySources propertySources = new MutablePropertySources();
        sources.forEach(propertySources::addLast);
        return new Binder(ConfigurationPropertySources.from(propertySources));
    }

    @Test
    void probesAreEnabled() throws Exception {
        Boolean enabled = binder().bind("management.endpoint.health.probes.enabled", Boolean.class)
                .orElse(false);
        // Compose does not auto-enable the probe endpoints; k8s does. Both must work.
        assertEquals(Boolean.TRUE, enabled,
                "management.endpoint.health.probes.enabled 必须为 true");
    }

    @Test
    void exposureIsMinimal() throws Exception {
        List<String> included = binder()
                .bind("management.endpoints.web.exposure.include", Bindable.listOf(String.class))
                .orElse(List.of());
        assertEquals(List.of("health"), included,
                "exposure.include 只放开 health，其余按需再开");
    }

    @Test
    void livenessOmitsExternalDependencies() throws Exception {
        List<String> liveness = binder()
                .bind("management.health.group.liveness.include", Bindable.listOf(String.class))
                .orElse(List.of());
        // A datastore blip must not restart the container.
        assertEquals(List.of("livenessState"), liveness,
                "liveness 只含进程内状态，不聚合 db/redis");
    }

    @Test
    void readinessAggregatesTheDatastore() throws Exception {
        List<String> readiness = binder()
                .bind("management.health.group.readiness.include", Bindable.listOf(String.class))
                .orElse(List.of());
        assertEquals(List.of("readinessState", "db"), readiness,
                "readiness 聚合 readinessState 与 db，作为部署门禁");
    }

    @Test
    void actuatorIsOnTheClasspath() {
        // The config above is inert without the starter; fail loudly if it is dropped.
        // Boot 4 moved health out of spring-boot-actuator into spring-boot-health,
        // so the old org.springframework.boot.actuate.health.HealthEndpoint no longer exists.
        org.junit.jupiter.api.Assertions.assertTrue(
                isPresent("org.springframework.boot.health.actuate.endpoint.HealthEndpoint"),
                "spring-boot-starter-actuator 必须在依赖里");
    }

    private static boolean isPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException missing) {
            return false;
        }
    }
}
