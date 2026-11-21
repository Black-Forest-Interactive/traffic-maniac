# Traffic Maniac – Entwurf 2 „Lagezentrum“

Ersetzt die Layout-Teile von `DESIGN.md` (Farben, Schriften, Komponenten von dort gelten weiter).
Entwürfe: `docs/design/mockups/v2/` – **Referenzauflösung 1920 × 1080, 1 px im Entwurf = 1 dp bei `uiScale = 1.0`.**
Maße also 1:1 übernehmen, nichts hochrechnen.

| Entwurf | Inhalt |
|---|---|
| `V2-Karte.dc.html` | Karte: Kartenansicht, Bauleiste mit Popover, Bauplan |
| `V2-Posteingang.dc.html` | Posteingang als Vollbildseite |
| `V2-Ordnungsamt.dc.html` | Ordnungsamt mit Statistiken |
| `V2-Parken.dc.html` | Parken & Politessen mit Statistiken |
| `V2-OePNV.dc.html` | ÖPNV: Linienliste + Netzkarte mit Verspätung je Abschnitt |
| `V2-Einsaetze.dc.html` | Einsätze: laufender Einsatz, Fahrzeuge, Statistik |
| `V2-Presse.dc.html` | Presse & PR (ersetzt „Haushalt & PR“), Zeitungsarchiv |
| `V2-Buerger.dc.html` | Bürger mit Verlauf, Gründen, Bedürfnis-Matrix, Brennpunkten |
| `V2-Kalender.dc.html` | Kalender als Wochenplan + Ereignisarten |
| `V2-Erfolge.dc.html` | Erfolge als Raster mit Fortschritt |
| `V2-Kaemmerei.dc.html` | neues Amt **Kämmerei** (Finanzen) |
| `V2-Bauamt.dc.html` | Muster für alle Ämter mit Listen: Tabelle + Detail |
| `V2-Zeitung.dc.html` | Tagesende: Zeitung + Bilanz, **schließbar** |

## Was am aktuellen Stand falsch ist (Anlass)

1. **Nichts hat einen festen Platz.** Aufträge, Planungsleiste, Tempo-Optionen und Dock liegen übereinander; Listen werden vom Dock abgeschnitten.
2. **Zwei Konkurrenz-Spalten neben der Karte** (Aufträge links + Amt rechts) – die Karte bleibt ein Schlitz.
3. **Ämter als schmale Seitenleiste** → endloses Scrollen („… und 18 weitere“), keine Übersicht, keine Sammelaktionen.
4. **Topbar 250 px hoch**, Abwahlindex wird Buchstabe für Buchstabe senkrecht gesetzt, 8 Tempo-Knöpfe.
5. Zeitung am Tagesende blockiert ohne Ausweg.

## Grundregeln

1. **Feste Zonen, nichts überlappt.** Topbar 56 dp · Rail 80 dp · Inhalt = Rest (1840 × 1024 dp).
2. **Die Karte ist nur ein Menüpunkt.** Rail-Eintrag „Karte“ (M) zeigt die Karte vollflächig. **Alles andere** – Posteingang, Kämmerei, alle Ämter, Presse, Bürger, Kalender, Erfolge, Hilfe – ist eine **Vollbildseite** anstelle der Karte. Nie Karte + Seitenpanel gleichzeitig. Spiel läuft weiter (Tempo in der Topbar).
3. **Auf der Karte schwebt nur:** Kartenansicht (oben links), Lage + Posteingang-Hinweis (oben rechts), Zoom (rechts Mitte), Bauleiste + Werkzeug-Popover (unten Mitte), Bauplan (unten rechts, nur wenn etwas geplant ist), Inspektor am angeklickten Objekt.
4. **Posteingang ist eine eigene Seite** (Kategorien links, Liste Mitte, Detail rechts mit direkten Aktionen). Auf der Karte nur ein kleiner Hinweis-Kasten mit Zählern, Klick/I öffnet die Seite.
5. **Größen:** Text 11/12/13/14/16/20/32 sp, Zahlen in JetBrains Mono. Klickflächen mind. 36 dp in dichten Listen, sonst 40–48 dp. Topbar-Elemente 40 dp hoch.
6. **Karte entrümpeln:** Tempolimits ab mittlerem Zoom als farbiges Band auf der Straße + **ein** Schild pro Abschnitt; bei weitem Zoom Cluster („30“ mit Zähler). Nie ein Schild pro Segment.

