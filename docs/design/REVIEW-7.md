# Design-Review 7 – Stand nach REVIEW-6

Grundlage: Screenshots vom 21.11. (fünfte Runde), erstmals mit Spielstand **Tag 3** (`*-day3-*`). Mit echten Daten zeigen sich vor allem **Zahlen-Fehler**. Reihenfolge = Priorität.

## Erledigt ✔
- **Kämmerei:**
  - Startausstattung wird getrennt verbucht („Start“), Topbar und KPIs passen zusammen.
  - Diagramm mit Achsen (±, 0/6/12/18 Uhr) und Summen in der Legende.
  - „Wohin geht es?“ nach Art aufgeteilt, „Woher kommt das Geld?“ mit allen Posten.
  - Haushaltsrunde mit Countdown und Prognose, Buchungen mit Ort.
- **Presse & PR:**
  - Doppelte Karte entfernt, „Anlässe“ mit normalem Knopf.
  - Wirkung und Kostenhinweis bei den PR-Optionen.
  - Ton-Verlauf und Schlagzeilen-Archiv, die mit Daten (Tag 3) sehr gut aussehen.
- **Posteingang (Tag 3):** Aufträge mit Belohnung, Frist, Fortschritt, Ziel und „Zur Stelle“ / „Ablehnen“. Genau so war es gedacht.
- **Einsätze (Tag 3):** Häufigkeit nach Art und letzte Einsätze mit Ort.
- **ÖPNV (Tag 3):** Sorgenkind hervorgehoben, Verspätung je Halt mit Balken, Netzkarte gut beschriftet.

## 1. Ordnungsamt: Zeiträume vermischt (Bug)
`game-office-day3`: Im Trichter „Weg eines Falls“ steht **Bearbeitet 61 = 2033 %** und **Bezahlt 44 = 1467 %**. „Geblitzt 3“ bezieht sich offenbar auf 24 h, die übrigen Zahlen auf die Amtszeit. Ebenso: KPI „Fotos bisher 3“, aber „Bußgelder bezahlt 44“.
- **Alle Stufen des Trichters und alle KPIs im selben Zeitraum zählen** (Amtszeit). Anteile beziehen sich immer auf „Geblitzt“ und können nie über 100 % liegen.
- Die Prozentspalte bricht um („2033 / %“) → Mindestbreite, `maxLines = 1`.
- „Fotos pro Stunde“: Die Balken sind rot und blau, ohne Legende. Entweder eine Farbe oder eine Legende (z. B. rot = Rotlicht, blau = Tempo). Text: „Spitze: 1 Foto“ (Singular).
- „Ältester Fall 0 Tage“ bei 1 Fall im Stapel → „unter 1 Tag“.

## 2. Kämmerei: Zeitraum je Karte benennen
Auf einer Seite stehen drei Zeiträume ohne Kennzeichnung: KPIs = heute, Diagramm = letzte 24 h, „Woher/Wohin“ = Amtszeit.
- Am Kopf jeder Karte ein Hinweis in grau: „heute“, „letzte 24 Std.“, „Amtszeit“. Oder besser: ein Umschalter **Heute · 24 Std. · Amtszeit** oben rechts unter dem Titel, der für alle Karten gilt.
- **Daten-Bug:** „Wo wird verdient?“ zeigt nur „Westliche Karl-Friedrich-Straße 90 €“, aber „Blitzer-Bußgelder 3.195 €“. Die Summe der Orte muss der Summe der Blitzer-Bußgelder entsprechen. Knöllchen ggf. als eigener Ort bzw. eigene Zeile „Ordnungsamt (Knöllchen)“.
- KPI „Bußgelder bisher 3.195 €“ enthält keine Knöllchen (3.975 €) → Label „Blitzer-Bußgelder“ oder beide zusammen zählen.
- „Haushalt – heute Nacht +8.571 €“ steht auch dann noch, wenn die Rate gerade gebucht wurde (Tag 3, 00:00). → „nächste: morgen 00:00“.
- „Sonstiges 12.700 €“ ist größer als einige benannte Posten. Den größten Einzelposten daraus als eigene Zeile zeigen.

## 3. Topbar: „bis Ereignis“ fehlt an Tag 3
Auf allen `*-day3-*`-Screenshots fehlt der Knopf „bis Ereignis ∨“ hinter 64×. Falls er absichtlich ausgeblendet wird (z. B. um 00:00 oder solange der Tagesabschluss offen ist): deaktiviert anzeigen statt ausblenden, damit die Topbar nicht springt.

## 4. ÖPNV: Einheitliche Farben und Formate
- **Farben:** Die Liste nutzt grün/orange/rot, „Verspätung an jedem Halt“ nutzt hellgrün/gelb. Dieselben Schwellen und Farben verwenden (z. B. ≤ 1 min grün, ≤ 3 min orange, darüber rot) und die Schwellen einmal als Mini-Legende zeigen.
- **Format:** Beim Sorgenkind „Ø +213 s“, sonst „+3:33“ → überall `m:ss`.
- „Halte bedient 38.354“: Zeitraum fehlt (heute? Amtszeit?) → Unterzeile „Amtszeit“ bzw. „heute“.

## 5. Posteingang
- **Doppelte Anführungszeichen** im Auftragstext: `„„Nachts wird … gerast.“ Zwei Stunden …“` → das äußere Paar entfernen (Text kommt schon mit Anführungszeichen).
- 42 Schäden füllen die Liste. Gleiche Schäden je Straße bündeln oder als **eine** Zeile „42 Schlaglöcher gemeldet → Bauamt“ mit Link zusammenfassen. Die einzelnen Schäden stehen ohnehin im Bauamt.
- Rail-Badges: Posteingang 45 und Bauamt 42 zählen dieselben Schäden doppelt. Posteingang-Badge = **ungelesene** Einträge, nicht alle offenen.

## 6. Kleinigkeiten
- **Einsätze:**
  - „Häufigkeit nach Art“ ist komplett rot. Rot bedeutet bei uns Gefahr/Fehler, deshalb neutral in `Primary`.
  - „Letzte Einsätze“: Die Spalte „24 min“ hat keine Überschrift → „Dauer“, dazu Tag und Uhrzeit.
- **Presse:** Die KPI „Ton der Zeitung“ zeigt „Verriss“, die Skala im Verlauf aber „bissig – neutral – freundlich“. Dasselbe Vokabular verwenden und die Stufe auf der Skala beschriften. Den Hinweis „Keine Wirkung – …“ nicht in Link-Blau, sondern in `Warn` oder `Muted`.
- **Toasts:** „Errungenschaft: Es blitzt“ → „Erfolg: Es blitzt“, wie in Navigation und Posteingang.
- **Tagesabschluss:** Die Zeitung ist jetzt kürzer als das Fenster, darunter ≈ 150 dp weiß. Fensterhöhe an den Inhalt anpassen (Zeitung bestimmt die Höhe, mindestens so hoch wie die Bilanz).
- **Spielbalance prüfen (keine Designfrage):** An Tag 3 liegen Bürgerzorn und Abwahlindex bei 0, obwohl es zwei Verrisse und 24.595 € Schadensersatz gab. Bitte prüfen, ob das gewollt ist.

## Reihenfolge
1 → 2 → 3 → 5 → 4 → 6. Danach wieder das Screenshot-Set inkl. `*-day3-*`. Gern zusätzlich die Seiten Bauamt, Parken, Bürger, Kalender und Erfolge mit Tag 3, die habe ich seit dem neuen Entwurf noch nicht gesehen.
