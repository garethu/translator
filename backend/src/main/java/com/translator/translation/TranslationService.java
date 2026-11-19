package com.translator.translation;

import java.util.List;
import java.util.concurrent.Semaphore;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

@Service
public class TranslationService {
    private static final BeanOutputConverter<TranslationResponse> OUTPUT = new BeanOutputConverter<>(
        TranslationResponse.class, JsonMapper.builder()
            .withCoercionConfig(LogicalType.Textual, coercion -> coercion
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build());

    private final ChatClient chatClient;
    private final LanguageCatalog catalog;
    private final Semaphore permits;

    public TranslationService(ChatClient chatClient, LanguageCatalog catalog,
            @Value("${translator.max-concurrent:1}") int maxConcurrent) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("translator.max-concurrent must be positive");
        }
        this.chatClient = chatClient;
        this.catalog = catalog;
        this.permits = new Semaphore(maxConcurrent);
    }

    public TranslationResponse translate(TranslationRequest request) {
        catalog.validate(request);
        if (!permits.tryAcquire()) {
            throw new TranslationException(TranslationException.Kind.BUSY);
        }
        try {
            String source = catalog.label(request.sourceLanguage());
            String target = catalog.label(request.targetLanguage());
            String instruction = "Translate the source text from " + source + " into " + target
                    + " (" + request.targetLanguage() + "). Both formal and informal must be written in "
                    + target + ", not in different languages.";
            if ("ar".equals(request.targetLanguage())) {
                instruction += " Use Modern Standard Arabic in both fields, not a regional dialect.";
            }
            TranslationResponse response = chatClient.prompt()
                    .messages(List.of(new SystemMessage(instruction), new UserMessage(request.text())))
                    .call().entity(OUTPUT, options -> options.useProviderStructuredOutput());
            if (response == null || response.formal() == null || response.formal().isBlank()
                    || response.informal() == null || response.informal().isBlank()
                    || (!"ar".equals(request.targetLanguage())
                        && (isPredominantlyArabic(response.formal()) || isPredominantlyArabic(response.informal())))) {
                throw new TranslationException(TranslationException.Kind.INVALID_OUTPUT);
            }
            return response;
        }
        catch (TranslationException exception) {
            throw exception;
        }
        catch (JacksonException | IllegalArgumentException exception) {
            throw new TranslationException(TranslationException.Kind.INVALID_OUTPUT);
        }
        catch (RuntimeException exception) {
            throw new TranslationException(TranslationException.Kind.UNAVAILABLE);
        }
        finally {
            permits.release();
        }
    }

    private static boolean isPredominantlyArabic(String text) {
        long arabicLetters = text.codePoints().filter(Character::isLetter)
                .filter(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.ARABIC)
                .count();
        long totalLetters = text.codePoints().filter(Character::isLetter).count();
        return arabicLetters >= 4 && arabicLetters > totalLetters - arabicLetters;
    }
}