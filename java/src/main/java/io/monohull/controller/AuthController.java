package io.monohull.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Auth bootstrap endpoints for the SPA. Login ({@code POST /api/auth/login}) and
 * logout ({@code POST /api/auth/logout}) are handled by the Spring Security filter
 * chain; this controller only exposes the current-session lookup.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Returns the authenticated user, or 401 when there is no active session. */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(401).build();
        }
        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        return ResponseEntity.ok(Map.of(
                "username", authentication.getName(),
                "roles", roles));
    }
}
