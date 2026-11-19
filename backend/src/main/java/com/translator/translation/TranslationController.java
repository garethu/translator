package com.translator.translation;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TranslationController {
    private final TranslationService service;
    private final LanguageCatalog catalog;

    public TranslationController(TranslationService service, LanguageCatalog catalog) {
        this.service = service;
        this.catalog = catalog;
    }

    @GetMapping("/api/languages")
    public List<LanguageCatalog.Language> languages() {
        return catalog.languages();
    }

    @PostMapping("/api/translations")
    public TranslationResponse translate(@Valid @RequestBody TranslationRequest request) {
        return service.translate(request);
    }
}