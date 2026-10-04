# ShiinaCasino

Casino-Mod für **Minecraft 1.21.1 / NeoForge 21.1.252 / Java 21**. Aktuelle Version: **1.2.0**.

Blackjack, Texas Hold’em Poker, Roulette, aufrechtes Glücksrad und Chip-Wechselstation. Große gemeinsame Blackjack- und Roulette-Tische besitzen echte Sitzplätze. Dunkles Holz, grüner Filz und goldene Details prägen die Gestaltung.

Spiele, Einsätze, Ergebnisse, Sitzbelegung und Chip-Auszahlungen werden auf dem Server verwaltet; Radanimationen laufen auf dem Client.

## Installation

Die installierbare Datei heißt `shiinacasino-1.2.0.jar`. Sie gehört auf den Server und auf alle Clients in den jeweiligen `mods`-Ordner. Eine vorhandene ShiinaCasino-JAR ersetzen und nur eine Version gleichzeitig verwenden. Die `-sources.jar` dient der Entwicklung.

Version 1.2.0 behält vorhandene Registry-IDs, Weltdaten und Chipguthaben bei. Welten müssen nicht zurückgesetzt werden. Alle Clients benötigen ebenfalls 1.2.0, da das Menüprotokoll erweitert wurde. Details und manuelle Testliste: [Update 1.2.0](UPDATE-1.2.0.md).

Menüs bieten persönliche Einsatz-/Auszahlungs-/Nettoanzeigen, letzte Einsätze bzw. atomare Roulette-Wiederholung, kurze Spielhilfe, zehn eigene Runden im Sitzungsverlauf und anpassbare Casino-Effekte. „Ton“ öffnet die Client-Einstellungen; `config/shiinacasino-client.toml` speichert Sounds an/aus, Lautstärke (Standard 50 %) und reduzierte Animationen.

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

Die installierbare JAR entsteht unter `build/libs/shiinacasino-1.2.0.jar`. Gradle, Buildausgaben, lokale Entwicklungswelten und Logs werden nicht mit Git versioniert. Der Gradle Wrapper ist enthalten.

## Entwicklung und Tests

```powershell
.\gradlew.bat runClient
.\gradlew.bat runGameTestServer
.\gradlew.bat runClientRendererTest
.\gradlew.bat runClientMenuTest
python tools/VerifyRelease.py
```

`build` führt die JUnit-Tests aus. Der GameTest-Server prüft unter anderem Platzierung, Hindernisse, Ausrichtungen, Itemverbrauch, Abbau, Sitzplätze und Rollbacks und beendet sich anschließend.

`runClientRendererTest` startet einen echten Entwicklungsclient mit eigener Testwelt in `run-render-check`, prüft Glücksrad- und Roulette-Rendering sowie laufende Drehungen und beendet den Client nach erfolgreicher Prüfung. Testklassen werden nicht in die installierbare Mod-JAR aufgenommen.

`runClientMenuTest` prüft 100 Menüansichten bei GUI-Skalierung 1-4, tatsächliche Minecraft-Glyphen beider Sprachdateien, Buttontexte, Grenzen, Überlappungen und lokale Unteransichten in einer eigenen Entwicklungswelt unter `run-menu-check`. Anschließend spielt es eine echte Glücksrad-Runde über die Netzwerkpakete mit dem integrierten Server und prüft Auszahlung, Verlauf und erneutes Öffnen. Screenshots und Prüfbericht bleiben in diesem Testverzeichnis. Es ersetzt keinen Test mit zwei echten Clients.

Für 1.2.0 wurden 91 JUnit-Tests, 15 GameTests, die genannten Menüprüfungen mit einer echten Serverrunde und der Welt-Renderer-Test erfolgreich abgeschlossen. Die Kombination mit Sable, Veil und Flywheel sowie zwei echte Clients und vollständige Chunk-Entlade-/Neuladezyklen im Ziel-Modpack bleiben manuell zu prüfen. `VerifyRelease.py` prüft die tatsächliche JAR, UTF-8-Ressourcen, Java 21 und den Ausschluss der Entwicklungstests.

Der Workflow [Build](.github/workflows/build.yml) baut Pushes und Pull Requests mit Java 21.

## Lizenz

[MIT](LICENSE). Informationen zu Assets, Werkzeugen und Abhängigkeiten stehen in [ASSET_LICENSES.md](ASSET_LICENSES.md). Die ursprüngliche Template-Lizenz bleibt in [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt) erhalten.
