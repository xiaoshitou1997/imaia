package com.imaia.repository;

import com.imaia.domain.ImContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 联系人的数据访问：按平台标识 upsert 与批量取姓名。 */
public interface ImContactRepository extends JpaRepository<ImContact, Long> {

    Optional<ImContact> findByAccountIdAndPlatformId(Long accountId, String platformId);
}
