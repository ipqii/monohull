package io.monohull.repository;

import io.monohull.entity.ActionExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActionExecutionRepository extends JpaRepository<ActionExecutionEntity, Long> {

    Optional<ActionExecutionEntity> findByExecutionId(String executionId);

    List<ActionExecutionEntity> findByEnvironmentIdOrderByStartedAtDesc(Long environmentId);

    List<ActionExecutionEntity> findByPipelineRunIdOrderBySequenceOrderAsc(String pipelineRunId);

    Optional<ActionExecutionEntity> findFirstByEnvironmentIdAndPipelineRunIdIsNotNullOrderByStartedAtDesc(Long environmentId);

    Optional<ActionExecutionEntity> findByPipelineRunIdAndSequenceOrder(String pipelineRunId, Integer sequenceOrder);
}
