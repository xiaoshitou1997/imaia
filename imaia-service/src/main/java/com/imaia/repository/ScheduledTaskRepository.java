package com.imaia.repository;

import com.imaia.domain.ScheduledTask;
import com.imaia.domain.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ScheduledTaskRepository extends JpaRepository<ScheduledTask, Long> {

    List<ScheduledTask> findTop20ByStatusAndFireAtLessThanEqualOrderByFireAtAsc(TaskStatus status, Instant fireAt);

    List<ScheduledTask> findTop20ByStatusAndNextFireAtLessThanEqualOrderByNextFireAtAsc(
            TaskStatus status, Instant nextFireAt);

    /** The most recent tasks (console task management). */
    List<ScheduledTask> findTop100ByOrderByCreatedAtDesc();

    long countByStatus(TaskStatus status);
}
