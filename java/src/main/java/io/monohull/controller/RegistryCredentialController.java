package io.monohull.controller;

import io.monohull.dto.RegistryCredentialRequest;
import io.monohull.dto.RegistryCredentialResponse;
import io.monohull.entity.RegistryCredentialEntity;
import io.monohull.service.RegistryCredentialService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config/registry")
public class RegistryCredentialController {

    private final RegistryCredentialService service;

    public RegistryCredentialController(RegistryCredentialService service) {
        this.service = service;
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

    private RegistryCredentialResponse toResponse(RegistryCredentialEntity e) {
        return new RegistryCredentialResponse(
            e.getId(), e.getUrl(), e.getUsername(),
            e.getPassword() != null && !e.getPassword().isBlank(),
            e.getDescription(),
            e.getCreatedAt(), e.getUpdatedAt());
    }
}