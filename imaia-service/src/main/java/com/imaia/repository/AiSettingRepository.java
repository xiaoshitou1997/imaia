package com.imaia.repository;

import com.imaia.domain.AiSetting;
import com.imaia.domain.Platform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AiSettingRepository extends JpaRepository<AiSetting, Long> {

    List<AiSetting> findByPlatformIsNullAndAccountIdIsNullAndConversationIdIsNull();

    List<AiSetting> findByPlatformAndAccountIdIsNullAndConversationIdIsNull(Platform platform);

    List<AiSetting> findByAccountIdAndConversationIdIsNull(Long accountId);

    List<AiSetting> findByConversationId(Long conversationId);

    List<AiSetting> findByConversationIdIn(Collection<Long> conversationIds);
}
