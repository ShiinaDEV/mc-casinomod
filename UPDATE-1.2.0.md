# ShiinaCasino 1.2.0

Fertige Mod für Minecraft 1.21.1, NeoForge 21.1.252 und Java 21. Entwickelt auf dem vorhandenen Stand 1.1.1; Registry-IDs, Weltdaten, Sitzmechanik, Platzierung und Auszahlungsregeln bleiben erhalten.

Installierbare JAR: `C:\Users\krawu\Desktop\Shiina Casino Mod\build\libs\shiinacasino-1.2.0.jar`

256.985 Bytes; SHA-256: `d98703a39f40e9f19fd68641b5522699cd9d808ce1a3d4b06eaa245004e4a35d`.

## Bedienung und Anzeigen

- Der Glücksrad-Minusbutton enthielt tatsächlich ein falsches `?`. Er verwendet jetzt `-`. Plus, Schließen und Hilfe verwenden `+`, `X`, `?`. Roulettebereiche und einfache Tauschbeschriftungen verwenden ASCII; korrekte Umlaute und andere nachweislich darstellbare Schriftzeichen bleiben erhalten. Verdeckte Karten behalten ihr absichtliches `?`.
- Serverbestätigungen und konkrete Ablehnungen erscheinen für kurze Zeit in einer Statuszeile. Während einer Anfrage zeigen Menü und deaktivierte Aktionen, dass die Bestätigung noch aussteht. Zustandsänderungen anderer Spieler bestätigen keinen eigenen Klick. Tischaktualisierungen überschreiben keine Rückmeldung durch einen leeren Text.
- Blackjack und Poker markieren den aktiven Spieler mit Rahmen, Farbe und `>`; Name und Zugzeit stehen zusätzlich in der Phasenanzeige. Dealerphasen bleiben verständlich beschriftet. Countdowns verwenden synchronisierte Fristen und eine lokale monotone Uhr.
- Persönliche Ergebnisse zeigen Einsatz, gesamte Auszahlung und Netto. Der Bereich heißt „Letzte Runde“, damit ein weiterhin sichtbares Ergebnis nicht mit dem neuen Einsatz verwechselt wird. Blackjack zählt Verdopplungen, Roulette alle eigenen Wetten, Poker die tatsächlichen Handbeiträge und die tatsächlich zugeteilten Beträge einschließlich Side Pots, geteilten Pots und nicht gecalltem Überschuss. Poker-Auszahlungen gehen zunächst in den Tischstack.
- Blackjack und Glücksrad übernehmen über „Letzter Einsatz“ ausschließlich den Betrag; Setzen/Drehen bleibt erforderlich. Roulette speichert die beim Ende der Setzphase tatsächlich vorhandene persönliche Kombination. „Letzte Wetten“ prüft alle Wetten, aktuelle Limits, die Grenze von zwölf Wetten, Teilnahmeberechtigung und die Gesamtkosten vor einer einzigen Abbuchung. Ein Fehlschlag erzeugt keine Teilwette.
- `?` öffnet die Spielhilfe innerhalb des Menüs, „Verlauf“ die letzten höchstens zehn eigenen abgeschlossenen Runden und „Ton“ die Einstellungen. Roulette bietet eine vollständige, größere Wettliste, damit zwölf Wetten nicht in winzigen Buttons dargestellt werden müssen. Unteransichten ändern weder Sitz noch Runde; laufende Fristen gelten weiter. Escape kehrt aus einer Unteransicht zum Spiel zurück.
- Karten bewegen sich kurz beim Austeilen bzw. Aufdecken; neue persönliche Ergebnisse erscheinen sanft. Bestätigte Aktionen und neue eigene Ergebnisse verwenden dezente Minecraft-Sounds. Gewinnende Roulettefelder erhalten einen Rahmen; das Glücksrad zeigt das markierte Ergebnisfeld und dessen Multiplikator auch im Menü. Alte bzw. doppelte Zustände erzeugen keine erneuten Effekte, das erstmalige Öffnen spielt keine vergangenen Ergebnisse ab.
- Die Wechselstation trennt einsetzbare Hauptinventarchips und erst abzuholendes Guthaben. Sie zeigt Diamanten, den tatsächlichen Kurs und mögliche ganze Tauschvorgänge einschließlich frei werdender Inventarplätze. Mengen: 1, 5, 10 oder Maximum, jeweils in Diamanten. Tausch ist vollständig atomar; beim Abholen vermindert nur die tatsächlich gelieferte Menge das Guthaben. Bestehende automatische Gewinnlieferung bleibt erhalten; gespeichertem Guthaben wird kein automatischer Einsatz hinzugefügt.

## Validierung und Ressourcen

