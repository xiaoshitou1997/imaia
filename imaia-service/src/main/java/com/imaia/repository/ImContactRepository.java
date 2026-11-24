package com.imaia.repository;

import com.imaia.domain.ImContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImContactRepository extends JpaRepository<ImContact, Long> {

    Optional<ImContact> findByAccountIdAndPlatformId(Long accountId, String platformId);
}
