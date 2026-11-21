# Design-Review 10 – Stand nach REVIEW-9

Grundlage: alle `*-day3-*`-Screenshots inkl. `game-maintenance-selected-day3` und `game-maintenance-detail-day3`, dazu `inspect-card.png`. REVIEW-9 ist bis auf Kleinigkeiten umgesetzt. **Das Layout ist damit fertig.** Offen sind ein Datenfehler, ein paar Zustände und ein Klicktest.

## Erledigt ✔
- **Achsen:**
  - Straßenzustand 50–100 % mit Endwerten 63/75/82 %.
  - Zufriedenheit 50–100 % mit Tagesmarken.
  - Parken mit rechter Achse für Falschparker.
  - „neu vs. behoben“ mit Werten.
- **Bauamt:**
  - „Alle auswählen“ im Tabellenkopf, Sammelleiste (Flicken / Schild 30 / Ausschreiben / Abbrechen).
  - Detail als Aufklappzeile mit Haltbarkeit und Dauer.
  - Verschleiß nach Ursache, schnellster Verfall, Kosten & Klagen mit Klagerisiko.
- **Bürger:** Spontandemo-Regel jetzt einheitlich, Pendler „Auto und Bus, gestern“, Links mit fester Spalte, Matrix mit Legende, Trend „gleich“.
- **Kalender:** Woche 1 von 2, Uhrzeit in den Blöcken, Chronik rechts, „Was kann passieren?“ unter dem Plan.
- **Erfolge:** „Fast geschafft“ nur noch mit Fortschritt > 0.

## 1. Parken: „Belegung Ø Tag“ stimmt nicht (Bug)
Die Tabelle „Welcher Preis bringt was?“ zeigt 0–2 % Belegung. Laut Diagramm darüber ist der Straßenrand aber tagsüber zu 90–95 % voll, im Mittel ≈ 45 %. Die KPI sagt 19 %.
- Vermutlich ein Faktor 100 oder die falsche Bezugsgröße (alle 20.499 Plätze statt nur Straßenrand). Die Zeile „0 € · jetzt“ muss ungefähr dem **Tagesmittel aus dem Diagramm** entsprechen.
- Gebühren/Tag danach gegenprüfen: Plätze × Belegung × Stunden × Preis.

## 2. Bauamt: Sammelaktion bei leerer Kasse
- Kasse 2.485 €, die Leiste bietet aber „Flicken 18.000 €“ und „Ausschreiben 337.500 €“ ganz normal an. → Knöpfe, die mehr kosten als die Kasse, **deaktivieren** und den Grund nennen. Zum Beispiel „Flicken 18.000 € – fehlen 15.515 €“, oder als Tooltip. Gleiches gilt für Einzel-Detail und Inspektor.
- Sind 12 von 42 Zeilen ausgewählt, zeigt die Kopf-Checkbox **„teilweise“** (Strich) statt leer.
- Die Sammelleiste verdeckt die letzte Zeile → Liste unten um die Leistenhöhe auffüllen.
- Zeilenhöhe ist ≈ 62 dp, gewünscht 46 dp (REVIEW-9 Punkt 4).
- „Verschleiß nach Ursache: Pkw & Lieferwagen 0 %“. Bei Tausenden Pkw ist das unwahrscheinlich → Rechnung prüfen, sonst „< 1 %“.
- „neu vs. behoben“: Die X-Achse zeigt „5 11 17 23 5 11 17“ ohne Tag → Trennlinie bei Mitternacht mit „Tag 2“ / „Tag 3“, Einheit „Uhr“.

## 3. Reparaturpreise: Inspektor vs. Bauamt
Im Inspektor (Goethestraße) kostet Ausschreibung 25.000 € und Turbo 75.000 €, im Bauamt-Detail (Östliche Friedrichstraße, 2 Abschnitte) 31.250 € und 93.750 €. Wenn der Preis von der Länge abhängt, muss man das sehen: „31.250 € · 2 Abschnitte“ bzw. „je Abschnitt 15.625 €“.
- Inspektor-Optionskarten: „Turbo“ bricht auf 3 Zeilen um, die Karten sind unterschiedlich hoch. Gleiche Höhe, Zusatz kürzer („75.000 € · schnell · kurzlebig“).

## 4. „Heute“ um 00:00 (aus REVIEW-8, noch offen)
ÖPNV um 00:00 von Tag 3: „Pünktlich 88 % – Busse, heute“ und „Halte bedient 38.354 – heute“. Um Mitternacht kann „heute“ noch keine Daten haben.
- **Regel für alle Seiten:** Wenn der laufende Tag noch < 1 Std. alt ist (oder 0 Ereignisse hat), den Wert von **gestern** zeigen und so beschriften („gestern“). Betrifft auch Kämmerei („Einnahmen heute“ ist korrekt, weil um 00:00 gebucht wird) und Einsätze („Heute 0“ ist korrekt). Nur Durchschnitte und Quoten umstellen.

## 5. Kleinigkeiten
- **Kalender:** Vergangene Tage (Mo, Di) sehen aus wie kommende → blass/grau hinterlegen. Dünne Stundenlinien alle 3 Std. im Raster.
- **Parken:** Im Belegungsdiagramm fehlt Tag 3 (es ist 00:00, okay). Senkrechte Mitternachtslinie wie bei Bürger.

## 6. Klicktest (bitte einmal von Hand)
Bisher wurden nur Screenshots geprüft. Vor der nächsten Runde einmal durchklicken und Auffälliges notieren:
1. Rail: jede Seite öffnen, **M** und „Zur Karte“ führen zurück, Esc schließt Tagesabschluss und Detail.
2. Bauamt: Filter-Chips, „Alle auswählen“, Sammelaktion Schild 30 ausführen → Status und Zähler aktualisieren sich, Kasse sinkt, Buchung erscheint in der Kämmerei.
3. Posteingang: Auftrag „Zur Stelle“ → Karte springt hin. „Ablehnen“ → Eintrag verschwindet, Zähler und Abzeichen passen.
4. Bürger „Zeigen“ / „ÖPNV →“, Kalender „Woche 2“, Erfolge-Filter.
5. Karte: Ebenen umschalten, Bauleiste Popover per Taste 1–9, Toast-Aktion „Ansehen“.
6. Touch-Ansicht (2340×1080): dieselben Schritte 1–3.

## Reihenfolge
1 → 2 → 4 → 3 → 5, dann 6. Danach brauche ich nur noch einen Durchgang mit den Screenshots, an denen sich etwas geändert hat, plus den Notizen aus dem Klicktest.
