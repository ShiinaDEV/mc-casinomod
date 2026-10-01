# ShiinaCasino 1.1.0

Minecraft 1.21.1, NeoForge 21.1.252, Java 21. Server und alle Clients benötigen dieselbe neue Mod-JAR. Die alte ShiinaCasino-JAR im jeweiligen mods-Verzeichnis ersetzen, nicht beide Versionen gleichzeitig installieren.

## Stationen und Bedienung

- Neu platzierte Blackjack-Tische haben eine Spielfläche von 3 × 2 Blöcken und sechs Stühle. Einschließlich Stühlen brauchen sie 3 × 4 Blöcke Grundfläche sowie freie Kopfhöhe.
- Neu platzierte Roulette-Tische haben 3 × 3 Blöcke Spielfläche, ein sichtbares animiertes Rad, ein nummeriertes Wettfeld und sechs Stühle. Mit Stühlen benötigen sie 3 × 5 Blöcke Grundfläche.
- Neue Glücksräder sind aufrecht auf einem dunklen Holzsockel aufgebaut. Die Station reserviert 3 × 1 × 3 Blöcke; das Rad zeigt zwölf gleich große Segmente, Beschriftung auf beiden Seiten und einen festen Zeiger.
- Rechtsklick auf einen freien Stuhl setzt den Spieler hin und öffnet das gemeinsame Spielmenü. Rechtsklick auf Tisch oder Stuhl öffnet es erneut. Escape/Menüschließen lässt den Spieler sitzen; Shift steht auf. Rechtsklick auf den Tisch ohne Sitz öffnet die Zuschaueransicht. An vergrößerten Tischen erfordert neues Setzen von Einsätzen einen zugehörigen Sitz.
- Alle Blackjack-Plätze gehören derselben Runde. Ein bereits spielender Platz bleibt bis zum Ende der Hand reserviert; der Spieler kann nicht auf einen anderen Platz wechseln. Beim Aufstehen gelten die bisherigen Blackjack-Abgangsregeln: Einsatz in der Setzphase erstatten, während der Hand automatisch stehen. Roulette-Wetten bleiben beim Aufstehen bestehen.
- Ein physisches Glücksrad führt jeweils eine gemeinsame Drehung aus. Ein weiterer Spieler kann nach Abrechnung seine Drehung starten. Auszahlungen ausschließlich in Chips: sechs Segmente 0×, drei 1×, zwei 2×, eins 4×. Die vorhandene Einstellung `wheelMultipliers` bleibt für alte Konfigurationsdateien lesbar, verändert diese feste Verteilung aber nicht mehr.
- Blackjack-, Poker-, Roulette- und Tauschregeln einschließlich Auszahlungen bleiben erhalten. Poker bleibt als Einzeltisch bestehen. Poker und Chip-Wechselstation behalten ihren Look; ihre fehlerhaften Gold-Ecken sind bündig korrigiert.

## Platzieren, Abbau und bestehende Welten

Eine Station wird mit dem bisherigen Stations-Item komplett aufgebaut. Ein ebener, solider Boden unter Tisch und Stühlen und freie Luft für die zusätzlichen Teile sind erforderlich. Die gesamte Station muss in einem Chunk liegen. Mit F3+G lassen sich Chunk-Grenzen anzeigen; bei erfolgloser Platzierung Platz/Boden/Kopfhöhe prüfen und von der Chunk-Grenze abrücken. Eine fehlgeschlagene Platzierung verbraucht kein Item.

Hauptblock und Teile bilden eine feste Einheit. Abbau eines Stuhls oder Tischteils baut die ganze Station ab; in Survival entsteht genau ein Stations-Item. Teile haben weder eigene Items noch Drops oder Rezepte. Belegte Sitze werden dabei freigegeben. Während einer finanzierten Runde ist normaler Abbau aller zugehörigen Teile gesperrt. Explosionen schützen finanzierte Stationen ebenfalls; Casino-Blöcke können nicht durch Kolben verschoben werden. Erzwungenes Entfernen durch Befehle und Chunk-Unload verwenden den bestehenden Rückzahlungs-/Ergebnisjournal-Pfad.

Bereits platzierte alte Blackjack- und Roulette-Tische funktionieren weiterhin als kleine Einzeltische mit ihren bisherigen Menüs. Sie werden nicht automatisch vergrößert und es werden keine Nachbarblöcke aus bestehenden Welten entfernt. Für größere Tische mit Stühlen muss man sie nach Rundenende selbst abbauen und mit der neuen JAR neu platzieren. Alte Glücksräder erhalten die aufrechte kleine Darstellung ohne zusätzliche Weltblöcke; für das große Rad ebenfalls neu platzieren. Neue Ausdehnung wird über eine Blockstate-Eigenschaft gespeichert; alte Zustände verwenden standardmäßig die alte Einblockform.

