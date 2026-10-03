package io.monohull.config;

import io.monohull.entity.CustomActionEntity;
import io.monohull.repository.CustomActionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionInitializerTest {

    @Mock private CustomActionRepository customActionRepo;

    private static ActionProperties.ActionDefinition definition(String id) {
        ActionProperties.ActionDefinition def = new ActionProperties.ActionDefinition();
        def.setId(id);
        def.setName(id);
        def.setTargetRole("ADM");
        def.setCommand(":");
        return def;
    }

    private static CustomActionEntity builtIn(String key) {
        CustomActionEntity entity = new CustomActionEntity();
        entity.setActionKey(key);
        entity.setBuiltIn(true);
        return entity;
    }

    @Test
    void demotesBuiltInsNoLongerDefinedButKeepsDefinedOnes() {
        ActionProperties props = new ActionProperties();
        props.getActions().add(definition("build-ear"));

        CustomActionEntity buildEar = builtIn("build-ear");
        CustomActionEntity dropped = builtIn("retired-action");
        when(customActionRepo.findByActionKey("build-ear")).thenReturn(Optional.of(buildEar));
        when(customActionRepo.findByBuiltInTrue()).thenReturn(List.of(buildEar, dropped));

        new ActionInitializer(props, customActionRepo).run(null);

        assertThat(buildEar.isBuiltIn()).isTrue();
        assertThat(dropped.isBuiltIn()).isFalse();
        assertThat(dropped.getActionKey()).isEqualTo("retired-action");
        verify(customActionRepo).save(dropped);
    }
}
