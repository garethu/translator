package com.translator.translation;

public class TranslationException extends RuntimeException {
    public enum Kind { INVALID_INPUT, INVALID_OUTPUT, UNAVAILABLE, BUSY }

    private final Kind kind;

    public TranslationException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}