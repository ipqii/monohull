package io.monohull.repository;

import io.monohull.entity.RegistryCredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RegistryCredentialRepository extends JpaRepository<RegistryCredentialEntity, Long> {
    Optional<RegistryCredentialEntity> findFirstByOrderByIdAsc();
}