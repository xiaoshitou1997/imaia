package com.imaia.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import com.imaia.domain.Contact;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    Optional<Contact> findByAccountIdAndPlatformAndExternalId(String accountId, String platform, String externalId);

    /** The most recently active contacts, newest first. */
    java.util.List<Contact> findTop200ByAccountIdOrderByLastSeenAtDesc(String accountId);
}
