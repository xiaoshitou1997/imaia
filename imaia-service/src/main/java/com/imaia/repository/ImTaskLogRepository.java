package com.imaia.repository;

import com.imaia.domain.ImTaskLog;
import com.imaia.domain.TaskLogStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 任务运行记录的数据访问：按任务分页查看执行历史。 */
public interface ImTaskLogRepository extends JpaRepository<ImTaskLog, Long> {

    Optional<ImTaskLog> findByTaskIdAndScheduledFor(Long taskId, Instant scheduledFor);

    Page<ImTaskLog> findByTaskIdOrderByScheduledForDesc(Long taskId, Pageable pageable);

    long countByStatus(TaskLogStatus status);
}
