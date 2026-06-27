package com.nadirkhoulali.ucs.map;

public record MapTileCachePruneResult(
        int scannedFiles,
        int deletedFiles,
        long bytesBefore,
        long bytesDeleted
) {
    public MapTileCachePruneResult {
        if (scannedFiles < 0 || deletedFiles < 0 || bytesBefore < 0 || bytesDeleted < 0) {
            throw new IllegalArgumentException("prune result values must be nonnegative");
        }
        if (deletedFiles > scannedFiles) {
            throw new IllegalArgumentException("deletedFiles cannot exceed scannedFiles");
        }
    }
}
