package io.monohull.repository;

import io.monohull.entity.ContainerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContainerRepository extends JpaRepository<ContainerEntity, Long> {
    Optional<ContainerEntity> findByDockerContainerId(String dockerContainerId);
}
