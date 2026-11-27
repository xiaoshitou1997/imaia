package com.imaia.ai;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.imaia.config.AiModelCatalog;
import com.imaia.domain.AiModel;
import com.imaia.domain.AiProvider;
import com.imaia.repository.AiModelRepository;
import com.imaia.repository.AiProviderRepository;

/**
 * 供应商及其模型的写入用例。
 *
 * <p>事务边界落在这里而不是 Controller：删除供应商要连带删模型，两步必须在同一
 * 事务里；把 {@code @Transactional} 挂在 Controller 上等于让 Web 层承担业务原子性，
 * 分层就失效了，也让这段逻辑无法在非 HTTP 场景复用。
 */
@Service
public class AiProviderService {

    private final AiProviderRepository providers;
    private final AiModelRepository aiModels;
    private final AiModelCatalog catalog;

    public AiProviderService(AiProviderRepository providers, AiModelRepository aiModels,
            AiModelCatalog catalog) {
        this.providers = providers;
        this.aiModels = aiModels;
        this.catalog = catalog;
    }

    /**
     * 删除供应商并移除其模型。
     *
     * @return 被一并删除的模型数量
     */
    @Transactional
    public int deleteWithModels(long providerId) {
        AiProvider provider = providers.findById(providerId).orElseThrow();
        List<AiModel> models = aiModels.findByProviderIdOrderByModelKeyAsc(provider.getId());
        aiModels.deleteAll(models);
        providers.deleteById(provider.getId());
        catalog.invalidate();
        return models.size();
    }
}
