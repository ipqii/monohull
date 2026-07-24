package io.monohull.repository;

import io.monohull.entity.BuildLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BuildLogRepository extends JpaRepository<BuildLogEntity, Long> {
    List<BuildLogEntity> findByEnvironmentIdOrderByCreatedAtAsc(Long environmentId);

    List<BuildLogEntity> findByEnvironmentIdOrderByIdAsc(Long environmentId, Pageable pageable);

    long countByEnvironmentId(Long environmentId);
}
