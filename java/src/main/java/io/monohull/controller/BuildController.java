package io.monohull.controller;

import io.monohull.dto.BuildRequest;
import io.monohull.dto.BuildResponse;
import io.monohull.service.BuildService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/builds")
public class BuildController {

  private final BuildService buildService;

  public BuildController(BuildService buildService) {
    this.buildService = buildService;
  }

  @PostMapping("/start")
  public ResponseEntity<BuildResponse> start(@Valid @RequestBody BuildRequest req) {
    buildService.startBuild(req);
    return ResponseEntity.ok(new BuildResponse(req.buildId(), "Started", "Build kicked off"));
  }

}
