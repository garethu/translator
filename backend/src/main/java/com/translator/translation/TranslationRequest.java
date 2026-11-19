package com.translator.translation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TranslationRequest(
        @NotBlank @Size(max = 6000) String text,
        @NotBlank String sourceLanguage,
        @NotBlank String targetLanguage) { }