package com.nadirkhoulali.ucs.config;

import java.util.ArrayList;
import java.util.List;

public record UcsConfigValidationReport(List<String> errors, List<String> warnings) {
    public UcsConfigValidationReport {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
    }

    public boolean valid() {
        return errors.isEmpty();
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        void error(String message) {
            errors.add(message);
        }

        void warning(String message) {
            warnings.add(message);
        }

        UcsConfigValidationReport build() {
            return new UcsConfigValidationReport(errors, warnings);
        }
    }
}
