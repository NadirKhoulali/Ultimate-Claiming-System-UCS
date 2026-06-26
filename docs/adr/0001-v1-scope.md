# ADR 0001: UCS v1 Scope

## Status

Accepted for initial implementation.

## Context

UCS is targeting NeoForge 1.21.1 first. The first public version needs a strong claiming foundation without carrying every possible future storage engine, loader, or claim geometry.

## Decision

UCS v1 uses full-height chunk claims keyed by dimension and chunk coordinates. The primary storage target is Minecraft SavedData for claim/domain data, with a separate file-backed terrain tile cache for the map system.

Ownership supports:

- Player-owned claims.
- API-only team-owned claims.
- Server/admin-owned claims.

UCS v1 explicitly defers:

- Vertical or cuboid claims.
- SQL storage engines.
- Force-loaded chunks.
- Cross-loader support.
- Concrete importers from other claim mods/plugins.

## Consequences

The initial domain model can stay compact and fast for large public servers. Future expansion remains possible through versioned data, storage interfaces, and API-only owner references.
