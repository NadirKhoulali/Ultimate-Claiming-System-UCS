# ADR 0002: File-Based Map Tile Cache

## Status

Accepted for v1 foundation.

## Context

UCS needs terrain map tiles for full world-map rendering, but claim/domain SavedData must stay small and server-thread friendly. Tile bytes can be regenerated, so they should not be stored with authoritative claim data.

## Decision

UCS stores terrain tiles in a separate file cache rooted under a server-provided map-cache directory. The v1 layout is:

`v<formatVersion>/<dimension namespace>/<dimension path>/z<zoom>/s_<xShard>_<zShard>/<tileX>_<tileZ>.ucstile`

The current tile format version is `1`. `MapTileKey` includes dimension id, zoom, tileX, and tileZ. Dimension ids are split into safe path segments, and tile coordinates are sharded in groups of 256 to avoid huge directories on Windows and Linux filesystems.

Each tile file uses a small binary envelope:

- magic `UCSM`
- format version
- dimension
- zoom
- tileX
- tileZ
- payload length
- payload bytes

The cache API returns explicit read statuses: `HIT`, `MISS`, `STALE_VERSION`, or `CORRUPT`. Missing, stale, or corrupt files are treated as regenerable misses by future tile generation code. Corrupt and stale files at the expected path are deleted during read.

Pruning scans `.ucstile` files under the root, deletes files older than `mapCache.maxTileAgeDays`, then deletes oldest remaining files until total size is within `mapCache.maxSizeMiB`.

## Consequences

- Claim SavedData stays authoritative and does not carry image bytes.
- Format changes can move to a new `vN` path without breaking current readers.
- Old format files are naturally ignored by new readers and are removed by pruning or explicit invalidation.
- Future generator and streaming systems can use the same cache without loading chunks or touching SavedData.
