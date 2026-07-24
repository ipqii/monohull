package io.monohull.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LogSink {

  private static final Logger log = LoggerFactory.getLogger(LogSink.class);

  // Reactor's default onBackpressureBuffer() caps at Queues.SMALL_BUFFER_SIZE (256).
  // The `unzip` output during build-and-deploy dumps hundreds of `inflating:` lines
  // per second; with a 256-slot buffer the SSE consumer can't drain fast enough,
  // tryEmitNext silently fails, and the live log appears to "freeze". Persistence
  // (BuildLogEntity) is unaffected because it writes from the same lambda but to
  // the DB, not through this sink — so /logs/history stays complete. 65536 leaves
  // plenty of headroom for any realistic burst without growing unbounded.
  private static final int BUFFER_SIZE = 65536;

  private final Map<String, Sinks.Many<String>> sinks = new ConcurrentHashMap<>();

  // autoCancel=false: with the default (true), the sink terminates the moment the
  // LAST SSE subscriber disconnects — so the first viewer who opens the live log
  // and navigates away permanently kills the stream for that build, and every
  // later subscriber gets an immediately-completed (Content-Length: 0) response.
  // With autoCancel off the sink survives subscriber churn; it is only ever
  // terminated by complete() when the build genuinely finishes.
  private static Sinks.Many<String> newSink() {
    return Sinks.many().multicast().onBackpressureBuffer(BUFFER_SIZE, false);
  }

  public void append(String buildId, String line) {
    Sinks.EmitResult result = sinks
      .computeIfAbsent(buildId, k -> newSink())
      .tryEmitNext(line);
    if (result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
      // Anything other than "no subscriber" means we're actually losing data on the
      // live stream. Log it so a freeze doesn't go silent. FAIL_ZERO_SUBSCRIBER is
      // expected when no client is currently watching, so it's noise — skip it.
      log.warn("Dropped log line for buildId={} ({})", buildId, result);
    }
  }

  public Flux<String> stream(String buildId) {
    return sinks
      .computeIfAbsent(buildId, k -> newSink())
      .asFlux();
  }

  public void complete(String buildId) {
    Sinks.Many<String> s = sinks.get(buildId);
    if (s != null) s.tryEmitComplete();
  }

}
