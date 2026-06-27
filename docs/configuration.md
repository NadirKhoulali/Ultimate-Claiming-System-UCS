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

## Claim Metadata

Player-edited names and descriptions are bounded by config:

- `claimMetadata.maxNameLength = 48`
- `claimMetadata.maxDescriptionLength = 240`

Names must be nonblank after trimming. Descriptions may be blank.

## Claim Teleport

`/claim home` uses server-side teleport policy:

- `claimTeleport.delaySeconds = 3`
- `claimTeleport.cancelOnMove = true`
- `claimTeleport.requireSafeLanding = true`

Safe landing requires the destination dimension to exist, the spawn chunk to still belong to an allowed claim, empty collision at the player's feet and head, a collision block below, and world-border containment.

## Roles And Flags

Default role ids are `owner`, `member`, `tenant`, `visitor`, and `banned`.

`roles.defaultTrustRoleId` defaults to `member`, `roles.bannedRoleId` defaults to `banned`, and `roles.requireInviteAcceptance` defaults to `false`. When invite acceptance is enabled, trust/role commands create pending invites until the target accepts.

UCS automatically includes the configured trust and banned role ids in the effective role list, which keeps older config files valid after role policy keys are added.

## Bans And Expulsion

Banned-player entry prevention is enabled by default:

- `bans.preventEntry = true`
- `bans.expulsionSearchRadiusBlocks = 48`
- `bans.expulsionCooldownTicks = 40`

UCS searches for a safe same-dimension location outside the claim before teleporting a banned or kicked player. If no safe location is found, the player is not moved and receives a failure message.

Default flag ids are namespaced with `ucs:`. Addons should also use namespaced ids.

`flags.defaultProtectionFlagIds` seeds new claims with enabled per-claim flags. It is not the full built-in registry; UCS also registers placeholders for later block, interaction, container, entity, item, combat, environment, redstone, mob, and movement protections.

Block build/break protection also has block-id policy lists:

- `protection.ignoredBlockIds` skips UCS block protection checks for matching block ids.
- `protection.allowedBlockIds` always allows matching blocks even when a claim flag is enabled.
- `protection.specialBlockIds` uses `ucs:special_block_use` instead of ordinary `ucs:block_break` for destruction checks. Defaults include high-value blocks such as beacons, conduits, dragon eggs, ender chests, respawn anchors, spawners, and vaults.
- `protection.containerTargetIds`, `doorTargetIds`, `buttonTargetIds`, `leverTargetIds`, and `redstoneTargetIds` classify right-click and boundary-redstone protection targets. Entries can be exact block ids such as `minecraft:barrel` or block tags such as `#minecraft:doors`.
- `protection.entityTargetIds` and `protection.vehicleTargetIds` classify entity interaction, damage, and mount/use protection targets. Entries can be exact entity type ids or entity type tags.

## Economy

Economy defaults are enabled when a compatible provider exists:

- `starterClaimPrice = 25.0`
- `pricePerExtraChunk = 5.0`
- `unclaimRefundRatio = 0.75`

UCS logs a warning on startup while these defaults are active so established servers review them before adding an economy provider.

The economy provider registry is exposed through the public API. UCS currently includes:

- `ucs:none`, a no-provider fallback that returns `PROVIDER_UNAVAILABLE`.
- `ubs`, a soft Ultimate Banking System adapter that activates only when the `ultimatebankingsystem` mod and its server API are available.

Claim pricing enforcement is built on top of this provider SPI in later economy slices. The SPI already supports balance validation, charge, refund, transfer, provider transaction references, and formatted amounts.

Claim creation and chunk editing now use the active provider when `enableWhenProviderExists` is true and a provider is available:

- New claims charge `starterClaimPrice + pricePerExtraChunk * (selectedChunks - 1)` before the claim is saved.
- Radius claims calculate the full square selection price before creating any claim state.
- Adding a chunk charges `pricePerExtraChunk` before saving the expanded claim.
- Removing or splitting out a chunk refunds `pricePerExtraChunk * unclaimRefundRatio` after the claim edit succeeds.
- Whole-claim refund calculation is available to future unclaim flows as `creationValue * unclaimRefundRatio`.
- Claim sale listings must be greater than zero and no higher than `maxClaimSalePrice`, which defaults to `1,000,000`.
- Tenant lease offers use the offered price and duration. Accepting or renewing a lease requires an active economy provider and transfers the price from tenant to claim owner. Lease offers, cancellation, eviction, and expiration do not use economy transactions.

