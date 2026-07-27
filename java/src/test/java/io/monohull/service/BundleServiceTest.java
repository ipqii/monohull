package io.monohull.service;

import io.monohull.dto.ImageConfigBundle;
import io.monohull.dto.ImageConfigBundle.ImageConfigPayload;
import io.monohull.dto.ImageConfigBundle.LaunchPayload;
import io.monohull.entity.ImageConfigEntity;
import io.monohull.repository.CustomActionRepository;
import io.monohull.repository.ImageConfigRepository;
import io.monohull.repository.PipelineDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BundleServiceTest {

    @Mock private ImageConfigRepository imageConfigRepo;
    @Mock private PipelineDefinitionRepository pipelineRepo;
    @Mock private CustomActionRepository customActionRepo;

    private BundleService service;

    @BeforeEach
    void setUp() {
        service = new BundleService(imageConfigRepo, pipelineRepo, customActionRepo);
    }

    private static ImageConfigEntity template() {
        ImageConfigEntity ic = new ImageConfigEntity();
        ic.setId(7L);
        ic.setClient("acme");
        ic.setProject("eam");
        ic.setMaximoVersion("7.6.1.3");
        ic.setAppImage("registry.example.com/maximo/app:7.6.1.3");
        ic.setDbImage("registry.example.com/maximo/db:7.6.1.3-demo");
        ic.setAdmImage("registry.example.com/maximo/adm:7.6.1.3");
        ic.setDbVendor("DB2");
        return ic;
    }

    private static ImageConfigPayload payload() {
        return new ImageConfigPayload(
            "acme", "eam", "7.6.1.3",
            "app:1", "db:1", "adm:1",
            "DB2", "maxdb76", null, null,
            null, null, null, null,
            null, null, null, null, null, null,
            null, null, null, null, null, null);
    }

    // ----- export -----

    @Test
    void exportCarriesLaunchDefaults() {
        ImageConfigEntity ic = template();
        ic.setLaunchDescription("7.6.1.3 + DB2 + demo data");
        ic.setLaunchStaticPorts(false);
        ic.setLaunchIncludeMock(true);
        ic.setLaunchIncludeSmtp(true);
        when(imageConfigRepo.findById(7L)).thenReturn(Optional.of(ic));

        ImageConfigBundle bundle = service.export(7L);

        assertThat(bundle.schemaVersion()).isEqualTo(ImageConfigBundle.SCHEMA_VERSION);
        assertThat(bundle.launch()).isNotNull();
        assertThat(bundle.launch().description()).isEqualTo("7.6.1.3 + DB2 + demo data");
        assertThat(bundle.launch().includeMock()).isTrue();
        assertThat(bundle.launch().includeSmtp()).isTrue();
        // false booleans are omitted from the YAML, not written as false
        assertThat(bundle.launch().staticPorts()).isNull();
    }

    // ----- import: schema versions -----

    @Test
    void importAcceptsV1BundleAndLeavesExistingLaunchDefaultsAlone() {
        ImageConfigEntity existing = template();
        existing.setLaunchDescription("keep me");
        existing.setLaunchIncludeMock(true);
        when(imageConfigRepo.findByClientAndProjectAndMaximoVersion("acme", "eam", "7.6.1.3"))
            .thenReturn(Optional.of(existing));
        when(imageConfigRepo.save(any(ImageConfigEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ImageConfigBundle v1 = new ImageConfigBundle(
            ImageConfigBundle.KIND, 1, payload(), null, List.of(), null);
        service.importBundle(v1, true);

        assertThat(existing.getLaunchDescription()).isEqualTo("keep me");
        assertThat(existing.isLaunchIncludeMock()).isTrue();
    }

    @Test
    void importAppliesV2LaunchSection() {
        when(imageConfigRepo.findByClientAndProjectAndMaximoVersion("acme", "eam", "7.6.1.3"))
            .thenReturn(Optional.empty());
        when(imageConfigRepo.save(any(ImageConfigEntity.class))).thenAnswer(inv -> {
            ImageConfigEntity e = inv.getArgument(0);
            e.setId(42L);
            return e;
        });

        ImageConfigBundle v2 = new ImageConfigBundle(
            ImageConfigBundle.KIND, 2, payload(), null, List.of(),
            new LaunchPayload("demo profile", null, Boolean.TRUE, null));
        var result = service.importBundle(v2, false);

        assertThat(result.imageConfigId()).isEqualTo(42L);
        org.mockito.ArgumentCaptor<ImageConfigEntity> captor =
            org.mockito.ArgumentCaptor.forClass(ImageConfigEntity.class);
        org.mockito.Mockito.verify(imageConfigRepo).save(captor.capture());
        ImageConfigEntity saved = captor.getValue();
        assertThat(saved.getLaunchDescription()).isEqualTo("demo profile");
        assertThat(saved.isLaunchIncludeMock()).isTrue();
        assertThat(saved.isLaunchStaticPorts()).isFalse();
        assertThat(saved.isLaunchIncludeSmtp()).isFalse();
    }

    @Test
    void importRejectsUnknownFutureSchemaVersion() {
        ImageConfigBundle v3 = new ImageConfigBundle(
            ImageConfigBundle.KIND, 3, payload(), null, List.of(), null);
        assertThatThrownBy(() -> service.importBundle(v3, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("schemaVersion");
    }
}
