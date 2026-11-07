# Stadtpakete

Jede Stadt ist ein Ordner `cities/<id>/`:

| Datei | Inhalt |
|-------|--------|
| `city.json` | Name und Texte (Zeitung, Anrede, Spitzname, Unterzeile im Menü), Beschreibung für die Stadtauswahl, Feinabstimmung und woher die Kartendaten kommen |
| `map.city` | die Karte – **nicht im Repository**, wird erzeugt: `scripts/import-city.sh <id>` |
| `LICENSE-ODbL.txt` | Lizenzhinweis zu den Kartendaten (OpenStreetMap, ODbL); gehört zu jeder ausgelieferten Karte |

Das Spiel bietet alle Pakete mit Karte im Startmenü zur Auswahl an.

## Feinabstimmung

`simulation` und `rules` überschreiben einzelne Werte aus `balancing/simulation.json` bzw.
`balancing/game-rules.json` – nur für diese Stadt, mit denselben Namen. Unbekannte Namen sind ein Fehler.
Beispiel für eine kleinere Stadt mit knapperer Kasse:

```json
"simulation": { "peakTripsPerHour": 3000 },
"rules": { "startBudget": 120000, "weeklyAllowance": 30000 }
```

Die Szenarien legen ihre Faktoren (z. B. `demandFactor`) darüber.

## Neue Stadt

1. Ordner `cities/<id>/` anlegen, `city.json` von Pforzheim kopieren und anpassen: `import.extract` ist ein
   OSM-Auszug von [Geofabrik](https://download.geofabrik.de/), `import.bbox` der Ausschnitt
   `minLon,minLat,maxLon,maxLat` (Stadtgebiet mit etwas Rand).
2. `LICENSE-ODbL.txt` mitkopieren.
3. `scripts/import-city.sh <id>` erzeugt `map.city`.
