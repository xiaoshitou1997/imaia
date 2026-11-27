package com.imaia.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.imaia.config.AiModelCatalog;
import com.imaia.domain.AiModel;
import com.imaia.domain.AiProvider;
import com.imaia.repository.AiModelRepository;
import com.imaia.repository.AiProviderRepository;

/**
 * 供应商删除用例：先确认存在、再连带删模型、最后清缓存。
 *
 * <p>验证的是调用顺序与「不存在时不写入」——真正的级联删除与事务回滚语义
 * 需要集成测试覆盖，本例不替代。
 */
class AiProviderServiceTest {

    private AiProviderRepository providers;
    private AiModelRepository models;
    private AiModelCatalog catalog;
    private AiProviderService service;

    @BeforeEach
    void setUp() {
        providers = mock(AiProviderRepository.class);
        models = mock(AiModelRepository.class);
        catalog = mock(AiModelCatalog.class);
        service = new AiProviderService(providers, models, catalog);
    }

    private AiProvider provider(long id) {
        AiProvider provider = AiProvider.builder().name("p" + id).baseUrl("http://x").build();
        provider.setId(id);
        return provider;
    }

    private AiModel model(long id, long providerId) {
        AiModel model = AiModel.builder().modelKey("m" + id).providerId(providerId).build();
        model.setId(id);
        return model;
    }

    @Test
    void missingProviderFailsBeforeAnyDelete() {
        when(providers.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteWithModels(9L))
                .isInstanceOf(NoSuchElementException.class);

        verify(models, never()).deleteAll(org.mockito.ArgumentMatchers.anyList());
        verify(providers, never()).deleteById(org.mockito.ArgumentMatchers.anyLong());
        verify(catalog, never()).invalidate();
    }

    @Test
    void deletesModelsThenProviderAndInvalidatesCache() {
        AiProvider target = provider(3L);
        List<AiModel> owned = List.of(model(10L, 3L), model(11L, 3L));
        when(providers.findById(3L)).thenReturn(Optional.of(target));
        when(models.findByProviderIdOrderByModelKeyAsc(3L)).thenReturn(owned);

        int removed = service.deleteWithModels(3L);

        assertThat(removed).as("返回值用于界面提示已移除多少模型").isEqualTo(2);
        verify(models).deleteAll(owned);
        verify(providers).deleteById(3L);
        verify(catalog).invalidate();
    }

    @Test
    void providerWithoutModelsStillDeletes() {
        when(providers.findById(4L)).thenReturn(Optional.of(provider(4L)));
        when(models.findByProviderIdOrderByModelKeyAsc(4L)).thenReturn(List.of());

        assertThat(service.deleteWithModels(4L)).isZero();
        verify(providers).deleteById(4L);
        verify(catalog).invalidate();
    }
}
