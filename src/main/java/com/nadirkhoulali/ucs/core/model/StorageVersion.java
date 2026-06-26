package com.nadirkhoulali.ucs.core.model;

public record StorageVersion(int value) {
    public StorageVersion {
        if (value < 1) {
            throw new IllegalArgumentException("value must be positive");
        }
    }
}
