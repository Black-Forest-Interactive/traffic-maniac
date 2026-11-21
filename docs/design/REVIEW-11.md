# Design-Review 11 – Abschluss

Grundlage: die 7 geänderten Screenshots (Parken, Bauamt ×3, ÖPNV, Kalender, Inspektor). REVIEW-10 ist umgesetzt. **Damit ist der Entwurf v2 abgenommen**, offen sind nur noch die drei Punkte unten.

## Erledigt ✔
- **Parken:**
  - Die Belegung in „Welcher Preis bringt was?“ passt jetzt zum Diagramm (0 € → 42 %).
  - Rechenweg steht darunter, Umschalter Straßenrand / Parkhäuser.
- **Bauamt:**
  - Kopf-Checkbox zeigt „teilweise“.
  - Zu teure Aktionen sind gesperrt mit „fehlen x €“, in Sammelleiste, Detail und Inspektor.
  - „Preise für 2 Abschnitte (+25 % je weiterem)“.
  - Pkw „< 1 %“ mit Erklärung (Achslast hoch 4).
  - „neu vs. behoben“ mit Tag/Uhr-Achse.
- **Inspektor:** Optionskarten gleich hoch, Preise ohne Umbruch.
- **ÖPNV:** Um 00:00 „gestern“ an Pünktlich, Halte, Umsteiger und Linien.
- **Kalender:** Vergangene Tage grau, Stundenlinien, Legende mit „vergangen“.

## Noch offen
1. **Bauamt-Zeilenhöhe:** weiterhin ≈ 62 dp (seit REVIEW-9 zweimal angemerkt). Bitte auf 46–48 dp; Checkbox und Text bleiben gleich groß, nur weniger Innenabstand. Falls 62 dp bewusst so gewählt sind (z. B. Touch-Ziel), kurz notieren, dann ist der Punkt erledigt.
2. **Sammelleiste:** Prüfen, dass man bis zur letzten Zeile scrollen kann. Im Screenshot verdeckt die Leiste die „Westtangente“. Unten Abstand in Leistenhöhe + 16 dp.
3. **Spielbalance Parken (keine Designfrage):** Laut Schätzung bringen 2 €/Std. am Straßenrand ≈ 60.000 €/Tag, das ist das Siebenfache der täglichen Haushaltsrate (8.571 €). Gerechnet wird mit 24 bezahlten Stunden. Üblich wäre Gebührenpflicht etwa 8–20 Uhr. Prüfen, ob das gewollt ist. Sonst ist Parken der eine Hebel, der alle Geldprobleme löst.

Ein **Klicktest** (REVIEW-10 Punkt 6) steht noch aus. Notizen davon bitte als `docs/design/KLICKTEST.md`, dann sehe ich sie mir an.

## Stand der offenen Punkte (21.11.)
1. **Zeilenhöhe – bewusst so, erledigt:** Die Zeilen sind 46 dp hoch. Die Screenshots entstehen bei 1920 px Breite, dort skaliert die Oberfläche automatisch um 1920 / 1440 = 1,33 (Entwurfsbreite 1440 px, `ScaledUi`). 46 dp ergeben dadurch ≈ 61 px – das sind die gemessenen „62 dp“. Bei 1440 px Breite sind es 46 px.
2. **Sammelleiste – erledigt:** Unter der Liste ist jetzt genau Leistenhöhe + 16 dp frei (die Leiste wird gemessen, kein fester Wert). Beleg: `game-maintenance-selected-end-day3` zeigt die Tabelle ganz unten, die letzte Zeile steht frei über der Leiste. Im Screenshot ohne Scrollen liegt die Leiste naturgemäß über den Zeilen darunter.
3. **Parken – teilweise:** Gebührenpflicht jetzt 8–20 Uhr, in der Simulation (bezahlt wird nur der Teil des Aufenthalts in dieser Zeit, `paidFromHour`/`paidUntilHour` in `balancing/simulation.json`) und in der Schätzung („Belegung Ø 8–20 Uhr“ × 12 Std.). Damit bringen 2 €/Std. am Straßenrand geschätzt ≈ 48.000 €/Tag statt ≈ 60.000 € – weiterhin gut das Fünffache der Haushaltsrate, weil der Straßenrand tagsüber im Mittel zu 68 % belegt ist. **Offene Balancing-Entscheidung:** niedrigere Zahlungsbereitschaft (`maxFeeTolerance`, derzeit 4 €/Std.), weniger gebührenpflichtige Plätze oder höherer Haushalt.

