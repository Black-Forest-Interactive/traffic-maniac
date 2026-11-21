# Design-Review 8 – Stand nach REVIEW-7 + die fünf restlichen Seiten

Grundlage: Screenshots vom 21.11. (sechste Runde), erstmals Bauamt, Parken, Bürger, Kalender und Erfolge mit Spielstand Tag 3. Reihenfolge = Priorität.

## Erledigt ✔ (REVIEW-7 komplett)
- **Ordnungsamt:** Der Trichter zählt einheitlich (62 → 1 → 61 → 44, höchstens 100 %), „unter 1 Tag“, „1 Foto“.
- **Kämmerei:**
  - Zeitraum an jeder Karte (heute / letzte 24 Std. / Amtszeit).
  - „Wo wird verdient?“ ergibt jetzt in der Summe die Bußgelder (3.165 + 30 = 3.195 €), Knöllchen als eigene Zeile.
  - „nächste: +8.571 € morgen“.
- **Topbar:** „bis Ereignis“ ist wieder da.
- **Posteingang:**
  - Schäden sind zu einer Zeile gebündelt, das Abzeichen am Menü zeigt 10 statt 45.
  - Anführungszeichen korrigiert.
- **ÖPNV:** Ampel-Legende, gleiche Farben in Liste und Halten, „Ø +3:33“.
- **Einsätze:** Häufigkeit in Blau, „Letzte Einsätze“ mit Wann / Art / Ort / Dauer.
- **Presse:** Skala „Verriss – neutral – Jubelpresse“, Hinweis in Warnfarbe.
- **Toasts:** „Erfolg: Es blitzt“.
- **Tagesabschluss:** Die Fensterhöhe passt jetzt zum Inhalt.

## 1. Bauamt – noch die alte Seite im neuen Rahmen
Kopf und KPIs passen. Darunter folgt aber das alte Panel: Die Karte „Zustand Ø / Repariert / Klagen“ **wiederholt die KPIs**, die rechte Spalte hat nur das Erhaltungsbudget, die halbe Seite ist leer. Soll laut `V2-Bauamt.dc.html`:
- **Doppelte Karte entfernen.**
- **Rechte Spalte (Statistik):**
  1. **Straßenzustand im Verlauf** seit Tag 1 + **Prognose bis Tag 14 je Erhaltungsbudget** (keins / das Nötigste / vorbildlich als gestrichelte Linien, gewählte farbig). Das Budget-Segment sitzt direkt darunter. Heute sieht man nicht, was „keins“ bewirkt.
  2. **Schäden neu vs. behoben** je 6 Std. (Balken).
  3. **Verschleiß nach Ursache** (Lkw, Pkw, Busse, Wetter) + die 3 Straßen mit dem schnellsten Verfall.
  4. **Kosten Amtszeit** (Flicken, Schilder, Erhalt, Klagen) + **Klagerisiko**, solange Schäden ungesichert sind („42 ungesichert → ≈ x Klagen/Tag“).
- **Linke Spalte = Tabelle** statt Akkordeon: Zeile 46 dp mit Checkbox · Straße · Schaden · Zustand-Balken · Status · Frist · Verfall/Tag. Filter-Chips (alle / ungesichert / mit Schild / in Reparatur), Sortierung nach Frist.
- **Sammelaktion** bei Auswahl (unten eingeblendete Leiste): „12 ausgewählt · Flicken 18.000 € · Schild 30 · Ausschreiben“. Bei 42 Schäden ist Einzelklicken sonst unspielbar.
- Das Detail (Reparatur-Optionen) ist gut. Es öffnet sich als Panel rechts von der Tabelle oder als Aufklappzeile, aber **nur eins** zur Zeit. Die Option „Flicken“ als Primärwahl über die ganze Breite ist okay. Ergänzen je Option: **Haltbarkeit** und **Dauer** (z. B. „hält 3 Tage · sofort“).

## 2. Parken & Politessen – ebenso
- Die Karte „Parkplätze 1/14581 · Straßenrand 1109/5918 · Knöllchen 113“ **wiederholt die KPIs** (ohne Tausenderpunkte) → entfernen.
- **Fehlt:**
  - **Belegung über die Zeit** (Parkhäuser, Straßenrand, Falschparker gestrichelt, Preisänderungen markiert).
  - **Welcher Preis bringt was?** (Tabelle €/Std. → geschätzte Belegung + Gebühren/Tag; aktueller Preis markiert, bester grün).