## Topbar (56 dp, `Ink`)

`Ampel-Logo · Wochentag/Tag x von y + Uhrzeit (Mono 18) · Tempo [⏸ 1× 2× 4× 8× 64×] · [Hinweis „gebremst · zurück auf 64×“ nur wenn auto-gebremst] · — · Kasse · Reputation · Bürgerzorn · Abwahlindex (150 dp, horizontaler Meter) · | · Speichern · Menü`

- 16× und 32× entfallen (oder hinter Rechtsklick auf 64×). Tasten 1–5.
- Kasse < 0: Chip-Hintergrund `#3A1F28`, Wert `#FF9AA2`, daneben „heute ±x“.
- **Bug:** Abwahlindex-Label nie in schmalen Container zwingen (heute bricht jeder Buchstabe um) – feste Breite 150 dp.

## Rail (80 dp, `Ink2`)

Karte (M) · Posteingang (I, Badge) · *Rathaus*: Kämmerei (K), Ordnung (O), Bauamt (B, Badge = ungesichert), Parken (P), ÖPNV (N), Einsätze (R, Badge = laufend), Presse (Z), Bürger (U), Kalender (C) · Abstand · Erfolge (J), Hilfe (F1).
„Blitzer-Statistik“ und „Haushalt & PR“ entfallen als eigene Punkte: Blitzer-Statistik steckt im Ordnungsamt, Geld in der Kämmerei, PR in „Presse“.
Eintrag 58–62 dp hoch, Icon 22 dp, Label 11 sp einzeilig (kurze Labels: „Ordnung“, „Presse“). Aktiv = `Active` gelb. Tooltip mit Taste.

## Posteingang (Vollbildseite – `V2-Posteingang.dc.html`)

- Spalten: Kategorien 240 dp (Alle, Einsätze, Aufträge, Schäden, Meldungen, Erledigt mit Zähler) · Liste · Detail 560 dp.
- Listenzeile 72 dp: Farbstreifen nach Typ, Typ-Zeile, Titel, Unterzeile, rechts Belohnung + Frist.
- **Detail eines Auftrags:** Kartenausschnitt + „Auf Karte zeigen“, Zitat, Frist / Ø-Tempo vs. Limit / Kosten fürs Ablehnen, **Ziel-Optionen mit direkter Aktion** („Polizeikontrolle bestellen · 1.200 €“, „Laser platzieren · 1.500 €/Tag“ → springt auf die Karte mit vorgewähltem Werkzeug), Ablehnen, „Später“.
- Option „Bei Dringendem Spiel bremsen“.

Die folgenden Karten-Typen gelten für die Listeneinträge:
- **Auftrag-Karte:** Zeile 1 „AUFTRAG · Absender“ + Belohnung grün rechts; Titel = Art · Straße; Zitat 1 Zeile; **Ziel-Box** („2 Std. Polizeikontrolle oder Laser“); Fuß: Restzeit + Balken, `Ablehnen` (Text) · `Zur Stelle` (primär, springt auf Karte + öffnet Inspektor).
  „Hin“ als Beschriftung ersetzen.
- **Einsatz-Karte:** roter Seitenstreifen, Einheiten als Badges (vor Ort grün / unterwegs gelb), `Zeigen`.
- **Schäden-Karte:** „26 offen · 6 ungesichert“, 3 dringendste, `Zum Bauamt`.
- **Protokoll** eingeklappt am Ende (alle übrigen Meldungen).

## Karte

