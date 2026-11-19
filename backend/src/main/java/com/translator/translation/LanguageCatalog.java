package com.translator.translation;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class LanguageCatalog {
    public record Language(String code, String label, boolean rtl) { }

    private static final List<Language> LANGUAGES = List.of(
            new Language("sq", "Albanian", false),
            new Language("ar", "Arabic", true),
            new Language("hy", "Armenian", false),
            new Language("az", "Azerbaijani", false),
            new Language("eu", "Basque", false),
            new Language("be", "Belarusian", false),
            new Language("bs", "Bosnian", false),
            new Language("bg", "Bulgarian", false),
            new Language("ca", "Catalan", false),
            new Language("hr", "Croatian", false),
            new Language("cs", "Czech", false),
            new Language("da", "Danish", false),
            new Language("nl", "Dutch", false),
            new Language("en", "English", false),
            new Language("et", "Estonian", false),
            new Language("fo", "Faroese", false),
            new Language("fi", "Finnish", false),
            new Language("fr", "French", false),
            new Language("ka", "Georgian", false),
            new Language("de", "German", false),
            new Language("el", "Greek", false),
            new Language("hu", "Hungarian", false),
            new Language("is", "Icelandic", false),
            new Language("ga", "Irish", false),
            new Language("it", "Italian", false),
            new Language("lv", "Latvian", false),
            new Language("lt", "Lithuanian", false),
            new Language("lb", "Luxembourgish", false),
            new Language("mk", "Macedonian", false),
            new Language("mt", "Maltese", false),
            new Language("no", "Norwegian", false),
            new Language("pl", "Polish", false),
            new Language("pt", "Portuguese", false),
            new Language("ro", "Romanian", false),
            new Language("ru", "Russian", false),
            new Language("gd", "Scottish Gaelic", false),
            new Language("sr", "Serbian", false),
            new Language("sk", "Slovak", false),
            new Language("sl", "Slovenian", false),
            new Language("es", "Spanish", false),
            new Language("sv", "Swedish", false),
            new Language("tr", "Turkish", false),
            new Language("uk", "Ukrainian", false),
            new Language("cy", "Welsh", false));

    private static final Map<String, Language> BY_CODE = LANGUAGES.stream()
            .collect(Collectors.toUnmodifiableMap(Language::code, Function.identity()));

    public List<Language> languages() {
        return LANGUAGES;
    }

    public String label(String code) {
        Language language = code == null ? null : BY_CODE.get(code);
        if (language == null) {
            throw new TranslationException(TranslationException.Kind.INVALID_INPUT);
        }
        return language.label();
    }

    public void validate(TranslationRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()
                || request.text().length() > 6000) {
            throw new TranslationException(TranslationException.Kind.INVALID_INPUT);
        }
        label(request.targetLanguage());
        label(request.sourceLanguage());
        if (request.sourceLanguage().equals(request.targetLanguage())) {
            throw new TranslationException(TranslationException.Kind.INVALID_INPUT);
        }
    }
}