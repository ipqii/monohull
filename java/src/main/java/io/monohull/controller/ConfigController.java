package io.monohull.controller;

import io.monohull.dto.*;
import io.monohull.entity.EnvironmentConfigEntity;
import io.monohull.entity.EnvironmentEntity;
import io.monohull.entity.ImageConfigEntity;
import io.monohull.entity.PipelineDefinitionEntity;
import io.monohull.repository.EnvironmentConfigRepository;
import io.monohull.repository.EnvironmentRepository;
import io.monohull.repository.ImageConfigRepository;
import io.monohull.repository.PipelineDefinitionRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ConfigController {

    private final ImageConfigRepository imageConfigRepo;
    private final EnvironmentConfigRepository configRepo;
    private final EnvironmentRepository envRepo;
    private final PipelineDefinitionRepository pipelineRepo;

    public ConfigController(ImageConfigRepository imageConfigRepo, EnvironmentConfigRepository configRepo,
                            EnvironmentRepository envRepo, PipelineDefinitionRepository pipelineRepo) {
        this.imageConfigRepo = imageConfigRepo;
        this.configRepo = configRepo;
        this.envRepo = envRepo;
        this.pipelineRepo = pipelineRepo;
    }

    @GetMapping("/config/images/next-sequence")
    public ResponseEntity<Map<String, Object>> nextSequence(@RequestParam String client, @RequestParam String project) {
        long count = envRepo.countByClientAndProject(client, project);
        return ResponseEntity.ok(Map.of("nextSequence", count + 1));
    }

    // Image config CRUD

    @GetMapping("/config/images")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ImageConfigResponse>> listImageConfigs() {
        List<ImageConfigResponse> configs = imageConfigRepo.findAll().stream()
            .map(this::toImageResponse)
            .toList();
        return ResponseEntity.ok(configs);
    }

    @PostMapping("/config/images")
    @Transactional
    public ResponseEntity<ImageConfigResponse> createImageConfig(@Valid @RequestBody ImageConfigRequest req) {
        ImageConfigEntity entity = new ImageConfigEntity();
        entity.setClient(req.client());
        entity.setProject(req.project());
        entity.setMaximoVersion(req.maximoVersion());
        entity.setAppImage(req.appImage());
        entity.setDbImage(req.dbImage());
        entity.setAdmImage(req.admImage());
        entity.setDbVendor(req.dbVendor());
        entity.setDbName(req.dbName());
        entity.setDbContainerPort(req.dbContainerPort());
        entity.setDbCommand(req.dbCommand());
        entity.setHostVolumePath(req.hostVolumePath());
        entity.setDbVolumeName(req.dbVolumeName());
        entity.setDbVolumeTarget(req.dbVolumeTarget());
        entity.setWorkspacePath(req.workspacePath());
        entity.setAppHttpPort(req.appHttpPort());
        entity.setAppHttpsPort(req.appHttpsPort());
        entity.setDbPort(req.dbPort());
        entity.setMockHostPort(req.mockHostPort());
        entity.setSmtpHostPort(req.smtpHostPort());
        entity.setSmtpUiHostPort(req.smtpUiHostPort());
        applyExtras(entity, req);
        applyLaunchDefaults(entity, req);
        if (req.pipelineId() != null) {
            entity.setPipelineDefinition(pipelineRepo.findById(req.pipelineId())
                .orElseThrow(() -> new IllegalArgumentException("Pipeline not found: " + req.pipelineId())));
        } else {
            entity.setPipelineDefinition(null);
        }
        entity = imageConfigRepo.save(entity);
        return ResponseEntity.ok(toImageResponse(entity));
    }

    @PutMapping("/config/images/{id}")
    @Transactional
    public ResponseEntity<ImageConfigResponse> updateImageConfig(@PathVariable Long id, @Valid @RequestBody ImageConfigRequest req) {
        ImageConfigEntity entity = imageConfigRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Image config not found: " + id));
        entity.setClient(req.client());
        entity.setProject(req.project());
        entity.setMaximoVersion(req.maximoVersion());
        entity.setAppImage(req.appImage());
        entity.setDbImage(req.dbImage());
        entity.setAdmImage(req.admImage());
        entity.setDbVendor(req.dbVendor());
        entity.setDbName(req.dbName());
        entity.setDbContainerPort(req.dbContainerPort());
        entity.setDbCommand(req.dbCommand());
        entity.setHostVolumePath(req.hostVolumePath());
        entity.setDbVolumeName(req.dbVolumeName());
        entity.setDbVolumeTarget(req.dbVolumeTarget());
        entity.setWorkspacePath(req.workspacePath());
        entity.setAppHttpPort(req.appHttpPort());
        entity.setAppHttpsPort(req.appHttpsPort());
        entity.setDbPort(req.dbPort());
        entity.setMockHostPort(req.mockHostPort());
        entity.setSmtpHostPort(req.smtpHostPort());
        entity.setSmtpUiHostPort(req.smtpUiHostPort());
        applyExtras(entity, req);
        applyLaunchDefaults(entity, req);
        if (req.pipelineId() != null) {
            entity.setPipelineDefinition(pipelineRepo.findById(req.pipelineId())
                .orElseThrow(() -> new IllegalArgumentException("Pipeline not found: " + req.pipelineId())));
        } else {
            entity.setPipelineDefinition(null);
        }
        entity = imageConfigRepo.save(entity);
        return ResponseEntity.ok(toImageResponse(entity));
    }

    private void applyLaunchDefaults(ImageConfigEntity entity, ImageConfigRequest req) {
        entity.setLaunchDescription(req.launchDescription());
        entity.setLaunchStaticPorts(req.launchStaticPorts());
        entity.setLaunchIncludeMock(req.launchIncludeMock());
        entity.setLaunchIncludeSmtp(req.launchIncludeSmtp());
    }

    private void applyExtras(ImageConfigEntity entity, ImageConfigRequest req) {
        entity.setDbExtraEnv(req.dbExtraEnv());
        entity.setDbExtraBinds(req.dbExtraBinds());
        entity.setAppExtraEnv(req.appExtraEnv());
        entity.setAppExtraBinds(req.appExtraBinds());
        entity.setAdmExtraEnv(req.admExtraEnv());
        entity.setAdmExtraBinds(req.admExtraBinds());
    }

    @DeleteMapping("/config/images/{id}")
    public ResponseEntity<Void> deleteImageConfig(@PathVariable Long id) {
        imageConfigRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // Environment config

    @GetMapping("/environments/{id}/config")
    @Transactional(readOnly = true)
    public ResponseEntity<ConfigResponse> getConfig(@PathVariable Long id) {
        EnvironmentConfigEntity config = configRepo.findByEnvironmentId(id)
            .orElseThrow(() -> new IllegalArgumentException("Config not found for environment: " + id));
        return ResponseEntity.ok(toConfigResponse(config));
    }

    @PutMapping("/environments/{id}/pipeline")
    @Transactional
    public ResponseEntity<ConfigResponse> setEnvPipeline(@PathVariable Long id,
                                                          @RequestBody Map<String, Long> body) {
        EnvironmentEntity env = envRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + id));
        Long pipelineId = body.get("pipelineDefinitionId");
        if (pipelineId == null) {
            env.setPipelineDefinition(null);
        } else {
            PipelineDefinitionEntity pipeline = pipelineRepo.findById(pipelineId)
                .orElseThrow(() -> new IllegalArgumentException("Pipeline not found: " + pipelineId));
            if (pipeline.getEnvironment() != null && !pipeline.getEnvironment().getId().equals(id)) {
                throw new IllegalArgumentException(
                    "Pipeline " + pipelineId + " is scoped to another environment");
            }
            env.setPipelineDefinition(pipeline);
        }
        envRepo.save(env);
        EnvironmentConfigEntity config = configRepo.findByEnvironmentId(id)
            .orElseThrow(() -> new IllegalArgumentException("Config not found for environment: " + id));
        return ResponseEntity.ok(toConfigResponse(config));
    }

    @PutMapping("/environments/{id}/config")
    @Transactional
    public ResponseEntity<ConfigResponse> updateConfig(@PathVariable Long id, @RequestBody ConfigUpdateRequest req) {
        EnvironmentConfigEntity config = configRepo.findByEnvironmentId(id)
            .orElseThrow(() -> new IllegalArgumentException("Config not found for environment: " + id));

        config.setHostVolumePath(req.hostVolumePath());
        config.setDbVolumeName(req.dbVolumeName());
        config.setStaticPorts(req.staticPorts());
        config.setAppHttpPort(req.appHttpPort());
        config.setAppHttpsPort(req.appHttpsPort());
        config.setDbPort(req.dbPort());
        config.setDbPassword(req.dbPassword());
        config.setDbCommand(req.dbCommand());
        config.setDbExtraEnv(req.dbExtraEnv());
        config.setDbExtraBinds(req.dbExtraBinds());
        config.setAppExtraEnv(req.appExtraEnv());
        config.setAppExtraBinds(req.appExtraBinds());
        config.setAdmExtraEnv(req.admExtraEnv());
        config.setAdmExtraBinds(req.admExtraBinds());

        config = configRepo.save(config);
        return ResponseEntity.ok(toConfigResponse(config));
    }

    private ImageConfigResponse toImageResponse(ImageConfigEntity e) {
        PipelineDefinitionEntity pipeline = e.getPipelineDefinition();
        return new ImageConfigResponse(e.getId(), e.getClient(), e.getProject(),
            e.getMaximoVersion(), e.getAppImage(), e.getDbImage(), e.getAdmImage(),
            e.getDbVendor(), e.getDbName(), e.getDbContainerPort(), e.getDbCommand(),
            e.getHostVolumePath(), e.getDbVolumeName(), e.getDbVolumeTarget(),
            e.getWorkspacePath(),
            e.getAppHttpPort(), e.getAppHttpsPort(), e.getDbPort(),
            e.getMockHostPort(), e.getSmtpHostPort(), e.getSmtpUiHostPort(),
            pipeline != null ? pipeline.getId() : null,
            pipeline != null ? pipeline.getName() : null,
            e.getLaunchDescription(),
            e.isLaunchStaticPorts(),
            e.isLaunchIncludeMock(),
            e.isLaunchIncludeSmtp(),
            e.getCreatedAt(),
            e.getDbExtraEnv(), e.getDbExtraBinds(),
            e.getAppExtraEnv(), e.getAppExtraBinds(),
            e.getAdmExtraEnv(), e.getAdmExtraBinds());
    }

    private ConfigResponse toConfigResponse(EnvironmentConfigEntity config) {
        EnvironmentEntity env = config.getEnvironment();
        Long pipelineId = (env != null && env.getPipelineDefinition() != null)
            ? env.getPipelineDefinition().getId() : null;
        return new ConfigResponse(
            config.getId(), config.getHostVolumePath(),
            config.getDbVolumeName(), config.isStaticPorts(),
            config.getAppHttpPort(), config.getAppHttpsPort(), config.getDbPort(),
            config.getDbPassword(), config.getDbCommand(),
            config.getDbExtraEnv(), config.getDbExtraBinds(),
            config.getAppExtraEnv(), config.getAppExtraBinds(),
            config.getAdmExtraEnv(), config.getAdmExtraBinds(),
            pipelineId
        );
    }
}