- **Kartenansicht** (oben links, 272 dp, weiß, einklappbar, Taste V): Liste der Ebenen als Radio-Zeilen 38 dp mit Icon + Taste (V1–V5):
  Verkehrsfluss · **Verkehrsdichte** (Kfz/Std., einfarbig hell→dunkel) · **Buslinien** (Linien in Linienfarbe, Abschnitte nach Verspätung grün/gelb/rot, Haltestellen als Punkte – wie die Schäden) · Straßenzustand · Zufriedenheit · Luft · **Geld**. Darunter **Legende der aktiven Ebene** (Farbverlauf + 3 Beschriftungen) und aufklappbar „Einblenden“ (Schilder & Ampeln, Blitzer & Kontrollen, Unfälle & Schäden, Aufträge, Bürger-Stimmung).
  Ersetzt die Segment-Leiste oben in der Mitte (die Ebenen hatten keine Legende und verdeckten die Karte).
- **Oben rechts:** Lage-Zeile (Kfz · Ø km/h · Staus) + Posteingang-Hinweis (je Typ eine Zeile mit Farbpunkt).
- **Inspektor** (Werkzeug Ansehen): 280 dp am angeklickten Objekt.
- Zoom rechts Mitte inkl. „Ganze Stadt“ (Pos1).

## Bauleiste (unten Mitte)

- Nur noch **Icon + Name**, Kachel 76 × 60 dp, Preise und Tasten im Tooltip/Popover. Reihenfolge: Ansehen | Tempo · Ampel · Kontrolle · Umleitung · Sperrung | Abbauen (Icon rot).
  Punkt oben rechts = hat Varianten. Aktiv = gelb.
- **Werkzeug-Popover** über der Leiste, 680 dp, **Pfeil zeigt auf das aktive Werkzeug**. Titel + Bedienhinweis, darunter:
  - Tempo: Schilder Schritt/10–70 (42 dp rund, gewählt 4 dp roter Ring), Original, nur nachts.
  - Werkzeuge mit Varianten: 4 Karten (Icon, Name, Preis, ein Satz Wirkung, Taste 1–4), gewählt = blauer Rahmen.
    Kontrolle: Blitzer, Laser, Anhänger, Polizei · Ampel: Ampel, Grüne Welle, Bedarfsampel, Gelbes Blinken · Umleitung: Umleitung, Einbahnstraße, Vorfahrt, Radweg-Querung · Sperrung: Vollsperrung, LKW-Verbot, Anlieger frei, Baustelle.
    *(Die Varianten sind Vorschläge – an die echten Werkzeuge anpassen.)*
- Esc = zurück zu Ansehen, Popover schließt.

## Bauplan (unten rechts, 340 dp)

Erscheint, sobald etwas geplant ist. Liste der geplanten Maßnahmen (Name, Ort, Kosten, ✕ zum Entfernen), Summe „Sofort“, „Kasse danach“ (rot wenn < 0), `↶` · `Verwerfen` · `Bauen ↵`. Geplante Dinge auf der Karte gestrichelt blau.

## Amtsstube (Muster für alle Ämter)

Kopf: Eyebrow „Rathaus“, Titel 32 sp, rechts Zusatzschalter + `Zur Karte M`.
Darunter **KPI-Kacheln** (Zahl Mono 26 sp), dann Inhalt in 12-Spalten-Raster, 16 dp Abstand, Karten weiß Radius 14.

### Kämmerei (neu) – `V2-Kaemmerei.dc.html`
Zeitraum: Heute · 24 Std. · Amtszeit.
- **KPIs:** Kassenstand (+ Sparkline 24 h, rot umrandet wenn < 0, „im Minus seit …“), Einnahmen heute, Ausgaben heute, Saldo heute (+ Kasse bei Tagesbeginn), Prognose 24 Uhr.
- **Geldfluss pro Stunde:** Balken nach oben = Einnahmen (`#2F6BD0`), nach unten = Ausgaben (`#D9701F`), eine Achse; Ausreißer werden mit Bruchmarke gekappt und beschriftet („17 Uhr · −32.000 € · Ampel Bahnhofplatz“). Hover-Tooltip je Stunde, Knopf „Als Tabelle“. Kein zweites y-Achsen-Diagramm für den Kassenstand (der steht als Sparkline in der KPI).
- **Woher / Wohin:** horizontale Balken je Posten. Einnahmen: Blitzer-Bußgelder, Laser, Knöllchen, Aufträge, Parkgebühren. Ausgaben: Baumaßnahmen, Personal, Reparaturen, PR-Agentur, Klagen, Laser-Miete.
- **Wo?:** Top-Einnahmeorte (Ort · Art · Betrag · Anteil) + größte Ausgaben; Klick = Karte mit Ebene „Geld“.
- **Buchungen:** Journal (Zeit · Buchung · Ort · Betrag), Filter Alle/Einnahmen/Ausgaben, Einzel-Bußgelder stündlich gebündelt.
- **Feste Kosten / Woche:** Personal, PR, Laser … Summe vs. Haushalt (+90.000 € sonntags).
- **Datenmodell:** jede Geldbewegung als `Booking(time, amount, category, placeId?, text)` speichern; alles oben ist eine Aggregation darüber.

