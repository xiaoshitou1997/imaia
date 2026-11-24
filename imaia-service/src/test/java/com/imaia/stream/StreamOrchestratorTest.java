package com.imaia.stream;

import com.imaia.domain.Platform;

import com.imaia.adapter.ImEvent;
import com.imaia.adapter.PlatformSource;
import com.imaia.config.DispatchProperties;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** Lifecycle wiring: disabled platforms are skipped, enabled ones are validated and subscribed. */
class StreamOrchestratorTest {

    @Test
    void disabledSourceIsSkippedWithoutValidation() {
        AtomicBoolean disabledValidated = new AtomicBoolean();
        PlatformSource disabled = fakeSource(Platform.WECHAT, false, disabledValidated);
        AtomicBoolean enabledValidated = new AtomicBoolean();
        PlatformSource enabled = fakeSource(Platform.FEISHU, true, enabledValidated);

        StreamOrchestrator orchestrator = new StreamOrchestrator(
                List.of(disabled, enabled), List.of(), new DispatchProperties(3));

        orchestrator.start();
        orchestrator.stop();

        assertThat(disabledValidated).isFalse();
        assertThat(enabledValidated).isTrue();
    }

    private static PlatformSource fakeSource(Platform platform, boolean enabled,
            AtomicBoolean validated) {
        return new PlatformSource() {
            @Override
            public Platform platform() {
                return platform;
            }

            @Override
            public boolean enabled() {
                return enabled;
            }

            @Override
            public void validate() {
                validated.set(true);
            }

            @Override
            public Flux<ImEvent> events(Consumer<ImEvent> dispatch) {
                return Flux.never();
            }

            @Override
            public void ack(ImEvent event) {
            }

            @Override
            public void onReset(Consumer<String> hook) {
            }

            @Override
            public boolean exitOnFailure() {
                return false;
            }
        };
    }
}
