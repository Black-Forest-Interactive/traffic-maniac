# traffic-maniac

Eine Parodie auf klassische Verkehrssimulatoren: Statt den Verkehr einer echten Stadt flüssig zu halten, bremst man ihn mit Ampeln, Tempolimits, Blitzern, Radspuren und Baustellen aus – bis hin zum Stillstand.

Gewonnen hat aber nicht, wer die Stadt am besten lahmlegt, sondern wer dabei **am meisten Geld verdient und sich am besten präsentiert**. Ob die Stadt am Ende in Trümmern liegt oder strahlt, ist völlig egal.

Erste Stadt: **Pforzheim** (Straßennetz aus OpenStreetMap).

## Status

Version 0.1 in Arbeit – M0 (Projekt-Setup), M1 (Karte) und M2 (Verkehr) stehen: In Pforzheim fahren rund 2.000 Fahrzeuge mit Ampeln, Vorfahrt und Navi. Werkzeuge zum Ausbremsen gibt es noch nicht.

## Entwickeln

Voraussetzung: JDK 25 (Gradle lädt sich selbst über den Wrapper).

```bash
./gradlew build                  # kompilieren, Tests, ktlint, detekt
./gradlew :desktop-app:run       # Spiel starten
./gradlew :desktop-app:run --args="--warmup 60"   # mit einer Stunde Vorlauf (volle Straßen)
./gradlew :desktop-app:benchmark --args="5000 60" # Simulation ohne Oberfläche messen (Fahrten/h, Minuten)
scripts/import-pforzheim.sh      # OSM-Extrakt laden (155 MB) und data/cities/pforzheim.city erzeugen
./gradlew ktlintFormat           # Formatierung automatisch korrigieren
```

Im Spiel: Mausrad zoomt, Ziehen verschiebt, Leertaste pausiert, 1–4 wählen Tempo 1x/2x/4x/8x.

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
