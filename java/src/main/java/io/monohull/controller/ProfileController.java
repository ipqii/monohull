package io.monohull.controller;

import io.monohull.dto.ImageConfigBundle;
import io.monohull.dto.ProfileLaunchResult;
import io.monohull.service.ProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * One-click profile launches (MXF-20): launch a stored profile (an image config plus its
 * launch defaults) by id, or upload a shared bundle YAML which is imported-if-absent and
 * launched in one call. Environment names are generated server-side; the response carries
 * the new environment so the UI can jump straight to its live build log.
 */
@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    private final ProfileService profileService;
    // Read-only YAML mapper for uploaded bundles. Deliberately a local instance, not a
    // bean: registering a second ObjectMapper bean would make Spring Boot's JSON
    // auto-configuration back off. Same story in BundleController.
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PostMapping("/{imageConfigId}/launch")
    public ResponseEntity<ProfileLaunchResult> launch(@PathVariable Long imageConfigId) {
        return ResponseEntity.ok(profileService.launch(imageConfigId));
    }

    @PostMapping("/launch")
    public ResponseEntity<ProfileLaunchResult> launchBundle(
            @RequestBody byte[] body,
            @RequestParam(name = "overwrite", defaultValue = "false") boolean overwrite) throws IOException {
        ImageConfigBundle bundle = yamlMapper.readValue(
            new String(body, StandardCharsets.UTF_8),
            ImageConfigBundle.class);
        return ResponseEntity.ok(profileService.launchBundle(bundle, overwrite));
    }
}
