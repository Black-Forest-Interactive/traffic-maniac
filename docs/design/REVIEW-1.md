# Design-Review 1 – Abgleich Umsetzung ↔ `DESIGN.md`

Grundlage: Screenshots vom 08.11.2026 (Fenster ~2000 × 1050 px). Reihenfolge = Priorität.
Referenz-Entwürfe: `docs/design/mockups/`.

## 1. Alles ist etwa halb so groß wie im Entwurf (wichtigster Punkt)

Der Entwurf ist für ein 1440 px breites Fenster gezeichnet. In der Umsetzung sind Topbar, Rail, Dock und Fenster
bei 2000 px Breite **kleiner** als im Entwurf bei 1440 px – Texte landen bei ~9–10 px, Klickflächen bei ~24 px.

| Element | Entwurf | Umsetzung (ca.) |
|---|---|---|
| Topbar-Höhe | 64 dp | 34 px |
| Uhrzeit | Mono 20 sp | ~11 px |
| KPI-Wert | Mono 15 sp | ~9 px |
| Rail-Breite / Eintrag | 92 dp / 58 dp hoch | 48 px / ~30 px |
| Dock-Kachel | 78 × 68 dp, Label 11 sp | ~40 × 40 px, Label ~7 px |
| Seitenfenster | 372 dp | ~195 px |
| Fenster-Titel | 22 sp | ~12 px |

**Fix:**
- Werte aus `DESIGN.md` 1:1 als dp/sp übernehmen, nichts „platzsparend“ verkleinern. Mindestens 44 dp für alles Klickbare.
- Zusätzlich eine **UI-Skalierung** einführen: `CompositionLocalProvider(LocalDensity provides Density(base.density * uiScale, base.fontScale * uiScale))`
  um den HUD-/Menü-Baum (nicht um die Karte). Default `uiScale = 1.0`, Auswahl in Einstellungen: 90 % / 100 % / 125 % / 150 %,
  optional „Automatisch“ = `clamp(windowWidthDp / 1440, 1.0, 1.5)`.

## 2. Hauptmenü & Laden sind noch nicht umgebaut

Aktuell: alte Struktur (Button-Liste + schmale Karte), nur umgefärbt; cremefarbener Hintergrund; gelber CTA.
Soll (siehe `mockups/Main.dc.html`, `mockups/Laden.dc.html`, DESIGN.md §5.1/5.2):

- Dunkle Seitenleiste `Ink` (260–300 dp) mit Logo, Claim, Icon-Navigation, „Beenden“ unten, OSM-Hinweis.
- Hintergrund rechts `Ground` (#EEF1F4) – **kein Creme** mehr (Creme gehört nur zur Karte).
- Szenarien als Kachelraster (`GridCells.Adaptive(300.dp)`), Kachel min. 236 dp hoch, Titel Display 22 sp.
- Auswahl eindeutig: gewählt = 3 dp `Primary`-Rahmen + Häkchen; nicht gewählt = 1 dp `Line`. Hover nur leicht (`Ground`).
  Heute ist unklar, ob „Tutorial“ (weiß) oder „Erste Amtszeit“ (grau) gewählt ist.
- Feste Aktionsleiste unten: Schwierigkeit als `SegmentedControl`, Zusammenfassung, CTA.
- **CTA „Los geht’s“ und aktiver Menüpunkt = `Primary` (Blau, weißer Text)**, nicht Gelb. Gelb (`Active`) ist nur für
  „gerade aktiv im Spiel“ (Werkzeug, Tempo, offenes Rail-Fenster) reserviert.

## 3. Schriften fehlen teilweise

„Szenario wählen“ rendert in einer Serifen-Fallback-Schrift, Titel in Noto Sans Black. Bricolage Grotesque / Inter
werden offenbar nicht geladen. Prüfen: Font-Dateien unter `composeResources/font/`, `FontFamily(Font(Res.font.…))`
und überall über die Typografie-Tokens statt Default-`TextStyle`.

## 4. Topbar

- **Abwahlindex-Chip streckt sich über die ganze Breite** (`weight(1f)`?). Feste Breite ~180 dp, alle KPI-Chips als
  Gruppe zentriert, Rest ist leerer Raum. Meter nur innerhalb des Chips.
- Bürgerzorn-Chip hat kein farbiges Icon; Icons wie im Entwurf (Kasse gelb, Reputation hellblau, Zorn/Abwahl hellrot).
- Wenn der Abwahlindex > 50: Chip-Hintergrund `Danger`-Tint, damit Gefahr auffällt.

## 5. Meldungen

- Titel sind zu lang und umbrechen dreizeilig. Kurzform: **Titel** = Was („Warnschild Tempo 30“, „Schlaglöcher“),
  **Unterzeile** = Wo · Uhrzeit („Karlsruher Straße · 10:07“).
- Gleichartige Meldungen bündeln: „3× Warnschild Tempo 30“ aufklappbar.
- Punktfarben nach Typ: Schaden/Unfall `Danger`, eigene Maßnahme `Primary`, Geld/erledigt `Success`, System (Spielstand, Vertrag) `Muted`.
- Max. 5 sichtbar, danach „Alle anzeigen“.

## 6. Werkzeug-Dock

- Label „Radweg-Querung“ bricht um und macht die Kachel höher → im Dock „Radweg“, voller Name im Tooltip.
- Alle Kacheln gleich hoch (`height(IntrinsicSize.Max)` oder feste 68 dp), Preis immer in Zeile 3, auch leer.
- Tastenkürzel oben links mit 60 % Deckkraft sichtbar machen (aktuell kaum zu erkennen).

## 7. Rail

- „Ordnungs-amt“ bricht falsch um → Label „Ordnung“ oder Schrift 10 sp + Silbentrennung „Ordnungs-/amt“ bewusst setzen.
- Abstand zwischen Einträgen 2 dp, Eintrag 58 dp hoch, Icon 22 dp (siehe Punkt 1).

## 8. Kleinigkeiten

- Tutorial-Karte unten links: Fortschrittspunkte (8/9) als Balken wie im Entwurf, Breite 340 dp.
- Seitenfenster „Haushalt & PR“: gut umgesetzt – nur Größen (Punkt 1). Die Segmente sind korrekt.
- OSM-Attribution unten rechts mit halbtransparentem Hintergrund, sonst unlesbar auf der Karte.

## Reihenfolge

1 → 3 → 2 → 4 → 5 → 6 → 7 → 8. Nach Punkt 1 einmal neue Screenshots bei 1440 × 900 und 2000 × 1050 vergleichen.
