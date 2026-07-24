package io.monohull.repository;

import io.monohull.entity.EnvironmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface EnvironmentRepository extends JpaRepository<EnvironmentEntity, Long> {

    @Query("SELECT e FROM EnvironmentEntity e LEFT JOIN FETCH e.containers LEFT JOIN FETCH e.config LEFT JOIN FETCH e.imageConfig ic LEFT JOIN FETCH ic.pipelineDefinition WHERE e.id = :id")
    Optional<EnvironmentEntity> findByIdWithContainersAndConfig(Long id);

    @Query("SELECT COUNT(e) FROM EnvironmentEntity e WHERE e.imageConfig.client = :client AND e.imageConfig.project = :project")
    long countByClientAndProject(String client, String project);

    boolean existsByName(String name);
}
