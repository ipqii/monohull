package io.monohull.repository;

import io.monohull.entity.EnvironmentConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EnvironmentConfigRepository extends JpaRepository<EnvironmentConfigEntity, Long> {
    Optional<EnvironmentConfigEntity> findByEnvironmentId(Long environmentId);

    // Used-port queries feed dynamic allocation. Removed environments are excluded:
    // teardown soft-deletes the env row (status REMOVED) and keeps the config row for
    // history, so without the filter every removed env would reserve its 6 ports
    // forever and the 12000-12999 pool would eventually exhaust.

    @Query("SELECT c.appHttpPort FROM EnvironmentConfigEntity c WHERE c.appHttpPort IS NOT NULL AND c.environment.status <> io.monohull.entity.EnvironmentStatus.REMOVED")
    List<Integer> findAllUsedAppHttpPorts();

    @Query("SELECT c.appHttpsPort FROM EnvironmentConfigEntity c WHERE c.appHttpsPort IS NOT NULL AND c.environment.status <> io.monohull.entity.EnvironmentStatus.REMOVED")
    List<Integer> findAllUsedAppHttpsPorts();

    @Query("SELECT c.dbPort FROM EnvironmentConfigEntity c WHERE c.dbPort IS NOT NULL AND c.environment.status <> io.monohull.entity.EnvironmentStatus.REMOVED")
    List<Integer> findAllUsedDbPorts();

    @Query("SELECT c.mockHostPort FROM EnvironmentConfigEntity c WHERE c.mockHostPort IS NOT NULL AND c.environment.status <> io.monohull.entity.EnvironmentStatus.REMOVED")
    List<Integer> findAllUsedMockHostPorts();

    @Query("SELECT c.smtpHostPort FROM EnvironmentConfigEntity c WHERE c.smtpHostPort IS NOT NULL AND c.environment.status <> io.monohull.entity.EnvironmentStatus.REMOVED")
    List<Integer> findAllUsedSmtpHostPorts();

    @Query("SELECT c.smtpUiHostPort FROM EnvironmentConfigEntity c WHERE c.smtpUiHostPort IS NOT NULL AND c.environment.status <> io.monohull.entity.EnvironmentStatus.REMOVED")
    List<Integer> findAllUsedSmtpUiHostPorts();
}
