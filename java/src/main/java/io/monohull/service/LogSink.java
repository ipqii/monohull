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

  /**
   * One log line on the live stream. {@code id} is the line's build_log row id when it was
   * saved, so a client can tell which live lines it already has from the saved history (and
   * a reconnect can resume after the last one it saw); null for lines that are never saved.
   */
  public record LogEvent(Long id, String line) {}

  private final Map<String, Sinks.Many<LogEvent>> sinks = new ConcurrentHashMap<>();

  // autoCancel=false: with the default (true), the sink terminates the moment the
  // LAST SSE subscriber disconnects — so the first viewer who opens the live log
  // and navigates away permanently kills the stream for that build, and every
  // later subscriber gets an immediately-completed (Content-Length: 0) response.
  // With autoCancel off the sink survives subscriber churn; it is only ever
  // terminated by complete() when the build genuinely finishes.
  private static Sinks.Many<LogEvent> newSink() {
    return Sinks.many().multicast().onBackpressureBuffer(BUFFER_SIZE, false);
  }

  public void append(String buildId, String line) {
    append(buildId, null, line);
  }

  /** Emit a line that was saved as build_log row {@code id} (null if it wasn't saved). */
  public void append(String buildId, Long id, String line) {
    Sinks.EmitResult result = sinks
      .computeIfAbsent(buildId, k -> newSink())
      .tryEmitNext(new LogEvent(id, line));
    if (result.isFailure() && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
      // Anything other than "no subscriber" means we're actually losing data on the
      // live stream. Log it so a freeze doesn't go silent. FAIL_ZERO_SUBSCRIBER is
      // expected when no client is currently watching, so it's noise — skip it.
      log.warn("Dropped log line for buildId={} ({})", buildId, result);
    }
  }

  public Flux<String> stream(String buildId) {
    return streamEvents(buildId).map(LogEvent::line);
  }

  public Flux<LogEvent> streamEvents(String buildId) {
    return sinks
      .computeIfAbsent(buildId, k -> newSink())
      .asFlux();
  }

  public void complete(String buildId) {
    Sinks.Many<LogEvent> s = sinks.get(buildId);
    if (s != null) s.tryEmitComplete();
  }

}
