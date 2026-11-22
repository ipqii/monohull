package io.monohull.config;

import io.monohull.entity.PipelineDefinitionEntity;
import io.monohull.entity.PipelineStepEntity;
import io.monohull.repository.PipelineDefinitionRepository;
import io.monohull.service.PipelineMarkers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Write the container-start markers (start-db, check-db-schema, start-adm) into saved
 * pipeline definitions that don't list them, so the Pipelines page shows every step a
 * build runs. Idempotent: a definition that already lists them is left alone. Steps only
 * store action keys, so this doesn't depend on the built-in actions being upserted first.
 */
@Component
public class PipelineMarkerInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PipelineMarkerInitializer.class);

    private final PipelineDefinitionRepository pipelineRepo;

    public PipelineMarkerInitializer(PipelineDefinitionRepository pipelineRepo) {
        this.pipelineRepo = pipelineRepo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (PipelineDefinitionEntity pipeline : pipelineRepo.findAllWithSteps()) {
            List<String> keys = pipeline.getSteps().stream().map(PipelineStepEntity::getActionKey).toList();
            List<String> withMarkers = PipelineMarkers.withMarkers(keys);
            if (withMarkers.equals(keys)) continue;

            pipeline.getSteps().clear();
            for (int i = 0; i < withMarkers.size(); i++) {
                PipelineStepEntity step = new PipelineStepEntity();
                step.setPipeline(pipeline);
                step.setActionKey(withMarkers.get(i));
                step.setSequenceOrder(i);
                pipeline.getSteps().add(step);
            }
            pipelineRepo.save(pipeline);
            log.info("Added container-start steps to pipeline '{}'", pipeline.getName());
        }
    }
}
