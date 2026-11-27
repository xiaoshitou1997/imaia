package com.imaia.repository;

import com.imaia.domain.AttachmentStatus;
import com.imaia.domain.ImAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** 消息附件的数据访问：按消息读取、登记待下载与回填状态。 */
public interface ImAttachmentRepository extends JpaRepository<ImAttachment, Long> {

    List<ImAttachment> findByMessageIdOrderByIdAsc(Long messageId);

    List<ImAttachment> findByMessageIdInOrderByIdAsc(Collection<Long> messageIds);

    List<ImAttachment> findTop100ByStatusOrderByCreatedAtAsc(AttachmentStatus status);
}
