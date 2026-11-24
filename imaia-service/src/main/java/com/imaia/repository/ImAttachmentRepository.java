package com.imaia.repository;

import com.imaia.domain.AttachmentStatus;
import com.imaia.domain.ImAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ImAttachmentRepository extends JpaRepository<ImAttachment, Long> {

    List<ImAttachment> findByMessageIdOrderByIdAsc(Long messageId);

    List<ImAttachment> findByMessageIdInOrderByIdAsc(Collection<Long> messageIds);

    List<ImAttachment> findTop100ByStatusOrderByCreatedAtAsc(AttachmentStatus status);
}
