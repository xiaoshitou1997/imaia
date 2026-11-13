package com.imaia.repository;

import com.imaia.domain.ImConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImConnectionRepository extends JpaRepository<ImConnection, Long> {

    /** The enabled connection of one platform, lowest id first. */
    Optional<ImConnection> findTopByPlatformAndEnabledTrueOrderByIdAsc(String platform);

    /** All connections, stable order (console list). */
    List<ImConnection> findAllByOrderByIdAsc();
}
