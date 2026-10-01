# Assets und Abhängigkeiten

Die Mod enthält ausschließlich für dieses Projekt erzeugte Pixeltexturen,
Block-/Itemmodelle, Kartenzeichnungen und Menügestaltung. Lizenz: MIT,
siehe `LICENSE`. Die Quelldatei `tools/GenerateAssets.java` erzeugt alle 14
PNG-Texturdateien (der Filz liegt für GUI und Blockatlas getrennt vor) reproduzierbar. Die vom Benutzer bereitgestellte `casino-vorlage.png`
wurde nur als gestalterische Referenz angesehen: Holz/Filz/Gold, graue Buttons,
rote Kartenrückseiten und das Tischrad. Sie wird weder als GUI-Textur verwendet
noch in die Mod-JAR kopiert. GUI-Karten und Radsegmente
werden durch eigenen Java-Code gezeichnet.

Sounds: Verweise auf die bereits installierten Vanilla-Sounds
`minecraft:ui.button.click` und `minecraft:entity.experience_orb.pickup`.
Minecraft-Sounddateien, Schriftarten oder Texturen werden nicht in die Mod kopiert.
Minecraft selbst bleibt unter den Nutzungsbedingungen von Mojang/Microsoft:
https://www.minecraft.net/eula

Laufzeitvoraussetzungen:

| Komponente | Version | Lizenz / Quelle | In der Mod-JAR eingebettet |
| --- | --- | --- | --- |
| Minecraft | 1.21.1 | Mojang/Microsoft, https://www.minecraft.net/eula | Nein |
| NeoForge | 21.1.252 | LGPL-2.1-only, https://github.com/neoforged/NeoForge | Nein |
| Gson, durch Minecraft bereitgestellt | 2.10.1 | Apache-2.0, https://github.com/google/gson | Nein |
| Java | 21 | Lizenz der gewählten Java-Distribution | Nein |

Werkzeuge ausschließlich für Entwicklung/Tests:

| Komponente | Version | Lizenz / Quelle |
| --- | --- | --- |
| Gradle Wrapper / Gradle | 9.2.1 | Apache-2.0, https://github.com/gradle/gradle |
| ModDevGradle | 2.0.148 | LGPL-2.1-only, https://github.com/neoforged/ModDevGradle |
| NeoForge-MDK-Template | Minecraft 1.21.1 | MIT, unveränderte `TEMPLATE_LICENSE.txt` |
| JUnit Jupiter / Platform | 5.11.4 / 1.11.4 | EPL-2.0, https://github.com/junit-team/junit5 |
| Parchment-Mappings | 1.21.1 / 2024.11.17 | CC0-1.0, https://github.com/ParchmentMC/Parchment |
| Foojay Resolver | 1.0.0 | Apache-2.0, https://github.com/gradle/foojay-toolchains |

Die originale ExampleMod-MDK-Demonstration bleibt im Ordner erhalten, wird
jedoch durch die Build-Konfiguration vollständig von ShiinaCasino ausgeschlossen.
Es gibt keine zusätzliche zu installierende Modbibliothek und keine Datenbank.
