package com.translator.translation;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class TranslationErrorHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(TranslationException.class)
    public ResponseEntity<ProblemDetail> translationFailure(TranslationException exception) {
        HttpStatus status = switch (exception.kind()) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case INVALID_OUTPUT -> HttpStatus.BAD_GATEWAY;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case BUSY -> HttpStatus.TOO_MANY_REQUESTS;
        };
        String detail = switch (exception.kind()) {
            case INVALID_INPUT -> "Provide nonblank text of at most 6000 characters and choose supported, different source and target languages.";
            case INVALID_OUTPUT -> "The model did not return both valid translations in the requested language.";
            case UNAVAILABLE -> "The translation model is unavailable. Please try again later.";
            case BUSY -> "The translator is busy. Please try again later.";
        };
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (exception.kind() == TranslationException.Kind.BUSY) {
            builder.header(HttpHeaders.RETRY_AFTER, "1");
        }
        return builder.body(ProblemDetail.forStatusAndDetail(status, detail));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = status.value() == 400
                ? "Invalid request. Check the JSON fields, text length, and language codes."
                : "The request could not be processed.";
        return super.handleExceptionInternal(exception, ProblemDetail.forStatusAndDetail(status, detail),
                headers, status, request);
    }
}