package io.monohull.repository;

import io.monohull.entity.PipelineDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PipelineDefinitionRepository extends JpaRepository<PipelineDefinitionEntity, Long> {

    Optional<PipelineDefinitionEntity> findByName(String name);

    @Query("SELECT DISTINCT p FROM PipelineDefinitionEntity p LEFT JOIN FETCH p.steps ORDER BY p.name")
    List<PipelineDefinitionEntity> findAllWithSteps();

    @Query("SELECT p FROM PipelineDefinitionEntity p LEFT JOIN FETCH p.steps WHERE p.id = :id")
    Optional<PipelineDefinitionEntity> findByIdWithSteps(Long id);

    /**
     * Pipelines visible to an environment: global (environment IS NULL) or scoped to this env.
     */
    @Query("""
        SELECT p FROM PipelineDefinitionEntity p
        WHERE p.environment IS NULL OR p.environment.id = :envId
        ORDER BY p.name
        """)
    List<PipelineDefinitionEntity> findVisibleForEnv(@Param("envId") Long envId);
}
