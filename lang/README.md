# Übersetzungen

Alle Texte stehen deutsch im Code – markiert mit `tr("…")` (sofort übersetzt) bzw. `translatable("…")` (feste
Beschriftungen, übersetzt bei der Anzeige). Platzhalter heißen `{name}` und müssen in der Übersetzung genauso
vorkommen; die Reihenfolge darf sich ändern.

`<Sprachkürzel>.json` bildet den deutschen Text auf die Übersetzung ab:

```json
"Tempo {kmh}: {street}": "{kmh} km/h: {street}"
```

Fehlt ein Eintrag oder ist er leer, zeigt das Spiel den deutschen Text.

## Ablauf

1. Neue oder geänderte Texte im Code: `scripts/i18n-extract.py en` – fehlende Einträge kommen mit leerer
   Übersetzung in `en.json`, verwaiste werden entfernt.
2. Die leeren Einträge übersetzen.
3. `./gradlew build` – `TranslationsTest` prüft, dass jeder Text übersetzt ist und die Platzhalter passen.

Eine neue Sprache braucht einen Eintrag in `Language` (Modul `i18n`) und eine eigene JSON-Datei. Stadtnamen,
Zeitung und Anrede kommen aus den Stadtpaketen (`cities/<id>/city.json`, Block `translations`).
