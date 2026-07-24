package io.monohull.repository;

import io.monohull.entity.CustomActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomActionRepository extends JpaRepository<CustomActionEntity, Long> {

    List<CustomActionEntity> findByImageConfigIdOrImageConfigIsNull(Long imageConfigId);

    Optional<CustomActionEntity> findByActionKey(String actionKey);

    /**
     * Returns every action visible to the given environment:
     *  - globally scoped (image_config_id IS NULL AND environment_id IS NULL)
     *  - scoped to this env's image_config (image_config_id = :imageConfigId)
     *  - scoped to this env directly (environment_id = :envId)
     *
     * Passing null for either parameter simply excludes that branch (a query-driven OR).
     */
    @Query("""
        SELECT a FROM CustomActionEntity a
        WHERE (a.imageConfig IS NULL AND a.environment IS NULL)
           OR (:imageConfigId IS NOT NULL AND a.imageConfig.id = :imageConfigId)
           OR (:envId IS NOT NULL AND a.environment.id = :envId)
        """)
    List<CustomActionEntity> findVisibleForEnv(@Param("imageConfigId") Long imageConfigId,
                                               @Param("envId") Long envId);

    List<CustomActionEntity> findByEnvironmentId(Long envId);
}
