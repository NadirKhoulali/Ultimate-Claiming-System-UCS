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

Archive records preserve the original claim snapshot plus archive id, reason, actor stable key, archive timestamp, and the UCS storage data version that created the archive. Restores validate that the archive data version is not newer than the running storage version and that the archived chunks are still free before the archive is removed.

Archive retention is configured separately from inactive purge. `archive.retentionDays` defaults to `365`; expired archive records are pruned when new archives are created.

## Claim Metadata

Saved claims persist display name, description, and an optional exact spawn position. The spawn stores dimension/chunk plus x/y/z/yaw/pitch; `spawnChunk` is still exposed as a derived compatibility accessor for chunk-level checks.

## Role Data

Saved claims persist active `roleAssignments` and pending `pendingRoleInvites` as role-id to player-UUID sets. Pending invites are intentionally separate from assignments so protection checks only use accepted memberships.

The configured banned role is stored in the same active role assignment map. Ban commands remove conflicting non-owner role grants before assigning the banned role.

## Spatial Index

`ClaimSpatialIndex` maps `ChunkKey` to `ClaimId` so protection checks can resolve a chunk without scanning every claim.

The index is rebuilt from persisted claims on load. Duplicate claimed chunks are treated as corrupted data; the colliding claim is skipped, an error is logged, and the SavedData is marked dirty so the next save rewrites clean data.

## Versioning

`UcsClaimsSavedData` stores a `storageVersion`. Current version is `1`. Newer data is loaded best-effort with a warning; future migrations should be added before changing the version.