### Ordnungsamt & Bußgeldstelle – `V2-Ordnungsamt.dc.html`
Links Statistik, rechts feste Spalte „Steuerung“ (400 dp).
- **KPIs:** Fotos 24 Std. (vs. gestern), Bußgelder bezahlt (+ Amtszeit, Ø je Fall), Aufklärungsquote (Meter), Aktenstapel (+ Trend/Tag, gelb umrandet), ältester Fall (vs. Verjährung).
- **Weg eines Falls:** Geblitzt → Im Stapel → Bearbeitet → Bezahlt mit Anzahl + Anteil; darunter Eingestellt / Verjährt / Fahrverbote. **Kapazitäts-Hinweis**: Sachbearbeiter × Fälle/Std × Std vs. Eingang/Tag → wann ist der Stapel weg / wann verjährt etwas.
- **Fotos pro Stunde** (24 Balken, Spitze markiert, Tooltip).
- **Wie viel zu schnell?** Balken je Klasse (1–10, 11–20, 21–30, >30 km/h) mit Bußgeldsatz; Hinweis zur Wirkung der Schärfe.
- **Standorte:** Ort · Art (Blitzer/Laser/Anhänger) · bezahlte Fälle · Einnahmen · Bekanntheit in Warn-Apps (Meter) · Ø-Tempo-Effekt; Klick = Karte.
- **Steuerung:** Sachbearbeiter-Stepper (+ Hinweis „+50 in Einarbeitung · fertig Donnerstag“), Bearbeitungsfrist, Schärfe (mit Wirkung), Fahrer nicht ermittelbar (Radio mit Folge), Warn-Apps (Anteil + Polizeiaktion / Blitzermarathon).
- Daten dafür: je Foto `(time, siteId, overKmh, state)`, Zustandswechsel mit Zeitstempel.

### Bauamt – `V2-Bauamt.dc.html` (Muster für Listen-Ämter)
- **Statistik-Zeile (4 Karten):**
  1. Straßenzustand Ø + Verlauf seit Tag 1 + **Prognose bis Szenarioende je Erhaltungsbudget** (keins/Nötigste/vorbildlich als gestrichelte Linien, gewählte farbig) – das Budget-Segment sitzt direkt darunter und zeigt den Zielwert.
  2. Offene Schäden (ungesichert / mit Schild / in Reparatur) + Balken „neu vs. behoben je 6 Std.“.
  3. Verschleiß nach Ursache (LKW, Pkw, Busse, Frost & Regen) + Straßen mit dem schnellsten Verfall.
  4. Kosten Amtszeit (Schilder, Flicken, Erhaltung) + Klagen + Klagerisiko solange ungesichert.
- **Tabelle** (8 Spalten): Filter-Chips, Suche, Sortierung; Zeilen 46 dp mit Checkbox · Straße · Schaden · Zustand-Meter · Status · Frist · **Verfall/Tag**; seitenweise.
- **Sammelaktion** bei Auswahl: Flicken · Schild Tempo 30 · Ausschreiben mit Gesamtpreis.
- **Detail** (4 Spalten): Reparatur-Optionen als Karten (Name, Haltbarkeit, Kosten, Dauer): Flicken 1.500 € sofort · Ausschreibung 25.000 € · Turbo 75.000 € · Planfeststellung 4.000 €; Absichern: Schild 30 / Schild 10 / Nichts tun.
- Daten: je Straße Zustand-Historie + Verschleißanteile nach Fahrzeugtyp.

