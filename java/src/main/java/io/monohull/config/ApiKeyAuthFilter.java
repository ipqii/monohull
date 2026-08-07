package io.monohull.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authenticates service-to-service callers (CLIs, CI, external dashboards) that present a
 * static bearer key via {@code Authorization: Bearer <monohull.api.key>}. On a match the
 * request runs as a synthetic {@code made-service} principal with {@code ROLE_SERVICE},
 * satisfying the {@code /api/**} authentication requirement — for the full API, since
 * bearer requests are also exempt from CSRF (see SecurityConfig): CSRF exists to protect
 * cookie-session auth, and browsers never attach an Authorization header implicitly.
 *
 * <p>No-op when no key is configured, or when the request is already authenticated
 * (e.g. a normal user session), so it never weakens existing auth.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String SERVICE_PRINCIPAL = "made-service";
    private final byte[] expectedKey;

    public ApiKeyAuthFilter(String apiKey) {
        this.expectedKey = (apiKey == null || apiKey.isBlank())
            ? null
            : apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (expectedKey != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                byte[] presented = header.substring(7).trim().getBytes(StandardCharsets.UTF_8);
                if (MessageDigest.isEqual(expectedKey, presented)) {
                    var auth = new UsernamePasswordAuthenticationToken(
                        SERVICE_PRINCIPAL, null,
                        List.of(new SimpleGrantedAuthority("ROLE_SERVICE")));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
