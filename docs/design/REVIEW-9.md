# Design-Review 9 – Bauamt, Parken, Bürger, Kalender, Erfolge (Tag 3)

Grundlage: `game-{maintenance,parking,citizens,events,achievements}-day3-1920x1080.png`. Alle fünf Seiten entsprechen jetzt im Aufbau dem Entwurf. Was folgt, ist Feinschliff, vor allem an **Diagrammachsen** und zwei **Datenfragen**.

## Erledigt ✔
- **Bauamt:**
  - Tabelle mit Checkbox, Zustand, Status, Frist und Verfall/Tag, Filter-Chips mit Zählern.
  - Zustandsverlauf mit Prognose je Budget, das Budget-Segment zeigt das Ziel („keins → 63 %“).
  - Diagramm „neu vs. behoben“.
- **Parken:**
  - Belegung im Verlauf, „Welcher Preis bringt was?“, Politessen mit Kosten/Ertrag und Urteil.
  - Brötchentaste und Anwohnerparken als Schalter, Blumenkübel als normaler Knopf.
  - „Falschparker bisher“ statt „gerade“.
- **Bürger:**
  - Zufriedenheit im Verlauf mit Zorn-Zone, „Warum sauer?“ mit Sprung ins Amt, Matrix „Was fehlt wem?“.
  - Brennpunkte mit Straßennamen, Zorn-Trend und Pendler-Wert von gestern.
- **Kalender:** Wochenplan, kompakte Liste „Geplant“, „Was kann passieren?“ mit Risiko.
- **Erfolge:** volle Breite, 5 Spalten, „Fast geschafft“, Filter, Fortschritt bei offenen Erfolgen, Eyebrow „Amtszeit“.

## 1. Diagramme: Achsen fehlen
Betrifft Bauamt (Straßenzustand, neu vs. behoben), Parken (Belegung) und Bürger (Zufriedenheit).
- **X-Achse:** Tage markieren („Tag 1 · Tag 2 · Tag 3“, senkrechte Hilfslinie um Mitternacht), bei 6-Std.-Balken die Uhrzeit.
- **Y-Achse:** mindestens drei Werte (z. B. 0 / 50 / 100 %), dünne Hilfslinien.
- **Bauamt – Straßenzustand:** Der Verlauf ist ein winziger Strich oben links, ⅔ der Fläche sind leer. Y-Bereich an die Daten anpassen (z. B. 50–100 %), dann sieht man den Abstand zwischen den drei Prognosen. Endwerte rechts an die Linien schreiben (63 / 75 / 82 %).
- **Bürger – Zufriedenheit:** Ebenso Y-Bereich 50–100 % statt 0–100 %, dann ist die Zorn-Zone ein schmaler Streifen unten und die Linien sind unterscheidbar.
- **Parken:** Falschparker haben eine eigene Skala („bis 157“). Diese als **rechte Y-Achse** in Rot beschriften, statt nur in der Legende.

## 2. Daten prüfen
- **Parken – „Welcher Preis bringt was?“:** Alle Preise zeigen „Belegung 0 %“, aber Gebühren bis ≈ 10 €/Tag. Die Schätzung nutzt offenbar die **Belegung jetzt** (00:00, nachts leer). → Mit dem **Tagesmittel** (oder Spitzenstunde) rechnen. Spalte umbenennen in „Belegung Ø Tag (geschätzt)“.
- **Spontandemo-Schwelle widersprüchlich:** Bürger sagt „Erst ab 35 % Unzufriedenen“, Kalender sagt „Auslöser: Bürgerzorn ab 20“. → Eine Regel, an beiden Stellen gleich formuliert.
- **„Pendler pünktlich 28 %“** (Bürger) und **„Pünktlich 88 %“** (ÖPNV) liegen nebeneinander. Das sind verschiedene Größen (Auto + Bus vs. nur Bus), das sollte man sehen → Unterzeile „Auto und Bus, gestern“ bzw. im ÖPNV „Busse“.

## 3. Kalender
- **Farben im Wochenplan:** Do–So sind grau wie „nach Amtsende“, liegen aber innerhalb der Amtszeit (14 Tage). Drei Zustände trennen:
  - vergangen = blass
  - heute = blau hinterlegt
  - kommend = weiß
  - nach Amtsende = schraffiert (nur in Woche 2 nach Tag 14)
- **Jetzt-Linie:** Waagerechte Linie in `Active` bei der aktuellen Uhrzeit in der Spalte „heute“.
- **Wochen umschalten:** „‹ Woche 1 · Woche 2 ›“ über dem Raster (die Amtszeit hat 2 Wochen, die Liste „Geplant“ zeigt Tag 10 und 13).
- Ereignis-Blöcke: Unter dem Namen die Uhrzeit („7–13“) zeigen, sofern die Höhe reicht.
- **Ungleiche Spalten:** Links unter dem Wochenplan ist es leer, rechts läuft „Was kann passieren?“ aus dem Bild. → „Was kann passieren?“ unter den Wochenplan (volle Breite der linken Spalte, Ereignisse in 2–3 Spalten) und die **Chronik** rechts unter „Geplant“.

## 4. Bauamt – Tabelle
- Zeilenhöhe ist ≈ 72 dp → **46 dp**, damit 12–14 Schäden ohne Scrollen sichtbar sind.
- **„Alle auswählen“-Checkbox** im Tabellenkopf fehlt (wichtig für die Sammelaktion).
- Zustand-Balken sind bei allen orange → nach Wert färben (< 40 % rot, < 70 % orange, sonst grün), dieselben Schwellen wie in der Kartenebene Straßenzustand.
- Bitte einen Screenshot mit **ausgewählten Zeilen** (Sammelaktion) und einem **geöffneten Schaden** (Detail mit Reparatur-Optionen) nachreichen. Ebenso den unteren Teil der rechten Spalte (Verschleiß nach Ursache, Kosten & Klagerisiko). Den sehe ich noch nicht.

## 5. Kleinigkeiten
- **Bürger – „Warum sauer?“:**
  - Die Balken sind unterschiedlich lang, weil der Link rechts verschieden breit ist → feste Spalte für den Link (≈ 140 dp), auch leer reservieren.
  - Zeilenabstand halbieren.
  - „Frische Luft“ → Link „Luft-Ebene →“ (Karte mit Ebene Luft), „Ruhe“ → „Lärm …“, sofern vorhanden.
- **Bürger – „Was fehlt wem?“:** Alle Zellen sind gleich hellgrün, obwohl die Werte 95–100 % sind. Die Farbskala braucht mehr Abstufung im oberen Bereich (z. B. < 60 rot, 60–80 orange, 80–90 hellgrün, > 90 grün). Kleine Legende darunter.
- **Brennpunkte:** „37 % →“: Der Pfeil ist mehrdeutig. Als Trend ↗ ↘ → mit Farbe, oder weglassen.
- **Erfolge:** „Fast geschafft“ zeigt „Planungsweltmeister 0/3“. Nur Erfolge mit Fortschritt > 0 zeigen. Wenn weniger als 3, dann weniger Zeilen.

## Reihenfolge
2 → 1 → 3 → 4 → 5. Für die nächste Runde: Bauamt mit Auswahl und geöffnetem Schaden plus die rechte Spalte bis unten (`game-maintenance-selected-day3`, `game-maintenance-detail-day3`).
