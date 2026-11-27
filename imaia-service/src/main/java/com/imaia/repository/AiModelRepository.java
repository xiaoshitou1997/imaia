package com.imaia.repository;

import com.imaia.domain.AiModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 模型目录的数据访问：按启用状态与供应商读取，供能力槽位推导使用。 */
public interface AiModelRepository extends JpaRepository<AiModel, Long> {

    List<AiModel> findByEnabledTrue();

    /** The synced directory of one provider, stable order (console list). */
    List<AiModel> findByProviderIdOrderByModelKeyAsc(Long providerId);

    /** Sync-upsert keyed by raw model id (order-insensitive). */
    List<AiModel> findByProviderId(Long providerId);

    /** Models of several providers in one query (console directory). */
    List<AiModel> findByProviderIdIn(Collection<Long> providerIds);

    Optional<AiModel> findByProviderIdAndModelKey(Long providerId, String modelId);
}
