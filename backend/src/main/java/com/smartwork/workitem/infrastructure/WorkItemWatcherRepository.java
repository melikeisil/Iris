package com.smartwork.workitem.infrastructure;

import com.smartwork.workitem.domain.WorkItemWatcher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkItemWatcherRepository extends JpaRepository<WorkItemWatcher, UUID> {

    Optional<WorkItemWatcher> findByWorkItemIdAndUserId(UUID workItemId, UUID userId);

    boolean existsByWorkItemIdAndUserId(UUID workItemId, UUID userId);

    List<WorkItemWatcher> findByWorkItemId(UUID workItemId);

    void deleteByWorkItemIdAndUserId(UUID workItemId, UUID userId);
}
