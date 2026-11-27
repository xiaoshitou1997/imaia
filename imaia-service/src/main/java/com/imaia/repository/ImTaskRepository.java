package com.imaia.repository;

import com.imaia.domain.ImTask;
import com.imaia.domain.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/** 定时任务的数据访问：按状态与到期时间取待执行任务。 */
public interface ImTaskRepository extends JpaRepository<ImTask, Long> {

    List<ImTask> findTop20ByStatusAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
            TaskStatus status, Instant nextRunAt);

    /** The most recent tasks (console task management). */
    Page<ImTask> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ImTask> findByStatusOrderByCreatedAtDesc(TaskStatus status, Pageable pageable);

    List<ImTask> findByConversationIdOrderByCreatedAtDesc(Long conversationId);

    long countByStatus(TaskStatus status);
}
