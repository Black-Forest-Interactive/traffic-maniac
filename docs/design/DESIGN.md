# Traffic Maniac – GUI-Redesign „Leitstelle“

Übergabe für die Umsetzung in Compose Multiplatform (Modul `ui`).
Die Entwürfe liegen unter `docs/design/mockups/` (`*.dc.html`, Quelltext aus dem Design-Canvas –
nicht direkt im Browser lauffähig, aber als Referenz für Maße, Farben und Inhalte lesbar).

| Entwurf | Inhalt | betrifft vor allem |
|---|---|---|
| `Main.dc.html` | Hauptmenü / Szenario-Auswahl | `MainMenu.kt` |
| `Laden.dc.html` | Spielstand laden | `MainMenu.kt` |
| `Game.dc.html` | Spiel-HUD (Topbar, Rail, Karte, Werkzeug-Dock, Seitenfenster) | `TrafficManiacApp.kt`, `TrafficHud.kt`, `Tools.kt`, `SidePanels.kt`, `OfficePanel.kt` |
| `Panels.dc.html` | Fenster: Bauamt, Parken, Bürger, Einsätze, Blitzer-Statistik, Erfolge | `MaintenancePanel.kt`, `ParkingPanel.kt`, `CitizensPanel.kt`, `EventsPanel.kt`, `CameraStatsPanel.kt` |
| `Farben.dc.html` | Farb- und Schrift-Übersicht | `ComicTheme.kt` |

## Leitidee

Die OSM-Karte ist bunt (Creme, Grün, Blau, Orange) – die Bedienung tritt zurück:
**dunkles Bedienfeld** (Topbar, linke Rail, Werkzeug-Dock) + **weiße Fenster** über der Karte.
Farbe in der UI hat immer eine Bedeutung:

- **Amtsblau** = primäre Aktion, Auswahl, Fortschrittsbalken
- **Ampelgelb** = „gerade aktiv“ (gewähltes Werkzeug, Tempo, aktive Rail-Seite)
- **Alarmrot** = Problem (Badges, Unfälle, Fristen, Löschen)
- **Grün** = Geld rein / erledigt

Die Kartenfarben (`ui/map/MapStyle.kt`) bleiben unverändert.

## 1. Farben

`ComicTheme.kt` durch diese Tokens ersetzen bzw. ergänzen:

```kotlin
object TmColors {
    // Dunkles Bedienfeld
    val Ink        = Color(0xFF15202B) // Topbar, Dock, Haupttext auf hell
    val Ink2       = Color(0xFF1E2B38) // linke Rail
    val Ink3       = Color(0xFF243444) // Chips/Segmente auf dunkel
    val LineDark   = Color(0xFF3B4C5D) // Trenner/Rahmen auf dunkel
    val OnDark     = Color(0xFFEEF1F4) // Text auf dunkel
    val OnDarkMuted= Color(0xFFA3B0BD) // Nebentext auf dunkel

    // Helle Flächen
    val Ground     = Color(0xFFEEF1F4) // Menü-Hintergrund, Kacheln in Fenstern
    val Surface    = Color(0xFFFFFFFF) // Fenster, Karten
    val Line       = Color(0xFFC3CCD5) // Rahmen hell
    val LineSoft   = Color(0xFFE3E8ED) // Trenner in Fenstern
    val Muted      = Color(0xFF586674) // Nebentext auf hell (≥ 4.5:1)

    // Akzente
    val Primary    = Color(0xFF2257B8) // Amtsblau, weißer Text darauf
    val PrimaryHover = Color(0xFF1A4691)
    val PrimaryTint= Color(0xFFEAF1FC) // gewählte Radio-Zeile
    val Active     = Color(0xFFFFC21A) // Ampelgelb, Text darauf = Ink
    val Danger     = Color(0xFFC62B3B) // weißer Text darauf
    val DangerText = Color(0xFFA3202E)
    val DangerTint = Color(0xFFFBE3E5)
    val Success    = Color(0xFF1E7A44)
    val SuccessTint= Color(0xFFDDF0E4)
    val Warn       = Color(0xFFF28C28) // Panne, „unzufrieden“-Balken
    val WarnTint   = Color(0xFFFFF3CC) // Frist-Chips, Text darauf 0xFF6B4A00

    // Ampel im Logo
    val LogoRed    = Color(0xFFE5484D)
}
```

