package io.monohull.controller;

import io.monohull.dto.BundleImportResult;
import io.monohull.dto.ImageConfigBundle;
import io.monohull.service.BundleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Export an ImageConfig template (plus its pipeline and the pipeline's custom actions) as a
 * single YAML bundle, and import that bundle on another Monohull instance.
 *
 * The endpoints sit alongside {@code ConfigController} under {@code /api/config} so they
 * read as a natural extension of the existing image-config CRUD surface.
 */
@RestController
@RequestMapping("/api/config")
public class BundleController {

    private final BundleService bundleService;
    private final ObjectMapper yamlMapper;

    public BundleController(BundleService bundleService) {
        this.bundleService = bundleService;
        // - SPLIT_LINES off: long single-line strings (image tags, paths) stay on one line.
        // - WRITE_DOC_START_MARKER off: skip the leading "---" so the file opens cleanly.
        // - MINIMIZE_QUOTES on: don't quote strings that don't need it (yaml-style identifiers).
        YAMLFactory yf = YAMLFactory.builder()
            .disable(YAMLGenerator.Feature.SPLIT_LINES)
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .build();
        this.yamlMapper = new ObjectMapper(yf);
    }

    @GetMapping(value = "/images/{id}/export", produces = "application/x-yaml")
    public ResponseEntity<byte[]> exportImageConfigBundle(@PathVariable Long id) throws IOException {
        ImageConfigBundle bundle = bundleService.export(id);
        byte[] body = yamlMapper.writeValueAsBytes(bundle);
        String filename = bundleFilename(bundle);
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("application/x-yaml"))
            .body(body);
    }

    /**
     * Accept anything resembling text/yaml. Browser file uploads from a <input type="file">
     * routed through axios as a plain body land here too.
     */
    @PostMapping("/import")
    public ResponseEntity<BundleImportResult> importBundle(
            @RequestBody byte[] body,
            @RequestParam(name = "overwrite", defaultValue = "false") boolean overwrite) throws IOException {
        ImageConfigBundle bundle = yamlMapper.readValue(
            new String(body, StandardCharsets.UTF_8),
            ImageConfigBundle.class);
        BundleImportResult result = bundleService.importBundle(bundle, overwrite);
        return ResponseEntity.ok(result);
    }

    private static String bundleFilename(ImageConfigBundle bundle) {
        ImageConfigBundle.ImageConfigPayload ic = bundle.imageConfig();
        String raw = ic.client() + "-" + ic.project() + "-" + ic.maximoVersion();
        String safe = raw.toLowerCase().replaceAll("[^a-z0-9.-]+", "-").replaceAll("(^-+)|(-+$)", "");
        return safe + ".bundle.yaml";
    }
}