Sitze werden nicht gespeichert. Abgang, Tod, Dimensionswechsel, Blockabbau, Chunk-Unload und Serverstopp entfernen Belegung und Mount. Ein gespeicherter Sitzmarker ermöglicht beim erneuten Login eine sichere Position außerhalb des Stuhls, ohne einen Mount wiederherzustellen. Laufende Runden werden nicht nach einem Neustart fortgesetzt: das vorhandene Journal erstattet offene Blackjack-/Poker-Einsätze und Roulette-Einsätze vor Ergebnisbestimmung; bereits festgelegte Roulette-/Glücksrad-Gewinne werden genau einmal ausgezahlt. Bei vollem Inventar bleibt Guthaben über `/shiinacasino claim` verfügbar.

## Technik und Ressourcen

Die bestehende zentrale Terminverwaltung bearbeitet aktive Runden. Unbenutzte Stationsteile führen keine Ticklogik aus. Nur Roulette und Glücksrad besitzen eine kleine BlockEntity für den sichtbaren Zustand; beide haben keinen Ticker. Nur tatsächlich besetzte Stühle erzeugen je eine unsichtbare Sitz-Entity. Diese prüfen ihren eigenen Passagier/Stuhl, verwenden keine Physiksimulation und werden beim Freigeben entfernt.

Radpakete laufen über die vorhandene Minecraft-BlockEntity-/Chunk-Synchronisation: fortlaufende Zustandsnummer, Rundennummer, Startzeit in Welt-Ticks, Dauer, Ziel und Drehstatus. Clients berechnen Winkel und Interpolation, spielen begrenzte lokale Klick-/Stoppsounds und verwerfen ältere Zustandsnummern. Neu hinzukommende Clients erhalten den aktuellen Zustand über das Chunk-Update. Es gibt keine Animationspakete pro Tick oder Frame, keine Casino-Threads, globalen BlockEntity-Scans oder Chunk-Tickets. Menü-Updates verwenden weiterhin kleine Deltas; geheime Pokerkarten werden weiter nur pro Besitzer erzeugt und versendet. Es wurden keine RAM-/CPU-Messwerte erhoben.

## Manuelle Testliste für zwei bis vier Spieler

- [ ] Alle Clients und Server mit derselben 1.1.0-JAR starten; alte Einzeltische prüfen und neue Stationen in allen vier Richtungen platzieren. Auch Platzmangel und Chunk-Grenzen prüfen.
- [ ] Gleichzeitig an Blackjack und Roulette sitzen; Sitzhöhe, Blickrichtung, sichtbare Spieler, Stuhlkollision und Doppelbelegung prüfen.
- [ ] Shift aufstehen und wieder hinsetzen; sichere Position bei benachbarten Hindernissen prüfen. Während einer Blackjack-Hand Platzbindung und automatisches Stehen prüfen.
- [ ] Menü schließen und erneut öffnen: Spieler bleibt sitzen, Einsatz, Karten, Wetten und gemeinsame Runde bleiben konsistent. Zuschauer setzt ohne Sitz keine neuen Einsätze.
- [ ] Gemeinsame Blackjack- und Roulette-Runden spielen; alle Spieler vergleichen Aktionen, Fristen, Ergebnis und Auszahlung. Pokerkarten nur dem Besitzer anzeigen.
- [ ] Während einer Runde disconnecten, erneut anmelden, sterben und Dimension wechseln; keine verwaisten oder doppelt belegten Sitze.
- [ ] Tisch und Stuhl abbauen: aktive finanzierte Runde schützt Station; nach Rundenende komplette Entfernung, freie Sitze und genau ein Item. Auch Creative und Explosion prüfen.
- [ ] Glücksrad von vorn, hinten und seitlich ansehen; alle zwölf Segmente, feste Zeigerposition, sichtbare Drehung und ruhige Endposition prüfen. Menü beim Drehen schließen, sich entfernen und wieder annähern; andere Spieler und später hinzugekommene Clients sehen dieselbe Drehung.
- [ ] Glücksrad-Gewinn genau einmal erhalten, auch bei Disconnect oder vollem Inventar. Eine neue Drehung während einer aktiven Drehung wird abgelehnt; zweite Drehung nach Abrechnung ist möglich. Sounds enden zuverlässig.
- [ ] Server während belegter Sitze/laufender Runden stoppen und danach erneut einloggen: kein ungültiger Mount, sichere Position, Stationen vollständig, Journal-Auszahlung/Erstattung genau einmal.
- [ ] Goldbeschläge aus mehreren Blickwinkeln und bei Bewegung ansehen: kleine bündige Ecken, keine schrägen Platten, kein Flackern. Inventardarstellung und Beleuchtung prüfen.

Automatisierte Ergebnisse und offene Ingame-Prüfungen stehen im Lieferbericht. Diese Checkliste ist noch nicht als echter Multiplayer-/Sichttest durchgeführt worden.
