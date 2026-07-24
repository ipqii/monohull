package io.monohull.controller;

import io.monohull.service.WebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public, unauthenticated endpoint git providers POST PR events to. Authenticity is verified
 * per-repo (HMAC signature / shared token) inside {@link WebhookService}; CSRF is disabled and
 * {@code /api/webhooks/**} is permit-all in SecurityConfig. The body is taken as raw bytes so
 * the HMAC is computed over exactly what the provider signed.
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/{provider}/{repoId}")
    public ResponseEntity<Map<String, Object>> receive(
            @PathVariable String provider,
            @PathVariable Long repoId,
            @RequestParam(name = "token", required = false) String token,
            @RequestHeader Map<String, String> headers,
            @RequestBody(required = false) byte[] body) {
        Map<String, Object> result = webhookService.handle(
            provider, repoId, token, body == null ? new byte[0] : body, headers);
        return ResponseEntity.accepted().body(result);
    }
}
