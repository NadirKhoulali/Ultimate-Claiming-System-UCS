# UCS Storage

UCS v1 stores claim/domain data in Minecraft `SavedData` under the overworld data storage using the data name `ucs_claims`.

## Boundaries

- Claim data is persisted in `SavedData`.
- Terrain map tiles are not stored in `SavedData`; they will use a separate file tile cache.
- SQL storage engines are out of scope for v1, but services depend on the `ClaimRepository` interface rather than the concrete SavedData adapter.

## Repository

`ClaimRepository` exposes:

- Active claim lookup by id.
- Active claim lookup by dimension/chunk.
- Save/update.
- Delete.
- Archive.
- Restore.

## Claim Metadata

Saved claims persist display name, description, and an optional exact spawn position. The spawn stores dimension/chunk plus x/y/z/yaw/pitch; `spawnChunk` is still exposed as a derived compatibility accessor for chunk-level checks.

## Spatial Index

`ClaimSpatialIndex` maps `ChunkKey` to `ClaimId` so protection checks can resolve a chunk without scanning every claim.

The index is rebuilt from persisted claims on load. Duplicate claimed chunks are treated as corrupted data; the colliding claim is skipped, an error is logged, and the SavedData is marked dirty so the next save rewrites clean data.

## Versioning

`UcsClaimsSavedData` stores a `storageVersion`. Current version is `1`. Newer data is loaded best-effort with a warning; future migrations should be added before changing the version.