Aktionspakete werden weiterhin an Menü, Spieler, Tisch, Runde, Revision, Sitz und Spielphase gebunden. Fristen werden auch im geschützten Logikzugang vor einer Aktion verarbeitet. Neue Fehlermeldungen unterscheiden beispielsweise abgelaufene Setzphase, fremden Zug, belegten Sitz, fehlende Chips, fehlenden Inventarplatz und nicht verfügbaren Tisch. Austauschpakete müssen die aktuelle Austauschrevision und den angezeigten Kurs nennen; Wiederholungen alter Pakete buchen nichts erneut ab.

Veraltete Zustandsdeltas werden **vor** dem Anwenden ihrer Arrayänderungen verworfen. Das verhindert auch Zugriffe auf inzwischen verkürzte Sitz-, Karten- oder Verlaufarrays. Ein serverseitiger Bestätigungszähler verhindert, dass ein fremdes Update den ausstehenden eigenen Klick freigibt oder als Erfolg vertont wird.

Verlauf und Wiederholungsvorlagen liegen nur im RAM der aktuellen Spielersitzung und werden beim Logout entfernt. Sie enthalten keine Karten. Verlaufseinträge entstehen an den tatsächlichen Abrechnungspunkten; vorzeitig abgebrochene Blackjack-/Poker-Hände und Roulette-Runden ohne festgelegtes Ergebnis erzeugen keine erfundenen Spielergebnisse. Bereits festgelegte Roulette-/Rad-Ergebnisse verwenden beim erzwungenen Schließen weiter die bestehende Auszahlung, mit genau einem zugehörigen Ergebnis.

Die Dealerabfolge war bereits richtig und ist erhalten: 1 Sekunde bis zum Aufdecken, 1,2 Sekunden zwischen zusätzlichen Karten und 1,5 Sekunden bis zum Ergebnis. Keine blockierenden Warteoperationen in der Spielimplementierung.

JavaCompile und Ressourcenfilter verwenden ausdrücklich UTF-8. Editor-Konfiguration, Ressourcenprüfungen und Modellgenerator sichern UTF-8 zusätzlich. Sprachdateien, erzeugte Metadaten, Gradle-Ressourcen und die tatsächlich verpackten Ressourcen wurden geprüft. Die Minecraft-Glyphprüfung unterscheidet Schriftunterstützung von Dateicodierung und bereits falschem Text.

Keine neuen Bibliotheken, Dienste, Datenbanken, Chunk-Tickets oder globalen Stationssuchläufe. Die bestehende Terminverwaltung bearbeitet finanzierte Runden. Nur offene Menüs vergleichen einmal pro Sekunde kleine Bestands-/Statusdaten und senden bei unverändertem Zustand kein Paket. Sichtbare Sekunden, Kartenbewegungen und Radbilder brauchen keine zusätzlichen Tick-/Framepakete. Radgeometrie im Menü wird gesammelt statt pro Farbstreifen einzeln an die GPU geschickt. Die bestehenden Welt-Renderer-Regeln für Buffer-Lebensdauer bleiben erhalten.

## Tatsächlich ausgeführte Prüfungen

| Prüfung | Ergebnis / Nachweis |
| --- | --- |
| Regulärer `gradlew.bat build` | Erfolgreich, Java 21; `build/build-1.2.0-final.log` |
| JUnit | 91 Tests, 0 Fehler, 0 übersprungen; `build/reports/tests/test/index.html` |
| Einsatz-/Abrechnungstests | Atomare Roulette-Wiederholung bei Geldmangel, geänderten Limits und Wettlimit; Storno/Vorlagen; verspätete/veraltete Aktionen; einmalige Abrechnung; Blackjack-Doppeln; Poker-All-ins, Side Pots und Rückgabe nicht gecallter Beiträge; begrenzter privater Sitzungsverlauf |
| Zusätzliche Randomprüfungen | 300 vollständige Pokerhände mit Chip- und Nettoerhaltung; 500 Inventar-/Kursfälle für die maximale sichere Tauschmenge |
| NeoForge-GameTest-Server | Alle 15 Tests bestanden, einschließlich echter Inventartransaktionen, Mehrfachpaketen, Mengenwechsel, Kurs-/Revisionsprüfung, voller Inventare, Sitzmechanik und Platzierungsregressionen; `build/gametest-1.2.0-final.log` |
| Echter Menüclient | 100 Ansichten bei GUI-Skalierung 1-4, deutsche und englische Glyphen, Grenzen, Überlappungen, vollständige Buttonbeschriftungen und lokale Unteransichten; `build/client-menu-test-1.2.0-release.log`, `run-menu-check/client-menu-result.txt` |
| Echter Client + integrierter Server | Glücksrad über die tatsächlichen Payloads: Bestätigung, einmalige Abbuchung, Ergebnis, korrekte Lieferung, eigener Verlauf genau einmal und Schließen/erneutes Öffnen ohne weitere Zahlung; Screenshot `run-menu-check/screenshots/live-wheel-result.png` |
| Welt-Renderer im echten Client | Glücksrad/Roulette, alle 16 Kombinationen aus Ausrichtung und alter/neuer Form, NBT-Roundtrip, mehrere Räder und bewegte Animation; keine „Not building!“-Exception; `build/client-render-test-1.2.0.log`, `run-render-check/client-render-result.txt` |
| Fertige JAR | 73 Klassen mit Classfile-Version 65; 71 gebaute Textressourcen geprüft, 183 passende Sprachschlüssel; keine Entwicklungs-/GameTest-Klassen; `python tools/VerifyRelease.py`, `build/release-verification-1.2.0.json` |