- **Brötchentaste / Anwohnerparken** sehen weiterhin wie ein Segment aus, bei dem nur eins gehen kann. → zwei **Schalter** mit je einer Zeile Erklärung (beide gleichzeitig möglich).
- Gebühren-Zeile „Alle Parkplätze und Parkhäuser“ hat keine Auswahl, weil die Preise gemischt sind → Hinweis „gemischt“ zeigen, Klick setzt alle.
- „Parkplätze → Blumenkübel (15.000 €)“ ist ein gestrichelter Rahmen. Das liest sich wie ein Platzhalter → normaler Sekundärknopf.
- **Politessen:** „+5“ → „+5 einstellen“ (wie im Ordnungsamt). Ergänzen: Knöllchen je Kopf/Tag, Kosten vs. Ertrag/Woche, „lohnt sich / lohnt sich nicht“.
- **Parkhaus-Tabelle:** Spalte Belegung als Balken + Gebühren 24 h. Den Papierkorb (rot) erst bei Hover zeigen, er ist zu dominant.
- **Daten prüfen:** „Falschparker gerade 1.081“ bei „Straßenrand belegt 1.109“ heißt, dass 97 % der Parker am Straßenrand falsch parken. Ist das so gewollt oder zählt hier etwas doppelt?

## 3. Bürger – Inhalt zu dünn
- „Unzufrieden 1 %“ steht als KPI **und** als eigene Karte → Karte entfernen, Schwelle (Strich bei 35 %) in der KPI zeigen.
- **Fehlt laut `V2-Buerger.dc.html`:**
  - **Zufriedenheit je Haushaltstyp über die Zeit** (3 Linien seit Tag 1, Zorn-Zone schraffiert) statt der drei statischen Balken.
  - **Warum sauer?** – Gründe mit Anteil und Sprung ins zuständige Amt (z. B. „Bußgelder 40 % → Ordnungsamt“).
  - **Was fehlt wem?** – Matrix Haushaltstyp × Bedürfnis (Farbskala). Ersetzt „Bedürfnisse im Mittel“.
- **Brennpunkte:** „Gebiet 32 / 240 / 181“ sagt dem Spieler nichts → **Straßen- oder Stadtteilname** (nächste Hauptstraße), dazu Hauptgrund, Einwohner, Trend ↑↓.
- KPIs: Bürgerzorn mit **Trend** (gestern → heute), dazu Spontandemo-Risiko. „Pendler pünktlich –“ um 00:00 → Wert von gestern zeigen („gestern 88 %“).

## 4. Kalender – Wochenplan fehlt
- Der Kern des Entwurfs fehlt: **Wochenplan Mo–So, 6–24 Uhr** als Raster, vergangene Tage blass, heute markiert, Ereignisse als Blöcke nach Art (geplant / Störung / möglich gestrichelt). Die Liste „Geplant“ kann rechts daneben bleiben (kompakter, 2 Zeilen je Termin, ohne Pfeil-Kacheln).
- Karte „Jetzt – Alles ruhig“ wiederholt die KPI „Jetzt ruhig“ → zusammenlegen.
- Die Infokarte „Dauerstau-Tage in Folge: 0 – danach …“ als **„Was kann passieren?“** ausbauen: je Ereignisart Auslöser → Folge (Rad-Korso, Spontandemo, Marathon …), mit aktuellem Risiko.
- **Chronik** (was ist passiert, Tag 1–3) als Zeile unten.

## 5. Erfolge
- Eyebrow „BERICHT“ → wie die anderen Seiten (oder „Amtszeit“). „4/20“ oben rechts neben „Zur Karte“ doppelt → entfernen.
- Raster nutzt nur die linke Hälfte (2 Spalten), rechts ist es leer. → **volle Breite, 5 Spalten**, kleinere Kacheln.
- KPI „Offen 16“ allein in einer breiten Karte → stattdessen **„Fast geschafft“**: die 3 offenen Erfolge mit dem größten Fortschritt, mit Balken (z. B. „Geflickt – 0/1 Straße“).
- Filter Alle / Erreicht / Offen. Offene Erfolge mit zählbarem Ziel zeigen ihren Fortschritt.
- Den Satz „Zählen nicht für die Wertung – aber für den Ruhm.“ als Unterzeile unter den Titel setzen, nicht in die Karte.

## 6. Kleinigkeiten
- **ÖPNV:** „Halte bedient 38.354 – heute“ und „Umsteiger 3 % der Fahrten heute“ um 00:00 von Tag 3. „Heute“ hat gerade erst begonnen, die Werte sind von gestern → „gestern“ oder „letzte 24 Std.“ Die letzte Richtung in „Verspätung an jedem Halt“ wird angeschnitten → Scroll-Ausblendung wie im Ordnungsamt.
- **Ordnungsamt:** „in 1 Arbeitstagen“ → Singular/Plural.
- **Kämmerei:** In „Wo wird verdient?“ steht „0 %“ für 30 € → „< 1 %“.
- **Posteingang:** Die Meldung „Haushaltsrunde: −2.771 € PR, Gehälter und Erhalt“ nennt PR, obwohl keine Agentur gebucht ist → nur die tatsächlichen Posten aufzählen.
- **Presse:** Im Ton-Verlauf stehen Tag 1 und Tag 2 an verschiedenen Stellen, aber beide mit „Verriss“ beschriftet. Entweder mehr Stufen (Verriss / bissig / neutral / wohlwollend / Jubelpresse) oder die Beschriftung weglassen.

## Reihenfolge
1 → 2 → 3 → 4 → 5 → 6. Danach bitte wieder `*-day3-*` für Bauamt, Parken, Bürger, Kalender und Erfolge.