### Parken & Politessen – `V2-Parken.dc.html`
- **KPIs:** Parkhäuser belegt (vs. gestern), Straßenrand belegt, Falschparker gerade + erwischt (Quote), Suchverkehr (Kfz, Ø Suchzeit, CO₂), Einnahmen (Knöllchen + Gebühren).
- **Belegung über die Zeit:** Parkhäuser, Straßenrand (%), Falschparker (gestrichelt, eigene Beschriftung) mit Markierung von Preisänderungen.
- **Welcher Preis bringt was?** Tabelle €/Std. → geschätzte Belegung + Gebühren/Tag; aktueller Preis rot, bester grün.
- **Politessen:** Stepper, Knöllchen je Kopf/Tag, Ø je Knöllchen, Kosten vs. Ertrag/Woche, Lohnt-sich-Hinweis.
- **Parkhäuser-Tabelle:** Name · Plätze · Belegung jetzt (Balken) mit Strich für gestern · Preis-Stepper · Gebühren 24 h · Umwandeln/Entfernen. Darüber „Alle auf …“-Segment.
- **Straßenrand:** Automatenpreis (Segment), Brötchentaste und Anwohnerparken als **Schalter mit Erklärung** (beide gleichzeitig möglich – heute sehen sie aus wie zwei ausgewählte Segmente), Anteil Parkplätze + Blumenkübel-Aktion.


## Meldungen (Toasts) auf der Karte

- **Unten links**, max. 3 gestapelt, 380 dp breit – nie oben über der Ebenen-Auswahl oder mitten auf der Karte.
- Aufbau: Farbstreifen + rundes Icon nach Typ (Problem rot, neu/Auftrag blau, Erfolg grün), Titel kurz, Unterzeile = Ort + Folge, optional **eine** Aktion („Ansehen“), ✕, dünner Zeitbalken (verschwindet nach ~6 s, bei Hover pausiert).
- Titel nicht in der Unterzeile wiederholen (heute: „Auftrag verpasst: Raser-Beschwerde“ + „Auftrag verpasst: Raser-Beschwerde – Bahnhofstraße“).
- Alles landet zusätzlich im Posteingang; Link „Alle Meldungen im Posteingang →“ unter dem Stapel.

## Weitere Seiten (alle gleiches Muster: KPIs oben, Inhalt im 12er-Raster)

### Kämmerei – Ergänzung
„Feste Kosten“ ersetzt durch **Haushaltsrunde** (jede Nacht 24 Uhr, je ⅐ der Wochenbeträge): Haushalt, Anwohnerausweise, PR-Agentur, Kampagne, Gehälter, Erhalt, Saldo, Countdown zur nächsten Runde. „Einnahmen bisher“ aus dem alten Haushalt-Fenster steckt in „Woher kommt das Geld?“.

### Ordnungsamt – Ergänzung (aus Blitzer-Statistik)
„Wie viel zu schnell?“ zeigt je Klasse **Anteil Fotos und Anteil Geld**; Rekord in der Kopfzeile; Standorte-Tabelle enthält auch **Anzeigetafeln** (ohne Fotos, mit Tempo-Effekt).

### Presse & PR – `V2-Presse.dc.html`
KPIs: Reputation, Ton der Zeitung, „Story wirkt“ (%), Aufschlag für die Lücke, PR-Kosten/Woche. Diagramm **Bild in der Zeitung vs. Zufriedenheit** mit schraffierter Lücke. PR-Agentur + Kampagne + **Anlässe** (Spatenstich, Grußwort …). **Zeitungsarchiv** (jede Ausgabe mit Schlagzeile, Ton, Ruf-Effekt, öffnet die Zeitung). Themen der Artikel mit Stimmung.

