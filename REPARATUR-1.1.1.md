# ShiinaCasino 1.1.1: Reparaturbericht

Installierbare Datei: `build/libs/shiinacasino-1.1.1.jar`

Minecraft 1.21.1, NeoForge 21.1.252; bestehendes Projekt weiterbearbeitet.

JAR: 224.744 Bytes, Java-Classfile-Version 65 (Java 21). SHA-256: `c0f08597f57c984c6ace0bf03e3f7abc3f960df87bf17024deafd8e4a2c74139`.

## Renderfehler

In `WorldWheelRenderer.render` wurde der von `buffers.getBuffer(RenderType.entitySolid(GOLD))` gelieferte Consumer für die komplette Sektorenschleife gehalten. Innerhalb derselben Schleife rief der Renderer `Font.drawInBatch` auf und schrieb danach weitere Dreiecke und den Rand in den alten Consumer. `board` machte denselben Fehler zwischen den einzelnen Roulettefeldern.

Der lokale Minecraft-1.21.1-Quellcode von `MultiBufferSource.BufferSource.getBuffer` zeigt die Ursache: Ein neuer RenderType ohne eigenen festen Puffer beendet `lastSharedType` mit `endBatch`. Dadurch wird der zuvor gelieferte BufferBuilder gebaut und beendet. Seine nächste `addVertex`-Operation wirft `IllegalStateException: Not building!`. Dafür braucht es keinen Fehler von Sable, Veil oder Flywheel. Consumer wurden nicht als Rendererfelder oder zwischen Frames gespeichert; die fehlerhafte Lebensdauer lag innerhalb eines Renderaufrufs.

Der Renderer schreibt jetzt die gesamte Disk-, Zeiger-, Rand- und Nabengeometrie vor der Beschriftung. Auch das Roulettefeld wird vollständig gezeichnet, bevor Schrift ausgegeben wird. Nach dem separaten Roulettefeld holt die Disk ihren Consumer erneut aus dem übergebenen MultiBufferSource. Kein Geometrie-Consumer wird nach einem Font-, Modell- oder weiteren getBuffer-Aufruf wiederverwendet. Der Renderer besitzt keine eigenen BufferBuilder und ruft kein endBatch/build auf Dispatcher-Puffern auf. Die Exception wird nicht abgefangen.

`entitySolid` verwendet in dieser Version QUADS und NEW_ENTITY. Die Dreiecke bleiben korrekt als degenerierte Quads mit vier Vertices aufgebaut; jeder Vertex liefert Position, Farbe, UV, Overlay, gepacktes Licht und Normalen. Die Animationsberechnung bleibt rein clientseitig aus synchronisiertem Ergebnis und Zeit.

Referenz zum BER-Vertrag: https://docs.neoforged.net/docs/1.21.1/blockentities/ber/

## Platzierungsfehler

Die Registrierung war vorhanden. Blackjack, Roulette und Glücksrad benutzten StationItem; Poker und Wechselstation den normalen BlockItem. Eigene Stuhlitems gibt es nicht.

Mehrere konkrete Codepfade erklären erfolglose Rechtsklicks:

- StationStructure.space wies jede Struktur über einer Chunkgrenze pauschal ab, auch bei freier Fläche und geladenen Chunks.
- Tischteile und Stuhlfreiraum mussten ausdrücklich Luft sein. Ersetzbare Blöcke wie Gras wurden abgewiesen.
- StationItem rief zunächst die Vanilla-Zustandsprüfung auf und setzte expanded erst danach. Damit wurde nicht die endgültige Hauptblockform geprüft.
- CasinoBlock und StationPartBlock konsumierten die Standard-Blockinteraktion durch Menüöffnung beziehungsweise Sitzen, bevor ein gehaltenes Blockitem zum Platzieren kam.
- Ablehnungen lieferten nur false/FAIL, ohne konkreten Grund. Der Aufbau der Teile erfolgte separat in setPlacedBy nach dem Hauptblock; nachträgliche Vollständigkeitskontrolle und Itemrückgabe ersetzten keine Transaktion.

Alle fünf Items verwenden jetzt StationItem. Zuerst wird die endgültige Ausrichtung samt expanded festgelegt, anschließend werden alle belegten Zellen, tragfähiger Boden, zwei freie Blöcke über jedem Stuhl, Bauhöhe/Weltgrenze, Berechtigung, geladene Chunks und tatsächliche Entity-Kollisionen geprüft. Ersetzbarkeit verwendet einen Kontext für die jeweilige Zelle. Spieler werden anhand der wirklichen Form geprüft, nicht anhand eines pauschalen Rechtecks um die ganze Station. Ein Spieler im tatsächlichen Kollisionsraum bleibt ein Hindernis und wird entsprechend gemeldet. Der bereits gesetzte Hauptblock wird beim Installationshilfsweg ausdrücklich erkannt.

Alle Teile werden zusammen mit dem Hauptblock vor Nachbarbenachrichtigungen aufgebaut. Bei einem Fehler werden der Eigentümer zuerst entfernt und die vorherigen Zellzustände wiederhergestellt. Eigene Teil-Abbaucallbacks erzeugen während dieser Transaktion keine Drops. Erst ein erfolgreicher placeBlock-Aufruf erreicht Vanillas Sound, Advancement, BlockEntity-Daten und Itemverbrauch; Creative verwendet weiter die Vanilla-Verbrauchsregeln. Der Client sagt die Interaktion voraus, während Blockaufbau und Verbrauch verbindlich vom Server kommen. Deutsche und englische Fehlermeldungen nennen Grund und Position.

Mit einem Casino-Item überspringen Hauptblock und Tischteile die Menüinteraktion zugunsten der Itemverwendung. Normale Spiel-, Sitz- und Menünutzung bleiben erhalten.

