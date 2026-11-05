# Später – Online & Web

> Teil der [Roadmap](../plan.md#4-roadmap). Verweise wie "3.1" beziehen sich auf Abschnitte in [plan.md](../plan.md).

## Ziel

Das Spiel kommt **ins Netz und in weitere Städte** – sobald die Desktop-Version steht. Die Architektur (reines Kotlin in `sim-engine` und `game`, Kommunikation über Befehle und Snapshots) ist genau dafür vorbereitet (3.1, 3.3).

## Voraussetzungen

- Version 1.0
- `sim-engine` und `game` sind frei von JVM-only-Abhängigkeiten in `commonMain`

## Mögliche Bausteine

### Micronaut-Backend
- Online-Highscores (mit Replay-Validierung: Server rechnet die Partie deterministisch nach)
- Städte-Katalog: Stadtdateien zum Herunterladen
- Accounts (optional)

### Weitere Städte
- `osm-import` als Dienst oder Batch-Job für beliebige Städte
- Qualitätscheck pro Stadt (Netzdichte, Zonen, Brücken, Ladesäulen)
- Kuratierte Liste deutscher Städte mit lokalem Humor in den Zeitungsvorlagen

### Web-Variante
- **Option A: Compose Multiplatform für Wasm** – gleicher UI-Code, Simulation läuft im Browser
- **Option B: Angular-Frontend + Server-Simulation** – Micronaut simuliert, Browser zeigt an (WebSocket, Binärprotokoll, Viewport-Filter)
- Entscheidung erst, wenn es so weit ist; A ist vermutlich deutlich weniger Aufwand

### Multiplayer (Idee)
- "Wer legt seine Stadt zuerst lahm" bzw. "Wer kassiert am meisten" – parallele Partien mit gleichem Seed, Vergleich in Echtzeit
- Erfordert Server-Simulation oder Replay-Abgleich

## Offene Fragen

- Lohnt sich eine Web-Variante, oder reicht Desktop?
- Hosting und Kosten für Backend und Städte-Downloads
