# ShiinaCasino

Casino-Mod für **Minecraft 1.21.1 / NeoForge 21.1.252**. Aktuelle Version: **1.1.1**.

Blackjack, Texas Hold’em Poker, Roulette, aufrechtes Glücksrad und Chip-Wechselstation. Große gemeinsame Blackjack- und Roulette-Tische besitzen echte Sitzplätze. Dunkles Holz, grüner Filz und goldene Details prägen die Gestaltung.

Spiele, Einsätze, Ergebnisse, Sitzbelegung und Chip-Auszahlungen werden auf dem Server verwaltet; Radanimationen laufen auf dem Client.

## Installation

Die installierbare Datei heißt `shiinacasino-1.1.1.jar`. Sie gehört auf den Server und auf alle Clients in den jeweiligen `mods`-Ordner. Eine vorhandene ShiinaCasino-JAR ersetzen und nur eine Version gleichzeitig verwenden. Die `-sources.jar` dient der Entwicklung.

Version 1.1.1 behält vorhandene Block- und BlockEntity-IDs sowie gespeicherte Spiele und Chipguthaben bei. Welten müssen nicht zurückgesetzt werden. Details: [Reparaturbericht 1.1.1](REPARATUR-1.1.1.md).

## Bauen

Voraussetzung: ein JDK **21**. `JAVA_HOME` auf dieses JDK setzen.

Windows / PowerShell:

```powershell
.\gradlew.bat build
```

Linux / macOS:

```sh
./gradlew build
```

Die installierbare JAR entsteht unter `build/libs/shiinacasino-1.1.1.jar`. Gradle, Buildausgaben, lokale Entwicklungswelten und Logs werden nicht mit Git versioniert. Der Gradle Wrapper ist enthalten.

## Entwicklung und Tests

```powershell
.\gradlew.bat runClient
.\gradlew.bat runGameTestServer
.\gradlew.bat runClientRendererTest
```

`build` führt die JUnit-Tests aus. Der GameTest-Server prüft unter anderem Platzierung, Hindernisse, Ausrichtungen, Itemverbrauch, Abbau, Sitzplätze und Rollbacks und beendet sich anschließend.

`runClientRendererTest` startet einen echten Entwicklungsclient mit eigener Testwelt in `run-render-check`, prüft Glücksrad- und Roulette-Rendering sowie laufende Drehungen und beendet den Client nach erfolgreicher Prüfung. Testklassen werden nicht in die installierbare Mod-JAR aufgenommen.

Für 1.1.1 wurden 74 JUnit-Tests, 14 GameTests und ein echter Client-Renderlauf erfolgreich abgeschlossen. Die Kombination mit Sable, Veil und Flywheel sowie Mehrspielerbetrieb und echte Chunk-Entladezyklen im Ziel-Modpack stehen noch zur Prüfung aus.

Der Workflow [Build](.github/workflows/build.yml) baut Pushes und Pull Requests mit Java 21.

## Lizenz

[MIT](LICENSE). Informationen zu Assets, Werkzeugen und Abhängigkeiten stehen in [ASSET_LICENSES.md](ASSET_LICENSES.md). Die ursprüngliche Template-Lizenz bleibt in [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt) erhalten.
