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

## Marketplace Data

Saved claims persist optional sale listings and tenant lease contracts directly inside the claim snapshot. Lease contracts store tenant owner reference, configured role id, price, duration, offer/start/expiry timestamps, status, and whether UCS granted the role. Missing lease data decodes as an empty lease map for older worlds.

Claim tax state and server sink ledger entries are stored as top-level SavedData collections. Tax state records the next due timestamp, last paid timestamp, missed payment count, outstanding debt, delinquency timestamp, and update timestamp. Ledger entries record each paid or failed billing attempt with amount, due timestamp, processed timestamp, owner stable key, stable economy reference, provider reference, status, and detail.

Nonpayment archiving preserves tax state after the active claim is archived, so admins can inspect debt and restore policy can decide whether unpaid debt blocks recovery. Clearing debt resets missed payments, delinquency, warning timestamp, and outstanding debt while scheduling the next regular tax due date.

Economy audit entries are stored as a top-level SavedData collection. They capture staff corrective actions with actor key, action/status, optional claim id, owner key, amount, UCS reference, provider id, provider reference, reason, and diagnostic detail. These entries are intended for admin commands and future admin-console screens, not ordinary player messages.

## Spatial Index

`ClaimSpatialIndex` maps `ChunkKey` to `ClaimId` so protection checks can resolve a chunk without scanning every claim.

The index is rebuilt from persisted claims on load. Duplicate claimed chunks are treated as corrupted data; the colliding claim is skipped, an error is logged, and the SavedData is marked dirty so the next save rewrites clean data.

## Map Tile Cache

Terrain map tiles are stored outside SavedData by `FileMapTileCache`. The cache root contains versioned `.ucstile` files keyed by dimension, zoom, tileX, and tileZ. Each file carries its own key metadata so mismatched or corrupt tiles fail gracefully and can be regenerated. Cache pruning deletes old files first, then oldest remaining files until the configured size cap is met.

Terrain generation writes cache payloads only after server-thread sampling has copied loaded chunk data into immutable snapshots. UCS uses `getChunkNow` for the built-in sampler, so map browsing cannot create new chunks. Unloaded or unavailable chunks render as the unknown color in the cached payload.

Tile streaming reads from the file cache first and generates on cache misses. Missing dimensions, failed generation, cancelled requests, and rate-limited tiles produce explicit placeholder/control responses rather than blocking the client.

## Versioning

`UcsClaimsSavedData` stores a `storageVersion`. Current version is `1`. Newer data is loaded best-effort with a warning; future migrations should be added before changing the version.
