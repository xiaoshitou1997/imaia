package com.imaia.consumer.repository;

import com.imaia.consumer.domain.PersonAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonAnalysisRepository extends JpaRepository<PersonAnalysis, Long> {

    Optional<PersonAnalysis> findByAccountIdAndContactId(String accountId, Long contactId);
}