Fokus-Ring: 3 dp `Active` (auf dunkel) bzw. `Primary` (auf hell), 2 dp Abstand.

## 2. Schrift

Drei Familien (alle OFL, als Compose-Resources unter `ui/src/commonMain/composeResources/font/` einbinden):

| Rolle | Familie | Verwendung |
|---|---|---|
| Display | **Bricolage Grotesque** ExtraBold 800 | Screen-Titel 44 sp, Fenster-Titel 22 sp, Szenario-Titel 22 sp, CTA 20 sp |
| Text | **Inter** 400/500/600/700 | alles andere; Body 14 sp, Labels 13 sp, klein 11–12 sp |
| Zahlen | **JetBrains Mono** 500/700 | Geld, Uhrzeit, Zähler, Prozente, Preise, Tastenkürzel |

Eyebrow-Labels über Titeln: Inter 700, 11 sp, Großbuchstaben, Letterspacing 1 sp, `Muted`.

## 3. Formen & Abstände

- Radien: Chips 6 dp · Buttons/Segmente 8–10 dp · Fenster/Karten 14 dp · Dock/Szenario-Karten 16 dp
- Fenster über der Karte: `Surface`, 1 dp `Line`, Schatten ~ 0/6/18 dp, `Ink` @ 18 %
- Dunkle Schwebeelemente (Ebenen-Umschalter, Zoom, Dock): `Ink`, Schatten `Ink` @ 28–35 %
- Raster 4 dp; Fenster-Innenabstand 20 dp horizontal, Sektionen durch 1 dp `LineSoft` getrennt
- **Klickflächen mindestens 44 dp** (im Bestand sind viele Buttons ~24 dp hoch – das ist der größte Usability-Gewinn)

## 4. Wiederverwendbare Komponenten

Vorschlag: neue Datei `ui/.../ui/components/` mit:

- **`SegmentedControl`** – Container `Ground`, 4 dp Padding, Segmente ohne Rahmen; gewählt = `Ink` gefüllt, weißer Text.
  Optional zweite Zeile in Mono für Preise/Werte (z. B. „normal / ±3 km/h“, „das Nötigste / 20.000 €“).
  Ersetzt die heutigen Reihen aus losen Pill-Buttons (Schärfe, Erhaltungsbudget, PR-Agentur, Parkregel, Gebühren).
- **`Stepper`** – `[−] Wert [+] [+5]`, 1 dp `Line`, Buttons 40 dp, Wert in Mono. Für Sachbearbeiter, Politessen, Bearbeitungsfrist, Parkhaus-Preise.
- **`StatTile`** – Kachel `Ground`, Label 11 sp `Muted`, Wert Mono 15–18 sp, optional Meter darunter.
- **`Meter`** – 6–10 dp hohe Leiste, Spur `LineSoft`/`#D5DCE3`, Füllung `Primary` (oder `Warn` bei „schlecht“), optional Schwellen-Markierung (2 dp `DangerText`).
- **`Badge`** – Pill, Tint-Hintergrund + kräftige Textfarbe (`DangerTint`/`DangerText`, `WarnTint`/`#6B4A00`, `SuccessTint`/`#14532D`).
- **`PanelScaffold`** – Kopf (Eyebrow „Amt“/„Bericht“ + Display-Titel + Schließen-Icon 40 dp), scrollbarer Inhalt.
- **`RadioCardList`** – Radio-Zeilen 44 dp; gewählt = 2 dp `Primary`-Rahmen + `PrimaryTint`. Für Entscheidungen mit Satz-Labels (z. B. „Fahrer nicht ermittelbar“).

Icons: einfache Strich-Icons (24er Raster, Strich 1.8–2), z. B. Lucide-Pfade als `ImageVector`. Keine Emojis.

## 5. Screens