### Bürger – `V2-Buerger.dc.html`
KPIs: Unzufrieden (Balken mit 43-%-Schwelle), Bürgerzorn + Trend, Spontandemos + Risiko, Pendler pünktlich, Abwahl-Prognose. **Zufriedenheit je Haushaltstyp über die Zeit** (3 Linien, Zorn-Zone markiert). **Warum sauer?** (Gründe mit Sprung ins zuständige Amt). **Was fehlt wem?** Matrix Haushaltstyp × Bedürfnis (Farbskala schlecht→gut). **Brennpunkte**: Gebiet, Zufriedenheit, Hauptgrund, Einwohner, Trend, Zeigen.

### Kalender – `V2-Kalender.dc.html`
„Jetzt“-Karte, Rad-Korso-Zähler (Dauerstau-Tage), Demo-Risiko/Std., Bilanz. **Wochenplan** (Mo–So, 6–24 Uhr; vergangen, heute, nach Szenarioende schraffiert) mit Ereignissen nach Art: geplant / Störung / möglich (gestrichelt) / eigene Termine. **Was kann passieren?** (Auslöser + Folge je Ereignisart). Chronik als Zeile unten.

### ÖPNV – `V2-OePNV.dc.html`
KPIs: pünktlich heute, Busse, Halte, Autos stehen gelassen, Sorgenkind-Linie. Links **Linien** (Badge in Linienfarbe, je Richtung Pünktlichkeitsbalken + Ø-Verspätung, unpünktlichste oben). Rechts **Netzkarte**: alle Linien gedämpft, gewählte Linie hervorgehoben, Abschnitte nach Verspätung gefärbt, Ursache als Label („Stau Zerrennerstraße +1:20“). Darunter **Verspätung an jedem Halt**. Die Maßnahmen-Knöpfe (Busvorrang, Busspur, Umleitung) sind Vorschläge.

### Einsätze – `V2-Einsaetze.dc.html`
KPIs: laufend, heute, Ø Anfahrt (+ Einfluss Tempo 30), Stau durch Einsätze, Rettungsgasse %. **Laufender Einsatz** als Zeitstrahl (gemeldet → vor Ort → unterwegs → frei) + Kartenausschnitt + Aktionen. **Fahrzeuge im Dienst** je Art als Kästchen (im Einsatz / unterwegs / frei). Erledigte Einsätze + Häufigkeit nach Art. Leerer Zustand: „Alles ruhig“ + trotzdem Fahrzeuge & Statistik zeigen.

### Erfolge – `V2-Erfolge.dc.html`
Fortschritt x/20, **Fast geschafft** (3 mit Balken), Filter Alle/Erreicht/Offen, Raster 5 Spalten: erreicht = weiß + gelbe Medaille + Tag; offen = grau gestrichelt + Schloss + Fortschritt falls zählbar.

## Tagesende / Zeitung – `V2-Zeitung.dc.html`

Modal 1280 × 860 über abgedunkeltem Spiel, **Spiel pausiert**.
- Links die Zeitung („Goldstadt-Bote“, Serif: Playfair Display + Source Serif 4), rechts die Tagesbilanz (Einnahmen/Ausgaben/Saldo, Kasse, Reputation/Zorn/Abwahl alt → neu mit Delta, Ton der Zeitung, Aufträge).
- **Schließen:** X oben rechts (44 dp), `Esc`, Klick auf den abgedunkelten Rand, Knopf „Schließen“.
  **Weiter:** `Weiter zu Tag 3 ↵` (Enter). Zusätzlich „Zur Kämmerei“.
- Nach dem Schließen läuft der neue Tag erst mit Leertaste/Weiter los. Jede Ausgabe landet in **Presse → Archiv**.

## Hauptmenü
Neuer Stand ist gut. Nur: Kacheln dürfen nicht unter der Aktionsleiste verschwinden (Liste scrollt **über** der Leiste, unten 24 dp Luft); „Weiterspielen“ grün ist ok (einziges Grün = „Fortsetzen“).

## Reihenfolge
1. Layout-Gerüst (Zonen, Topbar-Fix, Rail) · 2. Dock-Block inkl. Planung · 3. Posteingang · 4. Zeitung schließbar · 5. `Booking`-Journal + Kämmerei · 6. Amtsstube-Muster, Bauamt · 7. übrige Ämter · 8. Karten-LOD für Schilder.
