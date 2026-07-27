package io.monohull.service;

import io.monohull.dto.BundleImportResult;
import io.monohull.dto.CreateEnvironmentRequest;
import io.monohull.dto.EnvironmentResponse;
import io.monohull.dto.ImageConfigBundle;
import io.monohull.dto.ImageConfigBundle.ImageConfigPayload;
import io.monohull.dto.ProfileLaunchResult;
import io.monohull.entity.ImageConfigEntity;
import io.monohull.entity.PipelineDefinitionEntity;
import io.monohull.repository.EnvironmentRepository;
import io.monohull.repository.ImageConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock private ImageConfigRepository imageConfigRepo;
    @Mock private EnvironmentRepository envRepo;
    @Mock private BundleService bundleService;
    @Mock private EnvironmentService environmentService;

    private ProfileService service;

    @BeforeEach
    void setUp() {
        service = new ProfileService(imageConfigRepo, envRepo, bundleService, environmentService);
    }

    private static ImageConfigEntity profile() {
        ImageConfigEntity ic = new ImageConfigEntity();
        ic.setId(7L);
        ic.setClient("Acme");
        ic.setProject("EAM");
        ic.setMaximoVersion("7.6.1.3");
        ic.setPipelineDefinition(new PipelineDefinitionEntity());
        return ic;
    }

    private static EnvironmentResponse envResponse(String name) {
        return new EnvironmentResponse(99L, name, "build-1", "7.6.1.3", "DB2", "maxdb76",
            "PENDING", null, null, null, "tester", List.of());
    }

    // ----- name generation -----

    @Test
    void nextNameFollowsUiConventionAndSkipsTakenNames() {
        ImageConfigEntity ic = profile();
        when(envRepo.countByClientAndProject("Acme", "EAM")).thenReturn(2L);
        when(envRepo.existsByName("monohull-acme-eam-3")).thenReturn(true);
        when(envRepo.existsByName("monohull-acme-eam-4")).thenReturn(false);

        assertThat(service.nextEnvironmentName(ic)).isEqualTo("monohull-acme-eam-4");
    }

    // ----- launch by id -----

    @Test
    void launchUsesStoredDefaultsAndGeneratedName() {
        ImageConfigEntity ic = profile();
        ic.setLaunchStaticPorts(true);
        ic.setLaunchIncludeMock(true);
        when(imageConfigRepo.findById(7L)).thenReturn(Optional.of(ic));
        when(envRepo.countByClientAndProject("Acme", "EAM")).thenReturn(0L);
        when(envRepo.existsByName("monohull-acme-eam-1")).thenReturn(false);
        when(environmentService.createEnvironment(any())).thenReturn(envResponse("monohull-acme-eam-1"));

        ProfileLaunchResult result = service.launch(7L);

        ArgumentCaptor<CreateEnvironmentRequest> captor = ArgumentCaptor.forClass(CreateEnvironmentRequest.class);
        verify(environmentService).createEnvironment(captor.capture());
        CreateEnvironmentRequest req = captor.getValue();
        assertThat(req.name()).isEqualTo("monohull-acme-eam-1");
        assertThat(req.imageConfigId()).isEqualTo(7L);
        assertThat(req.staticPorts()).isTrue();
        assertThat(req.includeMock()).isTrue();
        assertThat(req.includeSmtp()).isFalse();
        assertThat(result.environment().name()).isEqualTo("monohull-acme-eam-1");
        assertThat(result.importResult()).isNull();
    }

    @Test
    void launchRefusesProfileWithoutPipeline() {
        ImageConfigEntity ic = profile();
        ic.setPipelineDefinition(null);
        when(imageConfigRepo.findById(7L)).thenReturn(Optional.of(ic));

        assertThatThrownBy(() -> service.launch(7L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("pipeline");
    }

    // ----- launch from uploaded bundle -----

    private static ImageConfigBundle bundle() {
        return new ImageConfigBundle(ImageConfigBundle.KIND, 2,
            new ImageConfigPayload("Acme", "EAM", "7.6.1.3",
                "app:1", "db:1", "adm:1", "DB2", null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null),
            null, List.of(), null);
    }

    @Test
    void launchBundleSkipsImportWhenTemplateExists() {
        ImageConfigEntity existing = profile();
        when(imageConfigRepo.findByClientAndProjectAndMaximoVersion("Acme", "EAM", "7.6.1.3"))
            .thenReturn(Optional.of(existing));
        when(imageConfigRepo.findById(7L)).thenReturn(Optional.of(existing));
        when(envRepo.countByClientAndProject("Acme", "EAM")).thenReturn(0L);
        when(envRepo.existsByName(any())).thenReturn(false);
        when(environmentService.createEnvironment(any())).thenReturn(envResponse("monohull-acme-eam-1"));

        ProfileLaunchResult result = service.launchBundle(bundle(), false);

        verify(bundleService, never()).importBundle(any(), anyBoolean());
        assertThat(result.importSkipped()).isTrue();
        assertThat(result.environment()).isNotNull();
    }

    @Test
    void launchBundleImportsWhenTemplateAbsent() {
        ImageConfigEntity imported = profile();
        when(imageConfigRepo.findByClientAndProjectAndMaximoVersion("Acme", "EAM", "7.6.1.3"))
            .thenReturn(Optional.empty());
        when(bundleService.importBundle(any(), anyBoolean())).thenReturn(new BundleImportResult(
            BundleImportResult.Outcome.CREATED, 7L, BundleImportResult.Outcome.NONE, null,
            List.of(), List.of(), List.of()));
        when(imageConfigRepo.findById(7L)).thenReturn(Optional.of(imported));
        when(envRepo.countByClientAndProject("Acme", "EAM")).thenReturn(0L);
        when(envRepo.existsByName(any())).thenReturn(false);
        when(environmentService.createEnvironment(any())).thenReturn(envResponse("monohull-acme-eam-1"));

        ProfileLaunchResult result = service.launchBundle(bundle(), false);

        verify(bundleService).importBundle(any(), anyBoolean());
        assertThat(result.importResult()).isNotNull();
        assertThat(result.importSkipped()).isNull();
    }
}
