package io.monohull.controller;

import io.monohull.dto.RegistryCredentialRequest;
import io.monohull.dto.RegistryCredentialResponse;
import io.monohull.entity.RegistryCredentialEntity;
import io.monohull.service.RegistryCatalogService;
import io.monohull.service.RegistryCredentialService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config/registry")
public class RegistryCredentialController {

    private final RegistryCredentialService service;
    private final RegistryCatalogService catalog;

    public RegistryCredentialController(RegistryCredentialService service, RegistryCatalogService catalog) {
        this.service = service;
        this.catalog = catalog;
    }

    @GetMapping
    public ResponseEntity<RegistryCredentialResponse> get() {
        return service.find()
            .map(e -> ResponseEntity.ok(toResponse(e)))
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    public ResponseEntity<RegistryCredentialResponse> save(@Valid @RequestBody RegistryCredentialRequest req) {
        RegistryCredentialEntity saved = service.upsert(req.url(), req.username(), req.password(), req.description());
        return ResponseEntity.ok(toResponse(saved));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete() {
        service.delete();
        return ResponseEntity.noContent().build();
    }

    /** Repository names available in the configured registry (MH-20). */
    @GetMapping("/catalog")
    public ResponseEntity<RegistryCatalogService.Catalog> catalog() {
        return ResponseEntity.ok(catalog.listRepositories());
    }

    /**
     * Tags for one repository. The name is a query parameter rather than a path variable
     * because repository names contain slashes ({@code made/app}).
     */
    @GetMapping("/tags")
    public ResponseEntity<RegistryCatalogService.Tags> tags(@RequestParam("repository") String repository) {
        return ResponseEntity.ok(catalog.listTags(repository));
    }

    private RegistryCredentialResponse toResponse(RegistryCredentialEntity e) {
        return new RegistryCredentialResponse(
            e.getId(), e.getUrl(), e.getUsername(),
            e.getPassword() != null && !e.getPassword().isBlank(),
            e.getDescription(),
            e.getCreatedAt(), e.getUpdatedAt());
    }
}