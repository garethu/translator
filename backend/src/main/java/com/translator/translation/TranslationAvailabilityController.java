package com.translator.translation;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@RestController
public class TranslationAvailabilityController {
    private final RestClient client;

    public TranslationAvailabilityController(@Qualifier("ollamaStatusClient") RestClient client) {
        this.client = client;
    }

    @GetMapping("/api/availability")
    public ResponseEntity<Void> availability() {
        try {
            client.get().uri("/api/version").retrieve().toBodilessEntity();
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).build();
        }
        catch (RestClientException exception) {
            return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).build();
        }
    }
}
