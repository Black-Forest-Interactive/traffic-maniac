# Design-Review 6 – Stand nach REVIEW-5

Grundlage: Screenshots vom 21.11. (vierte Runde), neu dabei Kämmerei und Presse am Desktop. Reihenfolge = Priorität.

## Erledigt ✔
- **ÖPNV:** Halte ohne Wert zeigen „–“ statt „noch keine Fahrt“. Die Netzkarte beschriftet nur noch wenige Halte, nichts überlappt mehr.
- **Buslinien-Ebene:** Die Streifen sind schmaler, kaum noch Lücken, Haltestellen als Ringe.
- **Touch:** Die Kämmerei hat jetzt den neuen Inhalt. Die Chips werden nicht mehr abgeschnitten, die Zeile scrollt. ÖPNV zeigt die Linien in voller Breite.
- **Ordnungsamt:** „–“ bei leeren Werten, „+5 einstellen“, Ausblendung unten als Scroll-Hinweis, „Mängel am Fahrzeug“ einzeilig.
- **Einsätze:** keine leeren Tabellenköpfe mehr, Kartenhöhe passt zum Inhalt.
- **Tagesabschluss:** „am Montag“, Kasse als „2.905 € → −5.595 €“ mit Warnung. Die Rechnung stimmt.

## 1. Kämmerei: Zahlen passen nicht zusammen
- Die Topbar zeigt „Kasse 8.300 € · heute ±0“, die Kämmerei dagegen „Ausgaben heute 91.700 € · Saldo heute −91.700 €“. **Beides muss aus derselben Quelle kommen.**
- Alle Buchungen stehen auf 00:00, das sind offenbar die Bauten zum Spielstart. Vorschlag: Diese Buchungen als **„Startausstattung“** (Tag 0) verbuchen. Sie erscheinen dann nicht als „heute“, aber in „Amtszeit“ und in der Buchungsliste unter „Tag 0“.
- **Diagramm „Geldfluss pro Stunde“:** Der einzige Balken steht bei „jetzt“, obwohl die Buchungen um 00:00 waren (5 Stunden vorher). Balken der richtigen Stunde zuordnen. Es fehlen Y-Achsen-Werte (mindestens 0 und ein Höchstwert) und Stunden an der X-Achse (0, 6, 12, 18 Uhr).

## 2. Kämmerei: Inhalt verfeinern
- **Buchungen:**
  - Gleiche Posten bündeln („2× Tempo-30-Schild −4.000 €“).
  - Je Zeile den **Ort** in grau zeigen (Straße). Klick auf die Zeile springt zur Karte.
  - Die Liste läuft unten aus dem Bildschirm. Sie soll in ihrer Karte scrollen, die Höhe richtet sich nach der rechten Spalte.
- **„Wohin geht es?“** fasst fast alles unter „Baumaßnahmen 90.500 €“ zusammen. Nach Art aufteilen: Blitzer, Busspur, Busvorrang, Fahrradstraße, Schilder, Anzeigetafeln, Personal, PR. Höchstens 6 Zeilen, der Rest als „Sonstiges“.
- **„Woher kommt das Geld?“** ist leer. Auch ohne Einnahmen die erwarteten Posten zeigen: „Haushalt heute Nacht +8.571 €“ (60.000 / 7), Bußgelder, Parken, Zuschüsse, jeweils mit 0 €.
- „Haushaltsrunde“ ist gut. Ergänzen: **„Nächste Abrechnung in 18:50 h“** und die Prognose für die Kasse danach.

## 3. Presse & PR: Doppeltes entfernen, Inhalt ergänzen
- Die Karte „Presse“ zeigt dieselben Werte wie die KPIs (Ton neutral, Story wirkt 100 %, Aufschlag +0 %). → Karte „Presse“ auflösen. Übrig bleibt eine Karte **„Anlässe“** mit Spatenstich/Eröffnung.
- Der Knopf „Spatenstich / Eröffnung (6.000 €)“ geht über die volle Breite → normale Knopfbreite, rechts ausgerichtet; Text links: „Nächster Anlass: …“.
- KPI „Spatenstiche“: Die Unterzeile wiederholt den Anlass → stattdessen „letzter: –“ oder „letzter: Tag 3“.
- **PR-Agentur / Kampagne:** Unter jeder Option kurz die Wirkung nennen, z. B. „+1 Ruf/Tag, Ton höchstens freundlich“. Dazu der Hinweis „Kosten erscheinen in der Kämmerei unter PR“.
- **Rechts unten leer:** Laut Entwurf `V2-Presse.dc.html` gehören dorthin:
  - **Ton der Zeitung im Verlauf**: die letzten 7 Tage als Punktreihe von bissig bis freundlich.
  - **Schlagzeilen-Archiv**: die letzten Ausgaben mit Tag, Schlagzeile und Ton. Ein Klick öffnet die Zeitung.

## 4. Posteingang (seit REVIEW-3 offen)
- Meldungen mit Ort (z. B. „Grüne Welle 50 km/h – Karlsruher Straße“) zeigen im Detail einen **Kartenausschnitt** (≈ 240 dp hoch) und die Aktion **„Auf der Karte zeigen“**.
- Meldungen zu Einstellungen („Bearbeitungsfrist 5 Tage“) → Aktion „Zum Ordnungsamt“.

## 5. Kleinigkeiten
- **Buslinien-Ebene:** An der großen Kreuzung oben Mitte kreuzen sich die Streifen noch. Die Reihenfolge der Linien soll über die Kreuzung hinweg gleich bleiben.
- **ÖPNV-Netzkarte:** Rechts liegt die Linie am Rand („Pforzheim Bahnhofstraße“ ist fast abgeschnitten). Beim Zoomen auf die Linie mehr Rand lassen (≈ 15 %).
- **Tagesabschluss:** Die Zeitung hat unter „Aus dem Rathaus“ und unter „Leserbriefe“ noch Leerraum. Entweder einen zweiten Leserbrief und einen weiteren Kurzartikel ergänzen, oder die Zeitungshöhe an den Inhalt anpassen.

## Reihenfolge
1 → 2 → 3 → 4 → 5. Danach wieder dasselbe Screenshot-Set (inkl. Kämmerei und Presse), wenn möglich zusätzlich ein Spielstand nach einigen Spieltagen (`*-day3-*`), damit die Statistiken mit echten Daten zu sehen sind.
