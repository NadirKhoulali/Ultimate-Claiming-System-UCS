# UCS Configuration

UCS uses a versioned common NeoForge config file. The schema version is stored as `schemaVersion`, and future releases will use `UcsConfigMigration` to plan migrations.

## Dimensions

Default claimable dimensions:

- `minecraft:overworld`
- `minecraft:the_nether`
- `minecraft:the_end`

Use `dimensions.disabledDimensions` to block temporary, resource, mining, or event dimensions. A dimension listed as both enabled and disabled is rejected by runtime validation.

## Claim Limits

The default limits are conservative starter values:

- `maxClaimsPerPlayer = 16`
- `maxChunksPerPlayer = 256`
- `maxChunksPerClaim = 128`
- `maxRadiusClaim = 5`
- `requireConnectedClaims = true`

The radius setting can select more chunks than a claim permits; UCS reports this as a warning because admins may intentionally keep radius commands below hard claim-size limits.

## Roles And Flags

Default role ids are `owner`, `member`, `tenant`, and `visitor`. Role behavior is implemented in later slices, but the ids are validated now so storage/API code can rely on stable lowercase keys.

Default flag ids are namespaced with `ucs:`. Addons should also use namespaced ids.

## Economy

Economy defaults are enabled when a compatible provider exists:

- `starterClaimPrice = 25.0`
- `pricePerExtraChunk = 5.0`
- `unclaimRefundRatio = 0.75`

UCS logs a warning on startup while these defaults are active so established servers review them before adding an economy provider.

## Map Cache

Map terrain tiles are configured separately from claim SavedData. The default cache size is `1024 MiB`, with request/job limits to protect large servers.

## Audit And Purge

Audit logging is enabled by default. Inactive purge is disabled by default. If inactive purge is enabled, `archiveBeforeDelete` must remain `true`.

## Commands And Messages

`commands.permissionNodePrefix` defaults to `ucs`. `commands.opFallbackEnabled` defaults to `true` and controls player OP fallback for UCS server-authority permission nodes. `messages.defaultLocale` defaults to `en_us`.

See [UCS Permissions](permissions.md) for the registered NeoForge nodes and fallback behavior.

## Validation

UCS validates config values at startup and fails fast on cross-field errors. This avoids silently running with a corrupt or dangerous policy.
