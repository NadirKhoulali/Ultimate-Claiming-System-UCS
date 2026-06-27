package com.nadirkhoulali.ucs.storage;

public class ClaimRepositoryException extends RuntimeException {
    public ClaimRepositoryException(String message) {
        super(message);
    }

    public ClaimRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
