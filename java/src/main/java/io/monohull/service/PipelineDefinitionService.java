package io.monohull.service;

import io.monohull.dto.*;
import io.monohull.entity.CustomActionEntity;
import io.monohull.entity.EnvironmentEntity;
import io.monohull.entity.PipelineDefinitionEntity;
import io.monohull.entity.PipelineStepEntity;
import io.monohull.repository.CustomActionRepository;
import io.monohull.repository.EnvironmentRepository;
import io.monohull.repository.PipelineDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PipelineDefinitionService {

    private final PipelineDefinitionRepository pipelineRepo;
    private final CustomActionRepository customActionRepo;
    private final EnvironmentRepository envRepo;

    public PipelineDefinitionService(PipelineDefinitionRepository pipelineRepo,
                                      CustomActionRepository customActionRepo,
                                      EnvironmentRepository envRepo) {
        this.pipelineRepo = pipelineRepo;
        this.customActionRepo = customActionRepo;
        this.envRepo = envRepo;
    }

    public List<PipelineDefinitionResponse> listPipelines() {
        return pipelineRepo.findAllWithSteps().stream()
            .map(this::toResponse)
            .toList();
    }

    public PipelineDefinitionResponse getPipeline(Long id) {
        PipelineDefinitionEntity entity = pipelineRepo.findByIdWithSteps(id)
            .orElseThrow(() -> new IllegalArgumentException("Pipeline not found: " + id));
        return toResponse(entity);
    }

    @Transactional
    public PipelineDefinitionResponse createPipeline(CreatePipelineRequest req) {
        PipelineDefinitionEntity entity = new PipelineDefinitionEntity();
        entity.setName(req.name());
        entity.setDescription(req.description());
        applyScope(entity, req.environmentId());
        replaceSteps(entity, req.steps());

        entity = pipelineRepo.save(entity);
        return toResponse(entity);
    }

    @Transactional
    public PipelineDefinitionResponse updatePipeline(Long id, CreatePipelineRequest req) {
        PipelineDefinitionEntity entity = pipelineRepo.findByIdWithSteps(id)
            .orElseThrow(() -> new IllegalArgumentException("Pipeline not found: " + id));

        entity.setName(req.name());
        entity.setDescription(req.description());
        applyScope(entity, req.environmentId());
        entity.getSteps().clear();
        replaceSteps(entity, req.steps());

        entity = pipelineRepo.save(entity);
        return toResponse(entity);
    }

    private void applyScope(PipelineDefinitionEntity entity, Long environmentId) {
        if (environmentId == null) {
            entity.setEnvironment(null);
            return;
        }
        EnvironmentEntity env = envRepo.findById(environmentId)
            .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + environmentId));
        entity.setEnvironment(env);
    }

    private void replaceSteps(PipelineDefinitionEntity entity, List<PipelineStepRequest> steps) {
        Long envId = entity.getEnvironment() != null ? entity.getEnvironment().getId() : null;
        for (PipelineStepRequest stepReq : steps) {
            CustomActionEntity action = customActionRepo.findByActionKey(stepReq.actionKey())
                .orElseThrow(() -> new IllegalArgumentException("Action not found: " + stepReq.actionKey()));

            if (!isStepActionCompatible(action, envId)) {
                throw new IllegalArgumentException(
                    "Action '" + action.getActionKey() + "' is scoped to a different environment "
                    + "and cannot be used in this pipeline");
            }

            PipelineStepEntity step = new PipelineStepEntity();
            step.setPipeline(entity);
            step.setActionKey(stepReq.actionKey());
            step.setSequenceOrder(stepReq.sequenceOrder());
            entity.getSteps().add(step);
        }
    }

    private boolean isStepActionCompatible(CustomActionEntity action, Long pipelineEnvId) {
        Long actionEnvId = action.getEnvironment() != null ? action.getEnvironment().getId() : null;
        if (actionEnvId == null) return true; // global or image-config-scoped → allowed in any pipeline scope
        if (pipelineEnvId == null) return false; // global pipeline cannot reference env-scoped action
        return actionEnvId.equals(pipelineEnvId);
    }

    @Transactional
    public void deletePipeline(Long id) {
        if (!pipelineRepo.existsById(id)) {
            throw new IllegalArgumentException("Pipeline not found: " + id);
        }
        pipelineRepo.deleteById(id);
    }

    private PipelineDefinitionResponse toResponse(PipelineDefinitionEntity entity) {
        List<PipelineStepDetailResponse> steps = entity.getSteps().stream()
            .map(step -> {
                String actionName = step.getActionKey();
                String targetRole = "UNKNOWN";
                CustomActionEntity action = customActionRepo.findByActionKey(step.getActionKey()).orElse(null);
                if (action != null) {
                    actionName = action.getName();
                    targetRole = action.getTargetRole();
                }
                return new PipelineStepDetailResponse(
                    step.getId(),
                    step.getActionKey(),
                    actionName,
                    targetRole,
                    step.getSequenceOrder()
                );
            })
            .toList();

        return new PipelineDefinitionResponse(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getEnvironment() != null ? entity.getEnvironment().getId() : null,
            steps,
            entity.getCreatedAt().toString(),
            entity.getUpdatedAt().toString()
        );
    }
}
