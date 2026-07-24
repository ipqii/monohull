package io.monohull.repository;

import io.monohull.entity.PrBuildEntity;
import io.monohull.entity.PrBuildStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrBuildRepository extends JpaRepository<PrBuildEntity, Long> {

    List<PrBuildEntity> findByRepositoryIdOrderByCreatedAtDesc(Long repositoryId);

    /** Active (not-yet-terminal) builds for a PR — used to supersede an in-flight build
     *  when a new commit is pushed. */
    List<PrBuildEntity> findByRepositoryIdAndPrNumberAndStatusIn(
        Long repositoryId, int prNumber, List<PrBuildStatus> statuses);

    /** Builds that produced a still-running environment for a PR — used to tear them down
     *  when the PR closes. */
    List<PrBuildEntity> findByRepositoryIdAndPrNumberAndEnvironmentIdNotNull(Long repositoryId, int prNumber);
}
