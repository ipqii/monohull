package io.monohull.controller;

import io.monohull.dto.CreatePipelineRequest;
import io.monohull.dto.PipelineDefinitionResponse;
import io.monohull.service.PipelineDefinitionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/config/pipelines")
public class PipelineDefinitionController {

    private final PipelineDefinitionService pipelineService;

    public PipelineDefinitionController(PipelineDefinitionService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @GetMapping
    public ResponseEntity<List<PipelineDefinitionResponse>> listPipelines() {
        return ResponseEntity.ok(pipelineService.listPipelines());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PipelineDefinitionResponse> getPipeline(@PathVariable Long id) {
        return ResponseEntity.ok(pipelineService.getPipeline(id));
    }

    @PostMapping
    public ResponseEntity<PipelineDefinitionResponse> createPipeline(@Valid @RequestBody CreatePipelineRequest req) {
        return ResponseEntity.ok(pipelineService.createPipeline(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PipelineDefinitionResponse> updatePipeline(@PathVariable Long id,
                                                                       @Valid @RequestBody CreatePipelineRequest req) {
        return ResponseEntity.ok(pipelineService.updatePipeline(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePipeline(@PathVariable Long id) {
        pipelineService.deletePipeline(id);
        return ResponseEntity.noContent().build();
    }
}
