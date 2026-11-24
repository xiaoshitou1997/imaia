package com.imaia.repository;

import com.imaia.domain.ImAccount;
import com.imaia.domain.Platform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImAccountRepository extends JpaRepository<ImAccount, Long> {

    /** The enabled connection of one platform, lowest id first. */
    Optional<ImAccount> findTopByPlatformAndEnabledTrueOrderByIdAsc(Platform platform);

    /** All connections, stable order (console list). */
    List<ImAccount> findAllByOrderByIdAsc();
}
