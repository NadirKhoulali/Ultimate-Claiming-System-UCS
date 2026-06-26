package com.nadirkhoulali.ucs.core.model;

import java.util.Objects;
import java.util.regex.Pattern;

final class IdentifierRules {
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern SIMPLE_KEY = Pattern.compile("[a-z][a-z0-9_-]{1,63}");

    private IdentifierRules() {
    }

    static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return normalized;
    }

    static String requireResourceId(String value, String fieldName) {
        String normalized = requireNonBlank(value, fieldName);
        if (!RESOURCE_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(fieldName + " must be a namespaced resource id");
        }
        return normalized;
    }

    static String requireSimpleKey(String value, String fieldName) {
        String normalized = requireNonBlank(value, fieldName);
        if (!SIMPLE_KEY.matcher(normalized).matches()) {
            throw new IllegalArgumentException(fieldName + " must be a lowercase simple key");
        }
        return normalized;
    }
}
