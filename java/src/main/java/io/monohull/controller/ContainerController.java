package io.monohull.controller;

import io.monohull.dto.ContainerStateResponse;
import io.monohull.service.EnvironmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/containers")
public class ContainerController {

    private final EnvironmentService envService;

    public ContainerController(EnvironmentService envService) {
        this.envService = envService;
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<ContainerStateResponse> status(@PathVariable Long id) {
        return ResponseEntity.ok(envService.getContainerLiveState(id));
    }

    @PostMapping("/{id}/restart")
    public ResponseEntity<Void> restart(@PathVariable Long id) {
        envService.restartContainer(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<Void> stop(@PathVariable Long id) {
        envService.stopContainer(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<Void> start(@PathVariable Long id) {
        envService.startContainer(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<java.util.List<String>> logs(@PathVariable Long id,
                                                      @RequestParam(defaultValue = "500") int tail) {
        return ResponseEntity.ok(envService.getContainerLogs(id, tail));
    }
}
