package io.monohull.controller;

import com.github.dockerjava.api.exception.DockerException;
import io.monohull.dto.BundleConflictResponse;
import io.monohull.service.BundleConflictException;
import io.monohull.service.DockerErrors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(BundleConflictException.class)
    public ResponseEntity<BundleConflictResponse> handleBundleConflict(BundleConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new BundleConflictResponse(ex.getMessage(), ex.getConflicts()));
    }

    /**
     * Referential-integrity violations (e.g. deleting an image config that environments
     * still reference — removed environments keep theirs for history) used to be a raw
     * 500. Say what actually blocks the delete.
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(
            org.springframework.dao.DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error",
            "Cannot delete: something still references this item. Environments keep their "
            + "image config for history — including removed ones — so delete or reassign "
            + "the referencing items first."));
    }

    /**
     * Docker daemon failures on synchronous endpoints (stop/start/restart/remove) used
     * to escape as a 500 with a stack trace. Translate to a plain-English cause + fix
     * (MXF-21); 502 because the daemon, not Monohull, is what failed.
     */
    @ExceptionHandler(DockerException.class)
    public ResponseEntity<Map<String, String>> handleDocker(DockerException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(Map.of("error", DockerErrors.explain(ex)));
    }
}
