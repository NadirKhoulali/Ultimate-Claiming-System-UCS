# UCS Domain Glossary

## Claim

A protected land record owned by a player, API-only team, or the server. In v1, a claim is made from one or more full-height chunks in one dimension.

## Claim Chunk

One full-height Minecraft chunk belonging to a claim. UCS v1 does not support vertical slices or cuboid subregions.

## Claim Spawn

An optional exact position inside a claim used by `/claim home`. A spawn records dimension/chunk plus x/y/z/yaw/pitch and is cleared automatically if chunk edits remove its chunk.

## Owner Reference

A stable reference to the authority that owns a claim. Owner references can point at a player UUID, an API-only team id, or a server/admin namespace.

Team owner references are valid in storage and API in v1, but built-in team UI and team-provider integration are deferred. Player-owned claims are the default command and GUI path.

## Role

A named claim-level permission group, such as owner, member, tenant, or visitor. Roles are configurable in later implementation slices, and v1 does not use inheritance.

## Flag

A typed protection rule key, such as `ucs:block_break` or `ucs:container_open`. Addons should use namespaced flag ids.

## Lease

A tenant contract that grants a tenant role on a claim for a time window. Lease is separate from tax: rent is paid by a tenant, while tax/upkeep is paid by an owner to the server sink.

## Archive

A preserved inactive or removed claim snapshot. Archive is used before destructive cleanup, nonpayment removal, and admin restore flows.

## Tax

A recurring owner-side upkeep or server surcharge. Tax money goes to the configured server sink ledger.

## Audit Entry

A durable record of an admin, economy, or claim-management action. Audit entries are used for debugging, abuse review, and admin console history.

## Map Tile

A cached terrain image chunk used by the UCS world map. Map tiles are stored outside claim SavedData and must never trigger new world generation.

## Out Of Scope For v1

Vertical/cuboid claims, SQL storage engines, force-loaded chunks, cross-loader support, and concrete migration importers are deferred by ADR 0001.
