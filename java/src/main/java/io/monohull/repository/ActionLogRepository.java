package io.monohull.repository;

import io.monohull.entity.ActionLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActionLogRepository extends JpaRepository<ActionLogEntity, Long> {

    List<ActionLogEntity> findByExecutionIdOrderByCreatedAtAsc(Long executionId);
}
