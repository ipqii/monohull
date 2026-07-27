package io.monohull.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.monohull.entity.RegistryCredentialEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Browses the configured private registry over the Docker Registry HTTP API V2, so the
 * Registry page can show which images are actually available (MH-20).
 *
 * <p>Auth is whatever the registry asks for. A plain {@code registry:2} behind htpasswd
 * takes Basic directly; registries fronted by a token service answer 401 with a
 * {@code WWW-Authenticate: Bearer realm=...} challenge, which we satisfy by exchanging the
 * stored Basic credentials for a scoped bearer token and retrying once.
 */
@Service
public class RegistryCatalogService {

    private static final Logger log = LoggerFactory.getLogger(RegistryCatalogService.class);

    /** Registries page the catalog via RFC 5988 Link headers; cap the walk so a misbehaving one can't spin us. */
    private static final int MAX_PAGES = 20;
    private static final int PAGE_SIZE = 200;

    private static final Pattern LINK_NEXT = Pattern.compile("<([^>]+)>\\s*;\\s*rel=\"?next\"?");
    private static final Pattern AUTH_PARAM = Pattern.compile("([A-Za-z_]+)=\"([^\"]*)\"");

    private final RegistryCredentialService credentials;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public RegistryCatalogService(RegistryCredentialService credentials, ObjectMapper mapper) {
        this.credentials = credentials;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /** True when a credential row exists — the page uses this to decide whether browsing is even offered. */
    public boolean isConfigured() {
        return credentials.find().isPresent();
    }

    /**
     * Lists repository names in the registry. Returns them sorted; {@code truncated} is true
     * when we stopped at {@link #MAX_PAGES} rather than reaching the end of the catalog.
     */
    public Catalog listRepositories() {
        RegistryCredentialEntity cred = requireCredential();
        String base = baseUrl(cred.getUrl());

        List<String> repositories = new ArrayList<>();
        String next = base + "/v2/_catalog?n=" + PAGE_SIZE;
        boolean truncated = false;

        for (int page = 0; ; page++) {
            if (page >= MAX_PAGES) {
                truncated = true;
                log.warn("Registry catalog for {} exceeded {} pages; showing the first {} repositories",
                    cred.getUrl(), MAX_PAGES, repositories.size());
                break;
            }
            HttpResponse<String> res = authedGet(URI.create(next), cred, "registry:catalog:*");
            JsonNode body = parse(res.body(), "catalog");
            JsonNode names = body.path("repositories");
            if (names.isArray()) {
                names.forEach(n -> repositories.add(n.asText()));
            }
            String link = res.headers().firstValue("link").orElse(null);
            String following = nextPageUrl(link, base);
            if (following == null) break;
            next = following;
        }

        repositories.sort(String::compareToIgnoreCase);
        return new Catalog(cred.getUrl(), List.copyOf(repositories), truncated);
    }

    /** Lists the tags of one repository. Registries return tags unordered, so sort them here. */
    public Tags listTags(String repository) {
        if (repository == null || repository.isBlank()) {
            throw new IllegalArgumentException("Repository name is required");
        }
        String repo = repository.trim();
        // Guard against a caller escaping the /v2/<name>/tags/list path.
        if (repo.startsWith("/") || repo.contains("..") || repo.contains("?") || repo.contains("#")) {
            throw new IllegalArgumentException("Invalid repository name: " + repository);
        }

        RegistryCredentialEntity cred = requireCredential();
        String base = baseUrl(cred.getUrl());
        URI uri = URI.create(base + "/v2/" + encodePath(repo) + "/tags/list?n=" + PAGE_SIZE);

        HttpResponse<String> res = authedGet(uri, cred, "repository:" + repo + ":pull");
        JsonNode body = parse(res.body(), "tag list");

        List<String> tags = new ArrayList<>();
        JsonNode node = body.path("tags");
        if (node.isArray()) {
            node.forEach(t -> tags.add(t.asText()));
        }
        tags.sort(RegistryCatalogService::compareNatural);
        return new Tags(repo, List.copyOf(tags));
    }

    // ---------------------------------------------------------------- internals

    private RegistryCredentialEntity requireCredential() {
        return credentials.find().orElseThrow(() -> new IllegalStateException(
            "No registry credentials are configured. Save a registry URL, username and "
            + "password before browsing images."));
    }

    /**
     * GETs a registry URL, sending Basic credentials up front and upgrading to a bearer
     * token if the registry answers with a Bearer challenge.
     */
    private HttpResponse<String> authedGet(URI uri, RegistryCredentialEntity cred, String scope) {
        String basic = "Basic " + Base64.getEncoder().encodeToString(
            (cred.getUsername() + ":" + cred.getPassword()).getBytes(StandardCharsets.UTF_8));

        HttpResponse<String> res = send(uri, basic);
        if (res.statusCode() == 401) {
            String challenge = res.headers().firstValue("www-authenticate").orElse("");
            if (challenge.toLowerCase().startsWith("bearer ")) {
                String token = fetchBearerToken(challenge, cred, scope);
                res = send(uri, "Bearer " + token);
            }
        }

        int code = res.statusCode();
        if (code == 401 || code == 403) {
            throw new RegistryUnavailableException(
                "The registry rejected the stored credentials (HTTP " + code + "). Check the "
                + "username and password on this page.");
        }
        if (code == 404) {
            throw new RegistryUnavailableException(
                "The registry has no catalog endpoint at " + uri.getHost() + " (HTTP 404). Some "
                + "registries — Docker Hub among them — do not expose /v2/_catalog.");
        }
        if (code / 100 != 2) {
            throw new RegistryUnavailableException(
                "The registry returned HTTP " + code + " for " + uri.getPath() + ".");
        }
        return res;
    }

    private HttpResponse<String> send(URI uri, String authorization) {
        HttpRequest req = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", authorization)
            .header("Accept", "application/json")
            .GET()
            .build();
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RegistryUnavailableException(
                "Could not reach the registry at " + uri.getScheme() + "://" + uri.getAuthority()
                + " — " + e.getMessage() + ". If it serves plain HTTP, prefix the registry URL "
                + "with http://", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RegistryUnavailableException("Interrupted while querying the registry", e);
        }
    }

    /** Exchanges Basic credentials for a scoped bearer token, per the token-auth spec. */
    private String fetchBearerToken(String challenge, RegistryCredentialEntity cred, String scope) {
        Map<String, String> params = new HashMap<>();
        Matcher m = AUTH_PARAM.matcher(challenge);
        while (m.find()) {
            params.put(m.group(1).toLowerCase(), m.group(2));
        }
        String realm = params.get("realm");
        if (realm == null || realm.isBlank()) {
            throw new RegistryUnavailableException(
                "The registry demanded bearer auth but did not say where to get a token.");
        }

        StringBuilder url = new StringBuilder(realm);
        url.append(realm.contains("?") ? '&' : '?');
        url.append("scope=").append(URLEncoder.encode(params.getOrDefault("scope", scope), StandardCharsets.UTF_8));
        if (params.containsKey("service")) {
            url.append("&service=").append(URLEncoder.encode(params.get("service"), StandardCharsets.UTF_8));
        }

        String basic = "Basic " + Base64.getEncoder().encodeToString(
            (cred.getUsername() + ":" + cred.getPassword()).getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> res = send(URI.create(url.toString()), basic);
        if (res.statusCode() / 100 != 2) {
            throw new RegistryUnavailableException(
                "The registry's token service rejected the stored credentials (HTTP "
                + res.statusCode() + ").");
        }

        JsonNode body = parse(res.body(), "token");
        String token = body.path("token").asText(null);
        if (token == null || token.isBlank()) {
            token = body.path("access_token").asText(null);
        }
        if (token == null || token.isBlank()) {
            throw new RegistryUnavailableException("The registry's token service returned no token.");
        }
        return token;
    }

    private JsonNode parse(String body, String what) {
        try {
            return mapper.readTree(body);
        } catch (IOException e) {
            throw new RegistryUnavailableException(
                "The registry returned a " + what + " response that isn't JSON — is that URL "
                + "really a Docker registry?", e);
        }
    }

    /**
     * Resolves the {@code Link: <...>; rel="next"} header to an absolute URL. Registries
     * emit a path-only value ({@code /v2/_catalog?n=200&last=foo}), so rebase it on the host.
     */
    static String nextPageUrl(String linkHeader, String base) {
        if (linkHeader == null || linkHeader.isBlank()) return null;
        Matcher m = LINK_NEXT.matcher(linkHeader);
        if (!m.find()) return null;
        String target = m.group(1).trim();
        if (target.startsWith("http://") || target.startsWith("https://")) return target;
        return base + (target.startsWith("/") ? target : "/" + target);
    }

    /**
     * Builds the API base from the stored registry URL. The stored value is normally a bare
     * host[:port] ({@code registry.example.com:5000}); assume HTTPS unless a scheme says
     * otherwise, so credentials are never silently downgraded to plaintext.
     */
    static String baseUrl(String storedUrl) {
        String s = storedUrl == null ? "" : storedUrl.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty()) {
            throw new IllegalStateException("The configured registry URL is empty.");
        }
        if (s.startsWith("http://") || s.startsWith("https://")) return s;
        return "https://" + s;
    }

    /**
     * Orders tags the way a person reads them: runs of digits compare numerically, so a
     * registry filling up with {@code cm-9, cm-11, cm-100} lists in that order rather than
     * the lexical {@code cm-100, cm-11, cm-9}. Non-digit runs compare case-insensitively.
     */
    static int compareNatural(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int startA = i;
                int startB = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                String numA = a.substring(startA, i).replaceFirst("^0+(?=.)", "");
                String numB = b.substring(startB, j).replaceFirst("^0+(?=.)", "");
                if (numA.length() != numB.length()) return numA.length() - numB.length();
                int byValue = numA.compareTo(numB);
                if (byValue != 0) return byValue;
            } else {
                int byChar = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
                if (byChar != 0) return byChar;
                i++;
                j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }

    /** Percent-encodes each path segment while leaving the {@code /} separators intact. */
    static String encodePath(String repository) {
        String[] segments = repository.split("/");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) out.append('/');
            out.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return out.toString();
    }

    public record Catalog(String registry, List<String> repositories, boolean truncated) {}

    public record Tags(String repository, List<String> tags) {}
}
