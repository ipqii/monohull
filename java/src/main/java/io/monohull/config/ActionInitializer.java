package io.monohull.config;

import io.monohull.entity.CustomActionEntity;
import io.monohull.repository.CustomActionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ActionInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ActionInitializer.class);

    private final ActionProperties actionProperties;
    private final CustomActionRepository customActionRepo;

    public ActionInitializer(ActionProperties actionProperties, CustomActionRepository customActionRepo) {
        this.actionProperties = actionProperties;
        this.customActionRepo = customActionRepo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (ActionProperties.ActionDefinition def : actionProperties.getActions()) {
            CustomActionEntity entity = customActionRepo.findByActionKey(def.getId())
                    .orElseGet(CustomActionEntity::new);
            entity.setActionKey(def.getId());
            entity.setName(def.getName());
            entity.setDescription(def.getDescription());
            entity.setTargetRole(def.getTargetRole());
            entity.setCommand(def.getCommand());
            entity.setWorkingDir(def.getWorkingDir());
            entity.setTimeoutSeconds(def.getTimeout());
            entity.setAfterAction(def.getAfterAction());
            entity.setAutoRun(def.isAutoRun());
            entity.setExecutionType(def.getExecutionType());
            entity.setRunAsUser(def.getRunAsUser());
            entity.setVerbose(def.isVerbose());
            entity.setBuiltIn(true);
            customActionRepo.save(entity);
            log.info("Upserted built-in action: {}", def.getId());
        }

        // A built-in dropped from application.yml becomes a regular user action rather
        // than lingering as an undeletable built-in. It keeps its key, so pipelines that
        // reference it still resolve, and it can be edited, rescoped or deleted.
        Set<String> definedKeys = actionProperties.getActions().stream()
                .map(ActionProperties.ActionDefinition::getId)
                .collect(Collectors.toSet());
        for (CustomActionEntity entity : customActionRepo.findByBuiltInTrue()) {
            if (!definedKeys.contains(entity.getActionKey())) {
                entity.setBuiltIn(false);
                customActionRepo.save(entity);
                log.info("Built-in action {} is no longer defined; converted to a user action",
                        entity.getActionKey());
            }
        }
    }
}
