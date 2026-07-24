package io.monohull.config;

import org.springframework.context.annotation.Configuration;

/**
 * Web MVC configuration.
 *
 * <p>Deliberately registers <strong>no</strong> CORS mapping. Monohull serves its SPA
 * from the same origin in production, and the dev setup uses Vite's {@code /api}
 * proxy (also same-origin from the browser's perspective). A cross-origin
 * allow-list here is unnecessary and actively harmful: browsers attach an
 * {@code Origin} header to same-origin <em>non-GET</em> requests, so a mismatched
 * allow-list (e.g. only {@code http://localhost:3000}) makes Spring reject every
 * POST/PUT/DELETE from the real origin with 403 "Invalid CORS request" — which
 * silently broke create/remove/stop/start once Monohull was served behind a public domain.
 */
@Configuration
public class WebConfig {
}