Economy transaction references are stable strings such as `UCS_CLAIM_CREATE`, `UCS_CHUNK_ADD`, `UCS_CHUNK_REMOVE_REFUND`, and rollback references for save failures after payment.

Claim purchases use `UCS_CLAIM_SALE_PURCHASE`. If ownership transfer fails after payment, UCS attempts `UCS_CLAIM_SALE_ROLLBACK`.

Lease acceptance uses `UCS_LEASE_ACCEPT`; renewal uses `UCS_LEASE_RENEW`. If a claim save fails after tenant payment, UCS attempts `UCS_LEASE_ACCEPT_ROLLBACK` or `UCS_LEASE_RENEW_ROLLBACK`.

Economy admins can run `/ucs economy preview [limit]` to inspect active provider status, configured prices, marketplace counts, tax settings, and upcoming tax charges. Corrective commands include `/ucs economy refund <player> <amount> <reason>`, `/ucs economy retry tax <claimId>`, `/ucs economy cancel sale <claimId> <reason>`, and `/ucs economy cancel lease <leaseId> <reason>`. Each corrective command writes a persisted economy audit entry with a UCS reference and human reason.

Economy audit inspection is staff-only through `/ucs economy audit`, `/ucs economy audit claim <claimId>`, and `/ucs economy audit owner <ownerKey>`. Provider transaction references are included there for diagnostics and are not exposed through ordinary player command messages.

## Claim Tax

Recurring claim tax is configured separately from claim creation prices and is disabled by default:

- `claimTax.enabled = false`
- `claimTax.intervalHours = 168`
- `claimTax.initialDelayHours = 168`
- `claimTax.baseAmount = 0.0`
- `claimTax.perChunkAmount = 0.0`
- `claimTax.maxClaimsPerTick = 64`
- `claimTax.warningHoursBeforeDue = 24`

The tax formula is `baseAmount + perChunkAmount * claimChunkCount`. Billing charges player-owned claims through the active economy provider and records successful withdrawals in the UCS server sink ledger. No bank treasury account is required. Team/server-owned claims or provider failures create failed ledger entries and mark the claim tax state delinquent for the later nonpayment flow instead of deleting the claim.

Admins with `ucs.economy.admin` can run `/ucs tax preview [limit]` to inspect upcoming charges, due status, and recorded debt.

## Nonpayment

Nonpayment policy controls what happens after recurring billing fails:

- `nonpayment.graceHours = 72`
- `nonpayment.retryIntervalHours = 24`
- `nonpayment.warningIntervalHours = 24`
- `nonpayment.archiveAfterGrace = true`
- `nonpayment.requireDebtPaidBeforeRestore = false`
- `nonpayment.maxClaimsPerTick = 64`

Failed recurring payments keep the claim active during grace, record outstanding debt, and retry at `retryIntervalHours`. Online player owners receive warning messages no more often than `warningIntervalHours`. If `archiveAfterGrace` is enabled and debt remains after grace, UCS archives the claim through the normal archive lifecycle with actor `system:nonpayment`.

Admins with `ucs.economy.admin` can run `/ucs debt list [limit]` to inspect debt and `/ucs debt clear <claimId>` to clear recorded debt. When `requireDebtPaidBeforeRestore` is true, `/ucs archive restore` is blocked until the debt is cleared.

## Map Cache

Map terrain tiles are configured separately from claim SavedData. The default cache size is `1024 MiB`, with request/job limits to protect large servers.

The file cache stores `.ucstile` files outside SavedData using the versioned path documented in [ADR 0002](adr/0002-file-based-map-tile-cache.md). `maxTileAgeDays` and `maxSizeMiB` control pruning. Missing, stale-version, or corrupt tiles are safe to regenerate.

Terrain tile generation is bounded by the same map-cache job/request limits and samples only chunks that are already loaded by the server. Unknown areas are encoded into the tile payload instead of forcing worldgen.

## Audit And Purge

Audit logging is enabled by default. Archive retention defaults to `archive.retentionDays = 365`; old archive records are pruned when new archives are created. Inactive purge is disabled by default. If inactive purge is enabled, `archiveBeforeDelete` must remain `true`.

## Commands And Messages

`commands.permissionNodePrefix` defaults to `ucs`. `commands.opFallbackEnabled` defaults to `true` and controls player OP fallback for UCS server-authority permission nodes. `messages.defaultLocale` defaults to `en_us`.

See [UCS Permissions](permissions.md) for the registered NeoForge nodes and fallback behavior.

## Validation

UCS validates config values at startup and fails fast on cross-field errors. This avoids silently running with a corrupt or dangerous policy.
