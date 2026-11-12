package com.imaia.consumer.repository;

import com.imaia.consumer.domain.PlatformAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformAccountRepository extends JpaRepository<PlatformAccount, Long> {

    /** The enabled connection instance of one platform, lowest id first. */
    Optional<PlatformAccount> findTopByPlatformAndEnabledTrueOrderByIdAsc(String platform);
}
