package io.monohull.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the pure URL/header handling in {@link RegistryCatalogService}. The HTTP
 * conversation itself needs a live registry, so it is not exercised here.
 */
class RegistryCatalogServiceTest {

    // ---- baseUrl: never silently downgrade credentials to plaintext -------------

    @Test
    void bareHostAssumesHttps() {
        assertThat(RegistryCatalogService.baseUrl("registry.example.com")).isEqualTo("https://registry.example.com");
    }

    @Test
    void bareHostWithPortAssumesHttps() {
        assertThat(RegistryCatalogService.baseUrl("registry.example.com:5000"))
            .isEqualTo("https://registry.example.com:5000");
    }

    @Test
    void explicitPlainHttpIsHonoured() {
        assertThat(RegistryCatalogService.baseUrl("http://registry.internal:5000"))
            .isEqualTo("http://registry.internal:5000");
    }

    @Test
    void trailingSlashesAreTrimmed() {
        assertThat(RegistryCatalogService.baseUrl("https://registry.example.com///"))
            .isEqualTo("https://registry.example.com");
    }

    @Test
    void emptyUrlIsRejected() {
        assertThatThrownBy(() -> RegistryCatalogService.baseUrl("   "))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("empty");
    }

    // ---- catalog pagination ----------------------------------------------------

    @Test
    void relativeNextLinkIsRebasedOnTheRegistryHost() {
        String link = "</v2/_catalog?n=200&last=made%2Fapp>; rel=\"next\"";
        assertThat(RegistryCatalogService.nextPageUrl(link, "https://registry.example.com"))
            .isEqualTo("https://registry.example.com/v2/_catalog?n=200&last=made%2Fapp");
    }

    @Test
    void absoluteNextLinkIsUsedAsIs() {
        String link = "<https://registry.example.com/v2/_catalog?last=zzz>; rel=\"next\"";
        assertThat(RegistryCatalogService.nextPageUrl(link, "https://registry.example.com"))
            .isEqualTo("https://registry.example.com/v2/_catalog?last=zzz");
    }

    @Test
    void unquotedRelNextIsAccepted() {
        assertThat(RegistryCatalogService.nextPageUrl("</v2/_catalog?last=a>; rel=next", "https://r.example.com"))
            .isEqualTo("https://r.example.com/v2/_catalog?last=a");
    }

    @Test
    void lastPageHasNoNextLink() {
        assertThat(RegistryCatalogService.nextPageUrl(null, "https://r.example.com")).isNull();
        assertThat(RegistryCatalogService.nextPageUrl("", "https://r.example.com")).isNull();
        assertThat(RegistryCatalogService.nextPageUrl("</v2/_catalog>; rel=\"prev\"", "https://r.example.com")).isNull();
    }

    // ---- tag ordering ----------------------------------------------------------

    @Test
    void tagsWithDigitRunsSortNumericallyNotLexically() {
        List<String> tags = new ArrayList<>(List.of("cm-100", "cm-9", "cm-11", "cm-42"));
        tags.sort(RegistryCatalogService::compareNatural);
        assertThat(tags).containsExactly("cm-9", "cm-11", "cm-42", "cm-100");
    }

    /** The real made/app repo mixes a bare number and "latest" in with the cm-N tags. */
    @Test
    void mixedNumericAndWordTagsAreOrdered() {
        List<String> tags = new ArrayList<>(List.of("latest", "cm-13", "36", "cm-9"));
        tags.sort(RegistryCatalogService::compareNatural);
        assertThat(tags).containsExactly("36", "cm-9", "cm-13", "latest");
    }

    @Test
    void leadingZeroesDoNotChangeOrder() {
        List<String> tags = new ArrayList<>(List.of("v010", "v9", "v0002"));
        tags.sort(RegistryCatalogService::compareNatural);
        assertThat(tags).containsExactly("v0002", "v9", "v010");
    }

    @Test
    void identicalTagsCompareEqual() {
        assertThat(RegistryCatalogService.compareNatural("cm-42", "cm-42")).isZero();
    }

    @Test
    void prefixIsShorterThanWhatExtendsIt() {
        assertThat(RegistryCatalogService.compareNatural("cm", "cm-1")).isNegative();
    }

    // ---- repository path encoding ----------------------------------------------

    @Test
    void nestedRepositoryKeepsItsSlashes() {
        assertThat(RegistryCatalogService.encodePath("made/app")).isEqualTo("made/app");
    }

    @Test
    void spacesEncodeAsPercentTwentyNotPlus() {
        assertThat(RegistryCatalogService.encodePath("odd name/app")).isEqualTo("odd%20name/app");
    }
}
