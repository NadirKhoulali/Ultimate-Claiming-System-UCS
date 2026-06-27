# UCS Permissions

UCS registers NeoForge permission nodes for server authority. These nodes are separate from claim roles such as owner, member, tenant, or visitor. Claim roles decide what a player can do inside a specific claim; NeoForge permission nodes decide who can use global administrative, bypass, map, economy, archive, and debug capabilities.

## Registered Nodes

The default `commands.permissionNodePrefix` is `ucs`, so the default nodes are:

- `ucs.admin` - administrative UCS commands.
- `ucs.bypass` - claim protection bypass.
- `ucs.map.view` - access to claim map overlays and map data.
- `ucs.economy.admin` - economy administration.
- `ucs.archive.restore` - restoring archived claims.
- `ucs.debug` - debugging and inspection tools.

Changing `commands.permissionNodePrefix` changes the registered node prefix on the next server restart. Use a lowercase simple key; the default is recommended unless a server has a naming conflict.

## OP Fallback

`commands.opFallbackEnabled` controls player OP fallback for non-public UCS permission nodes when NeoForge's active permission handler uses the node default. It defaults to `true`.

Fallback defaults:

- `ucs.map.view` is public by default.
- All admin, bypass, economy, archive, and debug nodes require OP level `2` when OP fallback is enabled.
- If OP fallback is disabled, those non-public nodes default to denied unless a permission handler grants them.
- Console and non-player command sources continue to use Minecraft command-source permission levels.

If a server installs a NeoForge permission handler, that handler's explicit result wins. For example, an OP player explicitly denied `ucs.bypass` by the handler is denied.

## Shared Service

Commands and future server-driven GUI handlers should call `UcsPermissionService` instead of checking OP status directly. The service returns a `UcsPermissionDecision` with the node name, allow/deny result, and decision source. Bypass/admin decisions are marked as audit candidates so later audit tooling can record the action that used the permission.

Use `/ucs permissions` to inspect the active handler, fallback setting, prefix, and nodes from inside the server.
