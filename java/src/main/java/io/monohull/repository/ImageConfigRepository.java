package io.monohull.repository;

import io.monohull.entity.ImageConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImageConfigRepository extends JpaRepository<ImageConfigEntity, Long> {

    /**
     * Natural-key lookup matching the {@code (client, project, maximo_version)} unique
     * constraint on {@code image_config}. Used by the bundle import to find existing rows
     * before deciding between insert and overwrite.
     */
    Optional<ImageConfigEntity> findByClientAndProjectAndMaximoVersion(
        String client, String project, String maximoVersion);
}