Geladene Nachbarchunks sind zulässig. Beim Abbau werden ungeladene, eindeutig zugeordnete Zellen in einer eigenen SavedData-Warteschlange gespeichert. Normales Chunkladen arbeitet nur die für diesen Chunk vorgemerkten Positionen ab; es gibt keine Scans, Chunk-Tickets, neuen Threads oder regelmäßigen Stationsticker. Auch der umgekehrte Fall (Teil abgebaut, Eigentümerchunk ungeladen) wird vorgemerkt. Die Warteschlange bewahrt Survival-/Creative-Drops und entfernt nur den erwarteten Zustand.

Der Clientlauf zeigte zusätzlich eine fehlende Filztextur: Blockmodelle verwiesen auf gui/felt, das im Standard-Blockatlas nicht aufgenommen wird. Die vorhandene Grafik wurde unverändert nach textures/block/felt.png kopiert und die Modellreferenzen korrigiert. Kein Redesign.

## Bestandene Prüfungen

- Vollständiger `gradlew.bat build` mit Java 21.0.9, auch Launcher und Gradle-Daemon auf Java 21: 74 JUnit-Tests, keine Fehler oder ausgelassenen Tests.
- 14 NeoForge-GameTests bestanden, Server beendet automatisch. Die neuen Tests verwenden unter anderem den echten serverseitigen Rechtsklickpfad ServerPlayerGameMode.useItemOn.
- 40 erfolgreiche Itemplatzierungen: fünf Items mal vier Ausrichtungen mal Survival/Creative. Richtiger Itemverbrauch, Eigentümerzuordnung und vollständige Teile; Abbau hinterlässt keine Teile.
- 152 Hindernisfälle an allen belegten Zellen in allen Ausrichtungen: keine Items verbraucht und keine halbfertigen Strukturen. Zusätzlich fehlender Boden, Stuhlfreiraum, Entity-Kollisionen, ersetzbares Gras und fehlende Adventure-Bauberechtigung.
- Rechtsklick auf eine bestehende Casino-Wechselstation platziert das gehaltene Pokeritem und öffnet kein Menü.
- Abbau über einen Strukturteil mit anschließender erneuter Platzierung: genau ein Drop in Survival, keiner in Creative, keine Restteile.
- Geladene Chunkgrenze, gespeicherte und wieder geladene Aufräumaufträge, korrekte Drops sowie Schutz fremder Ersatzblöcke.
- Erzwungene Entfernung eines Tischteils durch einen Nachbarcallback während des Aufbaus: Rollback stellt Gras wieder her, verbraucht kein Item und erzeugt keinen Drop.
- Legacy-Itemdaten mit expanded=false und eigener facing-Angabe: geprüfte große Struktur bleibt korrekt, Itemmetadaten bleiben erhalten.
- Echter NeoForge-Client mit integrierter Testwelt, GPU, Font, Vanilla-Dispatcher und MultiBufferSource: alle 16 Kombinationen aus Glücksrad/Roulette, expanded=false/true und vier Ausrichtungen wurden jeweils 1.078-mal gerendert. Mehrere Räder gleichzeitig; 8.624 Renderaufrufe mit laufender Drehung und nachgewiesen verändertem Winkel. BlockEntities wurden vorher aus ihren gespeicherten NBT-Daten mit unveränderten IDs wieder geladen. Kein Rendercrash. Der erste Versuch scheiterte an unvollständiger Erfassung der Testszene, der abschließende Lauf bestand.
- Installierbare JAR enthält Version 1.1.1 und die Filztextur, aber keine GameTest-, Probe- oder Client-Smoke-Testklassen.

Nachweise im Projekt:

- `build/final-build-gametest.log`
- `build/java-gradle-version.log`
- `build/reports/tests/test/index.html`
- `build/client-render-test.log`
- `run-render-check/client-render-result.txt`
- `run-render-check/screenshots/2026-10-01_23.20.58.png`

Reproduzierbare Clientprüfung: `gradlew.bat runClientRendererTest`. Sie legt eine eigene Entwicklungs-Testwelt im Verzeichnis run-render-check an und beendet den Client nach erfolgreicher Prüfung. Der Testcode wird nicht ausgeliefert.

## Noch im tatsächlichen Modpack prüfen

Der Clienttest lief ohne Sable, Veil und Flywheel. Deren Zusammenwirken mit dem Renderer bleibt offen. In einer vorhandenen Welt insbesondere das Glücksrad bei Overworld 29 86 22 ansehen, mehrere Räder drehen, Menüs/Chip-Auszahlungen und Sitzplätze mit mehreren Spielern prüfen. Auch echte Chunk-Entlade-/Neuladezyklen samt Serverneustart an einer chunkübergreifenden Station und die sichtbaren Fehlermeldungen im Modpack stehen noch aus. Die Aufräumwarteschlange wurde als gespeicherter NBT-Roundtrip gezielt geprüft; das ersetzt keinen kompletten Entladezyklus im Modpack.

## Austausch und Daten

Auf Server und allen Clients die alte ShiinaCasino-Mod-JAR durch build/libs/shiinacasino-1.1.1.jar ersetzen. Nur eine ShiinaCasino-Version gleichzeitig im jeweiligen mods-Ordner behalten. Die -sources.jar ist kein Installationsartefakt.

Block-/BlockEntity-IDs, Blockstate-Eigenschaften, Layout-Indizes, Rad-NBT, Spiele, Regeln, Menüs und Chipguthaben behalten ihr bestehendes Format. Die neue Aufräumwarteschlange liegt separat in shiinacasino_structure_cleanup.dat; CasinoSavedData wurde nicht geändert. Es fand keine Installation auf einem vServer und keine Löschung vorhandener Welten statt.