**Klicktest:** noch nicht gemacht – er muss von Hand erfolgen; `KLICKTEST.md` folgt erst mit echten Notizen.

---

## Festgehaltene Regeln (für neue Seiten und Änderungen)
Diese Regeln haben sich in den Reviews 2–11 ergeben. Sie gelten zusätzlich zu `DESIGN-v2.md`.

**Aufbau**
- Jede Seite: Eyebrow („Rathaus“ bzw. „Amtszeit“), Titel 32 sp, „Zur Karte M“ rechts. KPI-Reihe, darunter das 12er-Raster über die volle Breite. Kein ✕, keine Panels.
- KPIs nie in einer Karte darunter wiederholen.
- Leere Zustände: kurzer Satz + trotzdem Struktur zeigen (Achsen, Tabellenköpfe nur wenn Daten da sind). Keine Seite bleibt leer.
- Bei unterschiedlich hohen Spalten scrollt jede Spalte für sich, unten 24 dp Luft + Ausblendung als Scroll-Hinweis.
- Touch: KPIs als Chip-Zeile (horizontal scrollbar, Labels nie kürzen), Karten untereinander.

**Zahlen**
- Jeder Wert hat einen **benannten Zeitraum** (heute / gestern / letzte 24 Std. / Amtszeit), an der Karte oder in der Unterzeile.
- Alle Stufen eines Trichters oder einer Summe stammen aus demselben Zeitraum, Anteile ≤ 100 %, Summen der Teile = Gesamtwert.
- In der ersten Stunde eines Tages zeigen Quoten und Durchschnitte den Wert von gestern (beschriftet).
- Kein Wert ohne Daten: „–“ + „noch keine …“ statt 0 % oder 76 %.
- Kleine Anteile als „< 1 %“, nie „0 %“ bei einem Betrag > 0.
- Einheiten immer (€, %, km/h, min). Zeiten als `m:ss`, Geld mit Tausenderpunkt, Zahlen in JetBrains Mono.

**Farben**
- Gelb (`Active`) nur für „aktiv/jetzt“ im Spiel. Rot nur für Problem/Fehler/Kosten, nicht für neutrale Mengen. Neutrale Balken in `Primary`.
- Ampel-Schwellen einmal definieren und überall gleich nutzen (ÖPNV-Verspätung, Straßenzustand), mit kleiner Legende.
- Einnahmen `#2F6BD0`, Ausgaben `#D9701F`.

**Diagramme**
- Immer X- und Y-Achse mit Werten, Tageswechsel als senkrechte Linie mit „Tag n“.
- Y-Bereich an die Daten anpassen (z. B. 50–100 %), Endwerte von Prognosen an die Linien schreiben.
- Zweite Größe mit anderer Skala → rechte Achse in der Farbe der Reihe.

**Aktionen**
- Was mehr kostet als die Kasse, ist gesperrt und nennt den Fehlbetrag.
- Preise, die von etwas abhängen (Länge, Anzahl), nennen die Grundlage.
- Je Option: Preis · Dauer · Haltbarkeit/Wirkung in einer Zeile.
- Mehrfachauswahl: Kopf-Checkbox mit Zustand „teilweise“, Sammelleiste unten mit Gesamtpreis und Abbrechen.

**Texte**
- Deutsch, kurz, im Ton der Satire, aber Zahlen sachlich. „Erfolg“ statt „Achievement/Errungenschaft“.
- Titel nicht in der Unterzeile wiederholen. Gleichartige Meldungen bündeln („3× …“).
- Gleiche Begriffe auf allen Seiten (z. B. Ton der Zeitung: Verriss … Jubelpresse; Spontandemo-Schwelle überall gleich formuliert).
