# Ultimate Claiming System / UCS

Ultimate Claiming System / UCS is an open-source Minecraft claiming mod targeting NeoForge 1.21.1.

The first implementation slice establishes the mod scaffold, metadata, common/server/client entry points, a common config file, and a `/ucs version` smoke command. Claiming features will be implemented through the GitHub project board in dependency order.

## Development

Requirements:

- Java 21
- Git
- Gradle wrapper from this repository

Useful commands:

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat runServer
```

The mod id is `ucs`, and the base Java package is `com.nadirkhoulali.ucs`.

Project language and scope:

- [Domain glossary](docs/domain/glossary.md)
- [ADR 0001: UCS v1 Scope](docs/adr/0001-v1-scope.md)
- [Configuration guide](docs/configuration.md)
- [Storage design](docs/storage.md)

See [docs/development/testing.md](docs/development/testing.md) for the unit-test and GameTest validation strategy.

## License

UCS is licensed under the MIT License. See [LICENSE](LICENSE).
