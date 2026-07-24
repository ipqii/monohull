package io.monohull.controller;

import io.monohull.service.LogSink;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;


@RestController
@RequestMapping("/api/builds")
public class LogController {
  private final LogSink logs;
  public LogController(LogSink logs) { this.logs = logs; }


  @GetMapping(value="/{id}/logs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<String> stream(@PathVariable("id") String id) {
    return logs.stream(id);
  }
}
