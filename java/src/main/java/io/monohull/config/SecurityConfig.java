package io.monohull.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Spring Security for the publicly exposed Monohull UI.
 *
 * <p>Session-cookie auth driven by a custom React login page: {@code /api/**}
 * requires authentication (except the auth bootstrap endpoints), all static SPA
 * assets and forward routes are public, and unauthenticated API calls get a 401
 * rather than a redirect so the SPA can react.
 *
 * <p>CSRF uses a non-HttpOnly {@code XSRF-TOKEN} cookie with the plain
 * (non-XOR) request handler, so a JS client may read the cookie and echo it
 * verbatim in the {@code X-XSRF-TOKEN} header — which axios does automatically.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           @Value("${monohull.api.key:}") String apiKey) throws Exception {
        // Plain (non-XOR) handler: the expected header value equals the raw token
        // stored in the cookie, so a JS client can echo the cookie verbatim.
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler)
                // External git providers can't echo our CSRF token; webhook authenticity is
                // verified by per-repo signature/secret in WebhookService instead.
                .ignoringRequestMatchers("/api/webhooks/**"))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            // Service-to-service auth for external dashboards (read-only API access).
            .addFilterBefore(new ApiKeyAuthFilter(apiKey), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/me").permitAll()
                .requestMatchers("/api/webhooks/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .successHandler((req, res, a) -> res.setStatus(HttpStatus.OK.value()))
                .failureHandler((req, res, e) -> res.setStatus(HttpStatus.UNAUTHORIZED.value())))
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((req, res, a) -> res.setStatus(HttpStatus.OK.value())))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> res.setStatus(HttpStatus.UNAUTHORIZED.value())))
            .headers(headers -> headers
                // TLS terminates at the upstream proxy, so the container only sees plain
                // HTTP and request.isSecure() is false — emit HSTS on every request
                // instead of only "secure" ones (browsers ignore it over plain HTTP).
                .httpStrictTransportSecurity(hsts -> hsts
                    .requestMatcher(AnyRequestMatcher.INSTANCE)
                    .maxAgeInSeconds(31536000)
                    .includeSubDomains(true))
                .addHeaderWriter(SecurityConfig::writeContentSecurityPolicy)
                .referrerPolicy(rp -> rp.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicy(pp -> pp.policy("camera=(), microphone=(), geolocation=()")));

        return http.build();
    }

    /** Hostnames/IPs (with optional port, incl. bracketed IPv6) safe to reflect into the CSP. */
    private static final java.util.regex.Pattern SAFE_HOST =
        java.util.regex.Pattern.compile("[A-Za-z0-9.\\-\\[\\]:]+");

    /**
     * Per-request CSP, built instead of Spring's static {@code contentSecurityPolicy(...)}
     * because {@code connect-src} must name the container-terminal websocket endpoint
     * explicitly: browsers disagree on whether {@code 'self'} covers same-origin
     * {@code ws:}/{@code wss:} (w3c/webappsec-csp#7), and the ones that say no refuse the
     * connection before it is even attempted. 'unsafe-inline' styles: Emotion (MUI)
     * injects style elements at runtime.
     */
    private static void writeContentSecurityPolicy(HttpServletRequest request, HttpServletResponse response) {
        String host = request.getHeader("Host");
        String wsSources = (host != null && SAFE_HOST.matcher(host).matches())
            ? " ws://" + host + " wss://" + host
            : "";
        response.setHeader("Content-Security-Policy",
            "default-src 'self'; "
                + "script-src 'self'; "
                + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                + "font-src 'self' https://fonts.gstatic.com; "
                + "img-src 'self' data:; "
                + "connect-src 'self'" + wsSources + "; "
                + "object-src 'none'; "
                + "frame-ancestors 'none'; "
                + "base-uri 'self'; "
                + "form-action 'self'");
    }

    /**
     * Forces the deferred CSRF token to materialise so {@code CookieCsrfTokenRepository}
     * writes the {@code XSRF-TOKEN} cookie on the response — including on the SPA's
     * unauthenticated {@code GET /api/auth/me} bootstrap call.
     */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}