### 5.1 Hauptmenü (`Main.dc.html`)
- Links dunkle Seitenleiste (`Ink`, 260–300 dp): Logo (Ampel + „TRAFFIC / MANIAC“), Claim, Navigation mit Icons
  (Spielen, Laden [+ Anzahl], Bestenliste, Einstellungen, Credits), unten „Beenden“ (Outline) + OSM-Hinweis.
  Aktiver Eintrag `Primary` gefüllt, 48 dp hoch.
- Rechts: Eyebrow „Neues Spiel“, Titel „Wählen Sie Ihre Amtszeit“, daneben Stadt-Karte (Kürzel-Badge, Name, Beschreibung, „Stadt ändern“).
- Szenarien als **Kachelraster** (min. 300 dp breit, auto-fill): Tag-Chip (Tutorial = `Active` „Empfohlen“), Titel, Beschreibung,
  Fußzeile mit Dauer + Startzeit. Gewählt: 3 dp `Primary`-Rahmen + Häkchen-Kreis.
- **Feste Aktionsleiste unten**: Schwierigkeit als `SegmentedControl`, Zusammenfassung („Pforzheim · Leicht / Tutorial … · 3 Tage“), CTA „Los geht’s →“ (`Primary`, 56 dp).

### 5.2 Spielstand laden (`Laden.dc.html`)
- Gleiche Seitenleiste.
- Neuester Stand groß: Mini-Kartenvorschau, „Zuletzt“-Chip, relative Zeit („heute, 14:29“), Szenario, Kasse/Reputation, CTA „Fortsetzen“.
- Ältere Stände als Tabelle: Spielstand | Kasse | Reputation | Gespeichert | [Laden] [Papierkorb-Icon].

### 5.3 Spiel-HUD (`Game.dc.html`)
Ersetzt die heutigen zwei Button-Reihen oben rechts und den Textblock oben links.

- **Topbar** (`Ink`, volle Breite): Logo + „Montag · Tag 1 von 3“ + Uhrzeit (Mono 20 sp) | Tempo-Segmente ⏸ 1× 2× 4× 8× (aktiv = `Active`) |
  KPI-Chips (`Ink3`): Kasse (+ Bußgelder), Reputation /100, Bürgerzorn, Abwahlindex mit Meter | Speichern, Menü.
- **Linke Rail** (`Ink2`, 92 dp): Icon + Label, gruppiert
  - *Ämter*: Ordnungsamt, Bauamt, Parken
  - *Berichte*: Blitzer, Haushalt & PR, Bürger, Kalender, Einsätze (Badge = Anzahl), Erfolge, Hilfe (= Legende)
  - Offenes Fenster = `Active` hinterlegt. Ein Klick öffnet/schließt das Fenster rechts.
- **Kartenansicht-Umschalter** oben mittig über der Karte (dunkles Segment): Verkehr, Umwelt: Luft, Zufriedenheit, Straßenzustand.
  Ersetzt die Buttons „Umwelt“, „Zufriedenheit ausblenden“, „Zustand“.
- **Oben links**: einklappbare Karte „Verkehrslage“ (Kurzzeile: Kfz · Ø km/h; aufgeklappt: wartend, angekommen, …),
  darunter **Meldungen** als eine Liste (farbiger Punkt je Typ, Titel, Zeit · Ort/Frist, Klick = Karte springt hin).
  Rechenzeit/FPS raus aus dem HUD → Debug-Overlay (z. B. F3).
- **Werkzeug-Dock** unten mittig (`Ink`, Radius 16): 10 Kacheln à 78 dp (Icon, Name, Preis in Mono, Tastenkürzel oben links).
  Aktives Werkzeug = `Active`. Werkzeuge: Ansehen, Tempolimit 2.000 €, Ampel 60.000 €, Blitzer 90.000 €, Laser 1.500 €/Tag,
  Anhänger 45.000 €, Vorfahrt 3.000 €, Radweg-Querung 8.000 €, Umleitung, Abbauen.
  *(Die Tastenkürzel Q/W/E/R/T/A/S/D/X im Entwurf sind Platzhalter – an echte Belegung anpassen, 1–4 sind schon Tempo.)*
