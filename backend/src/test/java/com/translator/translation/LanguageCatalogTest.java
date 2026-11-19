package com.translator.translation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LanguageCatalogTest {
    private final LanguageCatalog catalog = new LanguageCatalog();

    @Test
    void includesRequestedLanguagesWithUniqueIsoCodesAndArabicDirection() {
        assertThat(catalog.languages()).hasSize(44);
        assertThat(catalog.languages()).extracting(LanguageCatalog.Language::code)
                .doesNotHaveDuplicates()
                .contains("bg", "hr", "cs", "da", "nl", "en", "et", "fi", "fr", "de", "el", "hu",
                        "ga", "it", "lv", "lt", "mt", "pl", "pt", "ro", "sk", "sl", "es", "sv",
                        "sq", "hy", "az", "eu", "be", "bs", "ca", "fo", "ka", "is", "lb", "mk",
                        "no", "ru", "sr", "tr", "uk", "cy", "gd", "ar");
        assertThat(catalog.languages().stream().filter(LanguageCatalog.Language::rtl).toList())
                .containsExactly(new LanguageCatalog.Language("ar", "Arabic", true));
    }

    @Test
    void acceptsExplicitSourceAndExactTextLimit() {
        catalog.validate(new TranslationRequest("x".repeat(6000), "en", "ar"));
    }

    @Test
    void rejectsInvalidRequests() {
        for (TranslationRequest request : new TranslationRequest[] {
                new TranslationRequest(" ", "en", "nl"),
                new TranslationRequest(null, "en", "nl"),
                new TranslationRequest("x".repeat(6001), "en", "nl"),
                new TranslationRequest("text", "en", "en"),
                new TranslationRequest("text", "unknown", "nl"),
                new TranslationRequest("text", "auto", "nl"),
                new TranslationRequest("text", "", "nl"),
                new TranslationRequest("text", "en", ""),
                new TranslationRequest("text", "en", "auto"),
                new TranslationRequest("text", null, "nl"),
                new TranslationRequest("text", "en", null) }) {
            assertThatThrownBy(() -> catalog.validate(request))
                    .isInstanceOf(TranslationException.class)
                    .extracting("kind").isEqualTo(TranslationException.Kind.INVALID_INPUT);
        }
    }
}