Die Menü-Matrix verwendet definierte Zustände in einem echten Minecraft-Client. Der anschließende Glücksrad-Test verwendet echte Netzwerkpakete und Serveraktionen. Zwei echte Clients, Hörprüfung aller Sounds, das vollständige Ziel-Modpack und ein vollständiger Chunk-Unload-/Reload-Zyklus mit mehreren finanzierenden Spielern wurden nicht ausgeführt. Es wurden keine CPU-/RAM-Messwerte für einen Vier-Spieler-Server erhoben.

### Nachprüfung des sichtbaren „Failed (exit 1)“

Der abschließende `runClientMenuTest` ist tatsächlich erfolgreich beendet worden. Sein erfasster Prozess-Exitcode war **0**; das vollständige Log `build/client-menu-test-1.2.0-release.log` enthält `CASINO CLIENT MENU TEST PASSED: 100 layouts AND LIVE SERVER ROUND` und `BUILD SUCCESSFUL in 1m 6s`. Ausgeführt wurde:

```powershell
.\gradlew.bat runClientMenuTest *> build/client-menu-test-1.2.0-release.log
exit $LASTEXITCODE
```

Die anschließend sichtbare **1** gehörte zur Auswertung: Nach dem Lesen dieses Logs, der Ergebnisdatei und des JAR-Prüfberichts lief zuletzt `rg -n 'TODO|placeholder' src/main/java/de/shiina/casino`. Diese Suche fand keine Treffer; dafür liefert `rg` regulär Exitcode 1. Der gemeinsame Auswertungsbefehl übernahm diesen Code. Er meldete damit keinen fehlgeschlagenen Menütest. Die Suche wurde bei dieser Nachprüfung separat ausgewertet und ihr Code 1 bestätigt.

Die Aktualität wurde anhand der vollständigen Gradle- und Minecraft-Logs, der Testimplementierung, Dateizeitpunkte und Bytevergleiche geprüft. Zeitangaben: 04.10.2026, Europe/Berlin (UTC+2).

| Nachweis | Befund |
| --- | --- |
| Fertige JAR | Geschrieben um 15:57:15.993; SHA-256 unverändert wie oben |
| Abschließender Menüclient | Start um 16:05:31; Modliste nennt ausdrücklich ShiinaCasino 1.2.0; Logabschluss um 16:06:34.983 |
| Ergebnisdatei | Neu geschrieben um 16:06:33.681; PASS und 100 unterschiedliche Fälle, jeweils 25 pro GUI-Skalierung 1-4 |
| Fallverteilung | Blackjack 20, Poker 20, Roulette 24, Glücksrad 20, Wechselstation 16; deutsche Menüansichten, zusätzlich Glyphprüfung beider Sprachdateien |
| Screenshots | Alle 16 Dateien aus diesem Lauf, zwischen 16:05:53.946 und 16:06:33.331 |
| Getesteter Stand | Alle 73 Hauptklassen und alle 85 Hauptressourcen bytegleich zur JAR; alle 42 aktuellen Java-Hauptquellen bytegleich zum zugehörigen Sources-Archiv; alle 84 vorhandenen Quellressourcen bytegleich zu den entsprechenden gebauten Ressourcen |

Der Entwicklungsclient lädt die kompilierten Projektdateien; die Bytevergleiche belegen deren Übereinstimmung mit der ausgelieferten JAR. Der Test löscht die alte Ergebnisdatei beim Start und schreibt PASS erst nach allen 100 tatsächlich gerenderten Ansichten sowie der echten Glücksrad-Runde und dem erneuten Öffnen ohne weitere Zahlung. Die Ergebnisdatei ist daher kein übernommener früherer PASS-Marker. Der Testquelltext wurde zuletzt um 15:50:22.330 geändert und um 15:50:25.227 kompiliert, vor dem abschließenden Lauf.

