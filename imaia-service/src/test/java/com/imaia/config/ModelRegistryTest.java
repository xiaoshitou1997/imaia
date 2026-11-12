package com.imaia.config;

import com.imaia.TestProperties;
import com.imaia.domain.Model;
import com.imaia.domain.Provider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Capability-driven, need-based selection from the model directory (no default flag). */
class ModelRegistryTest {

    @Test
    void textTurnsUseTheOnlyChatModel() {
        ModelRegistry registry = TestProperties.modelRegistry("http://127.0.0.1:1");

        assertThat(registry.chatFor(false).id()).isEqualTo("chat-m");
        assertThat(registry.chatFor(false).providerName()).isEqualTo("zhipu");
    }

    @Test
    void imageTurnsPreferTheModelThatDeclaresImageInput() {
        // chat-m sees no images; vision-m does. The image turn must pick vision-m.
        Provider zhipu = TestProperties.provider("zhipu", "http://127.0.0.1:1");
        ModelRegistry registry = TestProperties.registry(zhipu,
                TestProperties.model(zhipu, "chat-m", "GLM-4-Flash", "openai-completions",
                        List.of("text"), List.of("text"), 4096, Map.of()),
                TestProperties.model(zhipu, "vision-m", "GLM-4.5V", "openai-completions",
                        List.of("text", "image"), List.of("text"), 4096, Map.of()));

        // text turn: two chat models and no default — ambiguity fails fast.
        assertThatThrownBy(() -> registry.chatFor(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chat-m").hasMessageContaining("vision-m");
        // image turn: exactly one model declares image input — no ambiguity.
        assertThat(registry.chatFor(true).id()).isEqualTo("vision-m");
        assertThat(registry.chatSupportsImages()).isTrue();
    }

    @Test
    void imageTurnsStillGoToTheOnlyChatModelWhenNothingDeclaresImage() {
        Provider zhipu = TestProperties.provider("zhipu", "http://127.0.0.1:1");
        ModelRegistry registry = TestProperties.registry(zhipu,
                TestProperties.model(zhipu, "chat-m", "GLM-4-Flash", "openai-completions",
                        List.of("text"), List.of("text"), 4096, Map.of()));

        assertThat(registry.chatFor(true).id()).isEqualTo("chat-m");
        assertThat(registry.chatSupportsImages()).isFalse();
    }

    @Test
    void ambiguousChatModelsFailFast() {
        Provider zhipu = TestProperties.provider("zhipu", "http://127.0.0.1:1");
        ModelRegistry registry = TestProperties.registry(zhipu,
                TestProperties.model(zhipu, "a-m", "A", "openai-completions",
                        List.of("text"), List.of("text"), null, Map.of()),
                TestProperties.model(zhipu, "b-m", "B", "openai-completions",
                        List.of("text"), List.of("text"), null, Map.of()));

        assertThatThrownBy(() -> registry.chatFor(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("a-m").hasMessageContaining("b-m");
    }

    @Test
    void byProtocolPicksTheOnlyImageModel() {
        ModelRegistry registry = TestProperties.modelRegistry("http://127.0.0.1:1");

        assertThat(registry.byProtocol(ModelRegistry.PROTOCOL_IMAGES).id()).isEqualTo("img-m");
        assertThat(registry.byProtocol(ModelRegistry.PROTOCOL_IMAGES).param("size"))
                .isEqualTo("1024x768");
    }

    @Test
    void missingProtocolFailsFast() {
        ModelRegistry registry = TestProperties.modelRegistryWithout(
                "http://127.0.0.1:1", "openai-video");

        assertThatThrownBy(() -> registry.byProtocol(ModelRegistry.PROTOCOL_VIDEO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("openai-video");
    }

    @Test
    void noChatModelAtAllFailsFast() {
        ModelRegistry registry = TestProperties.modelRegistryWithout(
                "http://127.0.0.1:1", "openai-completions");

        assertThatThrownBy(() -> registry.chatFor(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No chat model");
    }

}