- **Werkzeug-Optionen** als Popover direkt über dem Dock (weiß): bei Tempolimit runde Schild-Buttons
  Schritt/10/20/30/40/50/70 (gewählt = 3 dp roter Ring wie ein echtes Schild) | Original | nur nachts.
- **Zoom** +/− rechts mittig (dunkel). OSM-Attribution unten rechts.
- **Seitenfenster rechts** (372 dp, volle Höhe, weiß, scrollt) statt schwebender Kästen unterschiedlicher Größe.

### 5.4 Fenster (`Panels.dc.html`, Ordnungsamt in `Game.dc.html`)
Alle mit `PanelScaffold`; Kennzahlen oben als `StatTile`s, darunter Einstellungen in Sektionen.

- **Ordnungsamt & Bußgeldstelle**: Tiles Aufklärungsquote (Meter), Aktenstapel (bearbeitet · bezahlt · verjährt · eingestellt);
  Stepper Sachbearbeiter (+5) und Bearbeitungsfrist (max. 10 Tage); Segment „Schärfe der Blitzer“ (kulant ±5 / normal ±3 / gnadenlos ±1);
  `RadioCardList` „Wenn der Fahrer nicht ermittelbar ist“; Fußzeile Fahrverbote.
- **Bauamt**: Tiles Zustand Ø / Repariert / Klagen; Segment Erhaltungsbudget (keins 0 € / das Nötigste 20.000 € / vorbildlich 45.000 €);
  „Offene Schäden“ als **Akkordeon** – immer nur ein Schaden aufgeklappt, mit Gruppen *Reparieren* (Ausschreibung / Turbo / Planfeststellung + Preis)
  und *Oder absichern* (Schild 30 / Schild 10 / Nichts tun) + „Auf Karte zeigen“. Frist als `WarnTint`-Badge.
- **Parken & Politessen**: Tiles Parkplätze, Straßenrand, Knöllchen; Stepper Politessen; Segment Brötchentaste/Anwohnerparken,
  „Parkplätze → Blumenkübel 15.000 €“ als gestrichelter Button; zwei Gebühren-Segmente (0 € … 5 €/Std.);
  Parkhaus-Liste: Name + Belegung, Preis-Stepper, Papierkorb (statt `€− €+ − + weg`).
- **Bürger**: Unzufrieden-Meter mit Schwelle bei 35 %; Balkenlisten „Bedürfnisse im Mittel“ und „Zufriedenheit je Haushaltstyp“
  (Label | Balken | Mono-Prozent – Prozent nicht mehr umbrechen!); „Hier brodelt es“ mit „Zeigen“-Buttons.
- **Einsätze**: je Einsatz farbige Seitenleiste (Unfall rot, Panne orange), Titel, „seit … min“, Einheiten als Badges
  (vor Ort = grün, unterwegs = gelb).
- **Blitzer-Statistik**: Einnahmen gesamt (grün), gestapelter Balken „wie viel zu schnell“ (≤10 / 11–20 / 21–30 / >30, hell→dunkel+rot),
  Tabelle Standort | Fotos | Einnahmen, Rekordzeile.
- **Erfolge**: Zähler x/15, 2-spaltiges Raster, gesperrte als gestrichelter Kreis mit Schloss, „Alle anzeigen“.
- **Haushalt & PR, Kalender, Hilfe/Legende**: gleiches Muster (Tiles + Segmente bzw. Liste) – im Entwurf nicht einzeln gezeichnet.

## 6. Umsetzungsreihenfolge (Vorschlag)

1. `TmColors` + Typografie + Fonts; `ComicTheme.kt` darauf umstellen (Altfarben entfernen).
2. Komponenten aus Abschnitt 4.
3. HUD-Gerüst: Topbar, Rail, Kartenansicht-Umschalter, Dock + Optionen-Popover, Seitenfenster-Slot.
4. Fenster nacheinander auf `PanelScaffold` umbauen.
5. Hauptmenü + Laden.
6. Rechenzeit/FPS in Debug-Overlay verschieben.

Alle Texte bleiben über die bestehende i18n (`i18n/`, `lang/`) – die Entwürfe zeigen nur die deutschen Strings.