Frühere Fehlversuche bleiben ausdrücklich als solche erhalten: `build/client-menu-test-1.2.0.log` dokumentiert den abgebrochenen Start mit gleichzeitiger Ressourcenladung/Welterstellung; `build/client-menu-test-1.2.0-final.log` dokumentiert den zu frühen Hilfe-Klick des damaligen Testtreibers (`Incorrect subview`). Beide zeigen `BUILD FAILED` und Java-Exitwert -1. Die Startreihenfolge und das Warten auf tatsächlich gerenderte Bedienelemente wurden vor den folgenden erfolgreichen Läufen korrigiert. Ausschlaggebend für die Auslieferung ist das spätere `release.log`. Dessen vollständige Laufzeitlogs enthalten keine ERROR-/FATAL-Meldungen; vorhandene Warnungen betreffen Vanilla/Entwicklungsstart sowie das ausschließlich im Test registrierte `placement_rollback_probe`-Modell. Die umgeleiteten PowerShell-Gradle-Logs sind BOM-markiertes UTF-16 und wurden entsprechend gelesen; das ist unabhängig von der UTF-8-Codierung der Modquellen und Ressourcen.

Bei dieser Nachprüfung wurden keine erfolgreichen Gradle-, Menü-, GameTest- oder Rendererprüfungen erneut gestartet. Die Erfolgsangabe bleibt bestehen; ergänzt wurden die genaue Exitcode-Zuordnung, die Aktualitätsnachweise und die Abgrenzung der früheren Fehlversuche.

## Installation und Konfiguration

Auf Server und **allen** Clients die alte ShiinaCasino-JAR durch `shiinacasino-1.2.0.jar` ersetzen. Nur eine ShiinaCasino-Version gleichzeitig installieren; die `-sources.jar` gehört nicht in `mods`. Menüprotokoll 3 benötigt diese neue Version auf beiden Seiten. Eine Weltmigration oder Änderung bestehender Serverkonfigurationen ist nicht erforderlich. Bestehende Guthaben und Wiederanlauf-/Erstattungsregeln bleiben erhalten.

Neu: `config/shiinacasino-client.toml`, automatisch mit sinnvollen Standardwerten erzeugt. Änderungen sind auch über „Ton“ im Menü möglich und werden dort gespeichert.

```toml
casinoSounds = true
casinoSoundVolume = 0.5
reducedAnimations = false
```

Menüsounds respektieren die Minecraft-Hauptlautstärke; räumliche Radsounds zusätzlich die Blocklautstärke. Verlauf und letzte Einsätze werden beim Logout absichtlich zurückgesetzt. Es wurde weder committed noch gepusht oder auf einen Server deployt.

## Kurze manuelle Testliste

Singleplayer:

- Alle fünf Stationen öffnen, GUI-Skalierungen 1-4 ansehen; `-`, `+`, `X`, `?`, Umlaute, Hilfe, Verlauf und „Ton“ prüfen. Reduzierte Animationen und Sounds an/aus bzw. Lautstärke hören/ansehen.
- Blackjack mit Sieg, Verlust, Gleichstand und Verdopplung; Roulette mit mehreren Wetten; Glücksrad spielen. Einsatz/Auszahlung/Netto vergleichen. Letzten Betrag nur übernehmen und ohne Bestätigung keine Abbuchung erwarten; Roulette-Kombination bewusst wiederholen, auch bei Geldmangel.
- Volles Inventar bei Tausch, Guthabenabholung und Gewinn; danach Platz schaffen und Restguthaben abholen. Nichts darf verloren gehen. Mehr als zehn Runden spielen, Verlaufbegrenzung sowie Logout-Bereinigung prüfen.

Zwei Spieler:

- Gleichzeitige Sitzversuche, Blackjack-Züge und Dealerabfolge, Roulette-Wetten und Poker spielen. Aktiver Name/Frist müssen übereinstimmen; fremde Pokerkarten bleiben verborgen. Side Pots, kurze All-ins und geteilte Pots mit unterschiedlichen Stacks prüfen.
- Schnell mehrfach klicken, am Ende der Setzphase setzen, einen fremden Zug versuchen, aufstehen, Menü schließen/öffnen und disconnecten. Keine doppelte Abbuchung, Auszahlung oder reservierten Geistersitze; gespeichertes Guthaben vergleichen.
- Während finanzierter Runden normalen Abbau/Explosion prüfen, anschließend Abbauen und erneut platzieren. Auch tatsächliches Entfernen/Entladen und Neuladen von Chunks sowie Serverneustart im Ziel-Modpack prüfen: bisherige Erstattungs-/Ergebnisregeln, genau eine Lieferung und vorhandene Weltteile/Sitzplätze müssen erhalten bleiben.
