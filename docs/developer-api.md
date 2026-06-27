# UCS Developer API

UCS exposes a small public API under `com.nadirkhoulali.ucs.api`.

## Access

Use `UcsApi.claimService()` to access claim data:

```java
UcsApi.claimService().ifPresent(service -> {
    service.findClaim(chunkKey).ifPresent(claim -> {
        // Read immutable claim view data.
    });
});
```

The service is only present while a server world is active.

## Threading

All v1 API methods are server-thread only unless a future method explicitly documents async safety. Do not access Minecraft world objects from background threads through UCS.

## Read Models

`ClaimView`, `ClaimArchiveView`, and `OwnerView` are immutable read models. They do not expose repository internals or mutable collections.

`ClaimView` includes player-facing metadata:

- `displayName`
- `description`
- `spawn`, an exact claim spawn position when one is set
- `spawnChunk`, a compatibility convenience derived from `spawn`

## Ownership

Claims are owned by an `OwnerRef`:

- `PlayerOwner` is the v1 command and default UI path.
- `TeamOwner` is API-only in v1. Addons may create, read, and update team-owned claims through `UcsClaimService`, but UCS does not provide built-in team creation or management screens yet.
- `ServerOwner` represents server/admin claims.

Use `OwnerRef.stableKey()` or `ClaimOwnership` helpers for comparisons. Do not assume every claim owner is a player UUID.

## Claim Service

`UcsClaimService` supports:

- Listing active claims.
- Lookup by claim id.
- Lookup by dimension/chunk through the spatial index.
- Saving immutable domain claims.
- Deleting claims.
- Archiving claims.
- Restoring archives.

Mutation methods emit NeoForge events after repository commit.

Built-in player commands update claim metadata through the same service path, so name, description, and spawn edits emit `UcsClaimEvent.Updated` after repository commit.

## Events

Claim events live under `com.nadirkhoulali.ucs.api.event`:

- `UcsClaimEvent.Created`
- `UcsClaimEvent.Updated`
- `UcsClaimEvent.Deleted`
- `UcsClaimEvent.Archived`
- `UcsClaimEvent.Restored`
- `UcsProtectionDecisionEvent`

`UcsProtectionDecisionEvent` is cancellable. Calling `deny(reason)` cancels and denies the decision; calling `allow(reason)` clears cancellation and allows it.

## Versioning

UCS uses semantic versioning for the public API:

- Patch versions may fix bugs without changing signatures.
- Minor versions may add methods, event subclasses, or read-model fields.
- Major versions may remove or rename public API.

Packages containing `.internal.` are not public API.
