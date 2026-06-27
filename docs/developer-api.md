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

Use `UcsApi.protectionFlags()` to access the live protection flag registry while a server world is active. Addons may register custom `ProtectionFlagDefinition` instances during their server-side initialization before their own protection checks depend on them.

Use `UcsApi.economyProviders()` to access the provider registry:

```java
UcsApi.economyProviders().ifPresent(registry -> {
    var provider = registry.activeProvider();
    var payer = ClaimEconomyAccountRef.playerPrimary(playerUuid);
    var result = provider.validateCanCharge(payer, BigDecimal.valueOf(25));
});
```

The registry always has a fallback provider. `activeProvider()` returns the first available registered provider, or `ucs:none` when no compatible economy mod is available.

## Threading

All v1 API methods are server-thread only unless a future method explicitly documents async safety. Do not access Minecraft world objects from background threads through UCS.

Economy provider calls are also server-thread only by default. Providers may touch another mod's SavedData or service state, so addons should not call charge, refund, transfer, or balance methods from async workers unless the provider explicitly documents that operation as async-safe.

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
- `saleListing`, an optional persisted claim sale listing with listing id, seller player id/name, price, and listed timestamp

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

## Protection Flags

Typed protection flag API lives under `com.nadirkhoulali.ucs.api.protection`.

- `ProtectionFlagDefinition` records id, display name, category, default decision, allowed roles, and whether a player actor is required.
- `ProtectionFlagRegistry` exposes built-in flags and accepts addon registrations. Duplicate ids throw `DuplicateProtectionFlagException`.
- `UcsBuiltInProtectionFlags` provides stable constants and initial placeholder definitions for block, interaction, container, entity, item, combat, environment, redstone, mob, and movement categories.
- `ProtectionFlagEvaluator` returns `ProtectionDecision` values of `ALLOW`, `DENY`, or `ABSTAIN` with reason metadata such as `role_allowed`, `role_not_allowed`, `flag_disabled_for_claim`, and `unknown_flag`.

Claim `flagOverrides` are the persisted per-claim enabled flag set. Config `flags.defaultProtectionFlagIds` decides which flags new claims start with; saved claim flags remain readable even if a later config changes the defaults.

`ClaimProtectionService` is the first server-side protection service. Block break/place, right-click block interaction, neighbor notify, and piston pre-move NeoForge events route through it, then through `ProtectionFlagEvaluator`, then through `UcsProtectionDecisionEvent` before the event is cancelled. The service uses `UcsClaimService` lookups and `ClaimView` role data rather than reading the repository directly.

Interaction targets are classified by configurable exact block ids and `#tag` ids before a flag is evaluated. Containers, doors, buttons, levers, and redstone boundary sources intentionally use separate flags so addons and server configs can tune them independently.

Entity targets use the same exact-id/`#tag` approach for protected entities and vehicles. Player actors are taken directly from interact, attack, pickup, toss, and mount events; damage events also inspect the damage source and projectile owner when present. Natural actors pass through with no player actor, which lets enabled flags deny unsafe automation or mob-driven changes without loading chunks.

Natural protection uses NeoForge-native event surfaces: explosion detonation lists are filtered per affected block/entity, fluid placement and source conversion can be cancelled, mob spawn position and potential-spawn events can be denied, mob-griefing checks can be forced false, and PvP is handled through incoming damage actor resolution. No dedicated NeoForge server event for per-player weather display or vanilla fire spread was found in the 21.1.234 sources; UCS documents those as limitations and covers lava-created fire through fluid placement events where available.

Movement protection is split between cancellable events and server-tick state cleanup. `EntityTeleportEvent` checks the destination chunk with `ucs:teleport`; `EntityTravelToDimensionEvent` checks the source chunk with `ucs:portal_use`; the movement service enforces `ucs:entry`, grants and removes UCS-owned claim flight, stops denied elytra gliding, and damps high-speed airborne movement for `ucs:wind_charge` where NeoForge does not expose a dedicated server-side wind-charge event.

Admin bypass/debug state is held in `ProtectionAdminService`. `/ucs bypass` and `/ucs debug` are temporary per-player toggles guarded by `ucs.bypass` and `ucs.debug`; state is cleared on logout and server stop. Bypass allows decisions through `UcsProtectionDecisionEvent` with reason `admin_bypass`, and denial messages are throttled per player/action before optional debug details are sent.

## Economy Providers

The economy SPI lives under `com.nadirkhoulali.ucs.api.economy`.

- `ClaimEconomyProvider` exposes availability, balance lookup, charge validation, charge, refund, transfer, and formatting operations.
- `ClaimEconomyProviderRegistry` lets addons register providers and discover the active provider.
- `ClaimEconomyAccountRef` supports provider-neutral references for a player's primary account, a provider-native account id, and a server ledger.
- `ClaimEconomyResult` returns typed failure reasons, user-safe messages, the amount involved, the post-transaction balance when known, a provider transaction/reference id when available, and a provider-formatted amount.

UCS ships with two built-in providers:

- `ucs:none` is the fallback. It is never available and every operation fails with `PROVIDER_UNAVAILABLE`.
- `ubs` is a soft Ultimate Banking System adapter. It is available only when the `ultimatebankingsystem` mod is loaded, the UBS public API class can be resolved, and the UBS server API reports that its backing server state is available.

The UBS adapter does not compile against UBS. It reflectively calls `UltimateBankingApiProvider.get()` and then uses public `UltimateBankingApi` methods:

- Player charges use `withdrawFromPrimary`.
- Player refunds use `depositToPrimary`.
- Transfers use `transferFromPrimary`, `transferToPrimary`, or account-id `transfer` depending on the source and destination account refs.
- Balance validation resolves the primary account and calls `validateAccountCanSend` so failures preserve UBS account state such as missing, frozen, unavailable, or insufficient funds.

Server-ledger refs are treated as external sinks/sources for `charge`, `refund`, and `transfer` where possible. Direct server-ledger balances are unsupported until a provider exposes a durable server account concept.

Claim and chunk pricing uses `ClaimPricingService` internally. Payment failure on claim creation or chunk add returns `PAYMENT_FAILED` and leaves claim state unchanged. If a save fails after a successful charge, UCS attempts a rollback refund and includes that refund result in the command result. Remove/split refunds occur after the claim edit succeeds and are reflected in the returned audit detail with the provider transaction reference when one exists.

Claim sales are handled by `ClaimSaleService`. Owners can list and cancel sales; buyers purchase through provider transfer from buyer primary account to seller primary account. Listing ids are stable UUIDs and purchase requests may include the expected listing id to reject stale marketplace clicks. Purchase success transfers ownership to the buyer, clears the sale listing, assigns the owner role to the buyer, removes pending buyer invites, and emits an `ECONOMY_TRANSACTION` audit entry.

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
