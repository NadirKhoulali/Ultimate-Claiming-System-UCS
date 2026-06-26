# Testing UCS

UCS keeps the default validation loop fast and reserves heavier in-game validation for behavior that actually needs Minecraft runtime state.

## Quick Validation

Run this before opening a pull request:

```powershell
.\gradlew.bat --no-daemon build
```

This compiles the mod, generates NeoForge metadata, runs unit tests, and builds the jar.

## Unit Tests

Unit tests live in `src/test/java`. They should cover pure domain logic, config validation, value objects, pricing calculations, archive state machines, and other code that does not need a running Minecraft server.

## GameTest / Integration Tests

NeoForge run configurations are already defined for client, server, data generation, and GameTest server runs. Use GameTest for protection behavior, event behavior, command behavior, and persistence flows that need real Minecraft runtime objects.

The intended local command for future GameTest coverage is:

```powershell
.\gradlew.bat --no-daemon runGameTestServer
```

Do not add GameTest runs to the default CI workflow until UCS has focused GameTests and the runtime is stable enough to avoid slowing every pull request.
