# traffic-maniac

Eine Parodie auf klassische Verkehrssimulatoren: Statt den Verkehr einer echten Stadt flüssig zu halten, bremst man ihn mit Ampeln, Tempolimits, Blitzern, Radspuren und Baustellen aus – bis hin zum Stillstand.

Gewonnen hat aber nicht, wer die Stadt am besten lahmlegt, sondern wer dabei **am meisten Geld verdient und sich am besten präsentiert**. Ob die Stadt am Ende in Trümmern liegt oder strahlt, ist völlig egal.

Erste Stadt: **Pforzheim** (Straßennetz aus OpenStreetMap).

## Status

Version 0.1 ist spielbar (Meilensteine M0–M6), 0.2 (Fahrer & Überwachung) ist fertig, 0.3 (Bürger & Umwelt), 0.4 (Fahrzeugvielfalt & Chaos), 0.5 (Stadtverwaltung: Verfall, Reparaturen, Parken, Umleitungen), 0.6 (Ereignisse & Presse: Rad-Korsos, Demos, Spielkalender, Pressemodell, Achievements) und 0.7 (Cartoon-Look & Sound) sind größtenteils umgesetzt: Pforzheim mit rund 2.000 gleichzeitig fahrenden Autos, Tagesgang mit Berufsverkehr, Werkzeuge (Tempolimit, Ampel, Blitzer), Bußgelder, Bürgerzorn, Abwahlindex, PR, Lokalzeitung und Wertung nach 14 Spieltagen. Die Grafik ist im Comic-Stil selbst gezeichnet, Musik und Geräusche kommen aus einem eingebauten Synthesizer.

## Spielen

- Mausrad zoomt, Ziehen verschiebt die Karte.
- Unten die Werkzeugleiste: Tempolimit (mit Wert, auch "nur nachts"), Ampel, Blitzer, Laser, Vorfahrt, Radweg-Querung, Umleitung (auch über Nebenstraßen), Abbauen – dann auf eine Straße bzw. Kreuzung klicken.
- Rechts oben: Umwelt-Ebenen (Lärm, Luft, CO₂), Zufriedenheit der Wohngebiete, Straßenzustand, Blitzer-Statistik, Haushalt & PR, Ordnungsamt, Bauamt (Schadensmeldungen, Reparaturen, Erhaltungsbudget), Parken (Gebühren, Stellplätze, Politessen), Bürger, Kalender (Demos, Korsos, Markt, Marathon, Stadtfest), Achievements. Im Fenster Haushalt & PR gibt es einmal am Tag einen Spatenstich für die Presse.
- Leertaste pausiert, 1–4 wählen Tempo 1x/2x/4x/8x (nachts automatisch 8× schneller), Enter schließt den Tagesabschluss, M schaltet den Ton stumm, N die Musik.
- Ziel: nach 14 Tagen möglichst viel Geld in der Kasse und eine gute Reputation. Wird der Bürgerzorn zu groß, ist man vorher abgewählt.

## Entwickeln

Voraussetzung: JDK 25 (Gradle lädt sich selbst über den Wrapper).

```bash
./gradlew build                  # kompilieren, Tests, ktlint, detekt
./gradlew :desktop-app:run       # Spiel starten (Startmenü mit Szenarien)
./gradlew :desktop-app:run --args="--scenario berufsverkehr"   # direkt in ein Szenario
./gradlew :desktop-app:run --args="--warmup 60"   # mit einer Stunde Vorlauf (volle Straßen)
./gradlew :desktop-app:benchmark --args="5000 60" # Simulation ohne Oberfläche messen (Spitzen-Fahrten/h, Minuten)
./gradlew :desktop-app:headless --args="7"        # Strategien ohne Oberfläche durchspielen (Balancing)
scripts/import-pforzheim.sh      # OSM-Extrakt laden (155 MB) und data/cities/pforzheim.city erzeugen (Format 4 seit 0.5: neu importieren!)
./gradlew ktlintFormat           # Formatierung automatisch korrigieren
```

Spielwerte (Kosten, Bußgelder, Schwellen für Bürgerzorn usw.) stehen in `balancing/*.json`.

| Modul         | Inhalt                                                      |
|---------------|-------------------------------------------------------------|
| `city`        | Format der Stadtdatei: Straßennetz und Kulisse (Multiplatform) |
| `sim-engine`  | Verkehrssimulation, reines Kotlin (Multiplatform)           |
| `game`        | Spielregeln: Geld, Bürgerzorn, Werkzeuge (Multiplatform)    |
| `ui`          | Oberfläche mit Compose Multiplatform                        |
| `desktop-app` | Startpunkt und Packaging der Desktop-Anwendung              |
| `osm-import`  | Kommandozeilenwerkzeug: OpenStreetMap → Stadtdatei          |
| `build-logic` | gemeinsame Gradle-Konfiguration (Convention-Plugins)        |

## Dokumentation

- [Gesamtplan](docs/plan.md) – Spielprinzip, Architektur, Roadmap
- [Versionspläne](docs/versions/) – Umfang, Arbeitspakete und Abnahmekriterien pro Version, beginnend mit [Version 0.1](docs/versions/v0.1-kern.md)

## Technik (geplant)

- Kotlin Multiplatform, Gradle
- Desktop-Client mit Compose Multiplatform, 2D-Draufsicht im Cartoon-Stil
- Eigene Verkehrssimulation in reinem Kotlin
- Kartendaten aus OpenStreetMap
- Später optional: Micronaut-Backend und Web-Variante

## Lizenz

Apache License 2.0, siehe [LICENSE](LICENSE).

Kartendaten © OpenStreetMap-Mitwirkende, verfügbar unter der [Open Database License (ODbL)](https://www.openstreetmap.org/copyright).
