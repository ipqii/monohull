package io.monohull.repository;

import io.monohull.entity.ConnectedRepositoryEntity;
import io.monohull.entity.RepoProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConnectedRepositoryRepository extends JpaRepository<ConnectedRepositoryEntity, Long> {

    /** Matches the {@code (provider, repo_full_name)} unique constraint — used to resolve
     *  the connected repo a webhook payload belongs to. */
    Optional<ConnectedRepositoryEntity> findByProviderAndRepoFullName(RepoProvider provider, String repoFullName);
}
