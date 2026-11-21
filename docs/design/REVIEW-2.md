# Design-Review 2 – Umsetzung vs. `DESIGN-v2.md`

Grundlage: Screenshots in `scratchpad/shots/` (1440×900, 1920×1080, 2000×1050, Touch). Reihenfolge = Priorität.

## Was gut ist ✔
- Topbar: Höhe, Tempo-Segment, KPI-Chips, Abwahlindex-Meter – passt zum Entwurf, auch bei 1440 und 2000 px.
- Rail mit allen Einträgen inkl. Einsätze, aktiver Eintrag gelb.
- Bauleiste: Icon + Name, Punkte für Varianten, Abbauen rot, Popover mit Pfeil aufs Werkzeug. 
- Hauptmenü: entspricht dem Entwurf.
- Größen stimmen jetzt (kein Halbieren / Aufblasen mehr).

## 1. Die Seiten sind noch keine Vollbildseiten (wichtigster Punkt)
Posteingang, Ordnungsamt, ÖPNV, Einsätze (und vermutlich alle anderen) zeigen das **alte Seitenpanel (≈ 744 px) auf leerer grauer Fläche**. Beim Ordnungsamt stehen zwei alte Panels nebeneinander (Ordnungsamt + Blitzer-Statistik).
- Jede Seite nach ihrem Entwurf in `mockups/v2/` bauen: Kopf (Eyebrow „Rathaus“, Titel 32 sp, rechts „Zur Karte M“) → KPI-Kacheln → 12er-Raster über die **ganze Breite**.
- Kein ✕ oben rechts (es ist eine Seite, kein Fenster) → „Zur Karte M“.
- Blitzer-Statistik ist kein eigenes Panel mehr, sondern Teil des Ordnungsamts (Fotos/h, „Wie viel zu schnell“ Fotos vs. Geld, Standorte-Tabelle).
- Leere Zustände (Einsätze „Alles ruhig“, Aufträge 0) trotzdem mit KPIs, Fahrzeugen und Statistik füllen – nie eine leere Seite.

## 2. Karte: Ebenen, Meldungen, Lage noch im alten Layout
- **Ebenen-Umschalter** ist noch die Segmentleiste oben Mitte (Verkehr / Umwelt: Luft / Zufriedenheit / Straßenzustand). Soll: Kasten **oben links** mit Liste (Verkehrsfluss, **Verkehrsdichte**, **Buslinien**, Straßenzustand, Zufriedenheit, Luft, Geld), Legende der aktiven Ebene, „Einblenden“-Häkchen.
- **Meldungsliste** steht als Panel oben links auf der Karte → entfernen. Stattdessen: oben rechts Lage-Zeile + **Posteingang-Hinweis** (je Typ eine Zeile), neue Meldungen als **Toasts unten links**.
- **Buslinien-Ebene fehlt**: bei Nahzoom (map-transit.png) sieht man nur „H“-Punkte. Soll: Linienverlauf in Linienfarbe, Abschnitte nach Verspätung grün/gelb/rot, Haltestellen als Punkte auf der Linie.

## 3. Werkzeug-Popover zu groß
„Kontrolle“ hat 9 Varianten in 3 Reihen → Popover ≈ 520 px hoch, verdeckt die halbe Karte (bei 1440×900 bis an die Meldungen).
- Höchstens **2 Reihen**: Karten kompakter (Icon + Name + Preis, Beschreibung nur 1 Zeile oder als Tooltip), 5 Spalten.
- Oder aufteilen: **Blitzer** (fest, Rotlicht, Rotlicht+Tempo, Attrappe, Busspur) und **Mobil** (Laser, Anhänger, Polizei, Anzeigetafel) als zwei Werkzeuge.
- Tastenwahl 1–9 behalten.

## 4. Toasts uneinheitlich
toasts.png zeigt drei verschiedene Stile (weiß mit Medaille + Konfetti, dunkel, rote Pille). Soll (DESIGN-v2 „Meldungen“): **ein** Stil, weiß, Farbstreifen + rundes Icon nach Typ, Titel / Unterzeile, optional 1 Aktion, ✕, Zeitbalken, unten links gestapelt. Erfolge dürfen Konfetti behalten, aber im gleichen Kartenrahmen.

## 5. Tagesabschluss = alter Entwurf
dayreport.png:
- Fehlt: **✕ / Esc / Klick daneben** zum Schließen, Bilanz (Einnahmen, Ausgaben, Saldo, Reputation/Zorn/Abwahl alt→neu), Ton der Zeitung, „Zur Kämmerei“.
- Knopf gelb → **blau** (`Primary`), Schriften noch Noto statt Inter.
- **Bugs:** „Verkehrsbilanz: 0 Fahrzeug-km · 0 Fahrzeugstunden“; „Fahrten je Stunde“ ist ein Sägezahn aus zwei Geraden (0–12 und 12–24 Uhr) – sieht nach Platzhalterdaten aus.
- Layout nach `V2-Zeitung.dc.html`: Zeitung links groß, Bilanz rechts, Fußzeile mit Aktionen.

## 6. Inspektor abgeschnitten
inspect-card.png: Schadenskarte im Inspektor ist unten abgeschnitten („Schild 30/10“ halb sichtbar). Inspektor-Höhe an Inhalt anpassen oder scrollen; außerdem doppelter Rahmen (Karte in Karte) entfernen.

## 7. ÖPNV
- Seite hat nur die Liste. Soll: Liste links + **Netzkarte** rechts (gewählte Linie hervorgehoben, Abschnitte nach Verspätung) + Verspätung je Halt.
- Richtungen ohne Daten („–“) zeigen keinen Balken → grauer Balken + „noch keine Fahrt“.

## 8. Touch / Querformat
touch-transit.png: Panel nimmt die rechte Hälfte, Rail mit „Ämter / Berichte / Einsätze / Lage / Karte“ – weicht vom Android-Entwurf (Entwurf 1) ab, ist aber brauchbar. Auf der Karte überlappen ein Tempo-30-Schild und ein Bus-Icon – Schilder bei Kollision versetzen.

## Reihenfolge
1 → 2 → 5 → 3 → 4 → 7 → 6 → 8. Danach wieder Screenshots (gleiches Set).
