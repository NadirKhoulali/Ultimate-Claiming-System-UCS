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

`ClaimArchiveView` includes the archived claim snapshot, archive timestamp, reason, actor stable key, and storage data version. Addons should display archive ids to admins because restore APIs use `ArchiveId`.

`ClaimView` includes player-facing metadata:

- `displayName`
- `description`
- `spawn`, an exact claim spawn position when one is set
- `spawnChunk`, a compatibility convenience derived from `spawn`
- `roleAssignments`, active claim-level role memberships
- `pendingRoleInvites`, invite-only memberships that are not active yet

## Ownership

Claims are owned by an `OwnerRef`:

- `PlayerOwner` is the v1 command and default UI path.
- `TeamOwner` is API-only in v1. Addons may create, read, and update team-owned claims through `UcsClaimService`, but UCS does not provide built-in team creation or management screens yet.
- `ServerOwner` represents server/admin claims.

Use `OwnerRef.stableKey()` or `ClaimOwnership` helpers for comparisons. Do not assume every claim owner is a player UUID.

## Claim Service

`UcsClaimService` supports:

- Listing active claims.
- Listing archived claims.
- Lookup by claim id.
- Lookup by dimension/chunk through the spatial index.
- Lookup by archive id.
- Saving immutable domain claims.
- Deleting claims.
- Archiving claims.
- Restoring archives.

Mutation methods emit NeoForge events after repository commit.

Archive restore validates that archived chunks are unclaimed, archived dimensions remain claimable, the original claim id is not active, the owner reference is still valid, and the archive data version is not newer than UCS can read. Invalid restores throw `ClaimRepositoryException` and leave the archive intact.

Built-in player commands update claim metadata through the same service path, so name, description, and spawn edits emit `UcsClaimEvent.Updated` after repository commit.

## Roles

Built-in player commands can trust, untrust, assign configured roles, and accept or decline pending invites. Active assignments and pending invites are persisted on the claim and exposed through `ClaimView`.

`ClaimRoleResolver.effectiveRoles(...)` provides the first server-side role resolution helper for later protection, GUI, and marketplace systems. Banned roles take precedence over all other role assignments.

## Bans And Expulsion

The built-in banned role is the first hard-denial role. `/claim ban` assigns it and removes conflicting non-owner role grants; `/claim unban` clears it. `/claim kick` and automatic banned-entry prevention use `ClaimExpulsionService` to search for a safe same-dimension destination outside the claim.

Expulsion decisions post `UcsProtectionDecisionEvent` with `ucs:entry` or `ucs:expel`, allowing addons to observe or override the movement decision before UCS teleports the player.

## Archive Admin Commands

`/ucs archive list` shows recent archived claims, and `/ucs archive restore <archiveId>` restores an archive after validation. Both require the `ucs.archive.restore` NeoForge permission node.

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
