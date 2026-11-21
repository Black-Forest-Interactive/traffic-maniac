# Design-Review 4 – Stand nach REVIEW-3

Grundlage: Screenshots vom 21.11. (zweite Runde). Reihenfolge = Priorität.

## Erledigt ✔
- Ordnungsamt: Trichter „Weg eines Falls“ mit Kapazitäts-Hinweis, Fotos pro Stunde, Anteil Fotos **und** Geld, feste Spalte „Steuerung“ rechts.
- Einsätze: Wachen & Fahrzeuge (Wachen / unterwegs / im Einsatz), Amtszeit-Block.
- Posteingang: „Erfolg“ statt „Achievement“, Aktion „Zu den Erfolgen“.
- Tagesabschluss: Zeitung über die volle Höhe mit Zeichnung, „Verkehr“ einklappbar, Bilanz passt.
- Toasts: Titel 2-zeilig. Tempo: „bis Ereignis ∨“ statt „⏩“.
- ÖPNV: „Sorgenkind: keins – alles pünktlich“, Netzkarte mit Stadtkarte, Umschalter nach Verspätung / nach Linie.
- Touch: Seiten jetzt als Vollbild mit „Zur Karte“.

## 1. Buslinien-Ebene: Bündel verstecken jetzt alles
`map-transit-lines.png`: Fast jede Straße ist ein **graues Band mit „27 Linien“, „13 Linien“, „10 Linien“** – die einzelnen Linien sind wieder unsichtbar, nur Nebenstrecken sind farbig.
- **Zahl prüfen:** 27 Linien auf einer Straße ist unwahrscheinlich – vermutlich werden **Richtungen/Varianten als eigene Linien gezählt**. Pro Linien-*Nummer* zählen.
- Bündel **nicht grau**, sondern als **Streifen der beteiligten Linienfarben** (dünne Bänder nebeneinander, max. 6, danach „+n“).
- Schwelle zoomabhängig: in dieser Nahansicht bis **8 Linien** einzeln versetzt zeichnen; erst darüber bündeln. Bei weitem Zoom früher bündeln.
- Beschriftung „n Linien“ **einmal pro zusammenhängendem Abschnitt**, nicht an jedem Segment (heute 2–3 Labels auf derselben Straße).
- Hover/Klick auf ein Bündel → Liste der Linien (Badges), Klick hebt eine hervor.

## 2. ÖPNV-Netzkarte: Beschriftungen überlappen
- Haltestellennamen liegen übereinander („Kulturhaus“, „Fritz-Erler-Schule Süd“, „Pforzheim Bahnhofstraße“ …), oben links ein abgeschnittenes „…inien“.
  → Nur **Start- und Endhalt** dauerhaft beschriften, übrige Namen bei Hover; Kollisionsprüfung für Labels.
- Die **gewählte Linie** (717) selbst ist kaum zu sehen – nur rosa Haltepunkte. → gewählte Linie **dick in Linienfarbe**, alle anderen dünn grau; Karte auf diese Linie zoomen (Bounds + Rand).
- „Verspätung an jedem Halt“: noch 8× „noch keine Fahrt“ (aus REVIEW-3 offen) → einmal „Heute noch keine Fahrt in dieser Richtung“.

## 3. Einsätze: doppelte Leerkarten
„Alles ruhig – kein Blaulicht unterwegs“ und „Fahrzeuge im Dienst (0)“ stehen als zwei eigene Karten links **zusätzlich** zur Tabelle „Wachen & Fahrzeuge“ – das sagt dreimal dasselbe.
- Links **eine** Karte „Laufende Einsätze“ (leer: kurzer Satz + kleines Symbol), rechts Wachen & Fahrzeuge.
- „Amtszeit“ über die volle Breite darunter, mit „Häufigkeit nach Art“ und „Letzte Einsätze“ (leer: „noch keine“).

## 4. Posteingang
- Detail wiederholt den Titel („Erfolg freigeschaltet“ / „Erfolg freigeschaltet: Es blitzt!“) und hat den Platzhalter „Gut für Kasse oder Ruf.“ → konkrete Folge nennen (z. B. „+0 € · +1 Ruf“) oder weglassen.
- **Bündeln** noch offen: „Zu wenig Geld für …“ steht mehrfach einzeln.
- Zähler passen nicht: Liste „Alle 12 / Meldungen 12“, KPI „Meldungen 14“.

## 5. Touch (Querformat Handy)
- Auf 2340×1080 nimmt die KPI-Reihe fast die ganze Höhe ein, vom Inhalt sieht man nur die erste Zeile. → Auf Touch/kleiner Höhe KPIs als **eine kompakte Chip-Zeile** (Wert + Label nebeneinander), Inhalt darunter scrollt.
- `touch-budget.png` zeigt noch die alte Seite **„Haushalt & PR“** mit Eyebrow „BERICHT“ → auf Touch dieselben Seiten wie Desktop (Kämmerei, Presse & PR), Eyebrow „Rathaus“.
- Einheiten fehlen: „Kassenstand 8.300“, „Ausgaben bisher 91.700“ → mit „€“.

## 6. Kleinigkeiten
- Ordnungsamt: Seite reicht unter den Bildschirmrand (Wenn Fahrer nicht ermittelbar, Standorte) – sicherstellen, dass beide Spalten scrollen und unten 24 dp Luft haben.
- Tagesabschluss: linke Zeitungsspalte unter „Kurz gemeldet“ leer → kurzer Artikeltext (2–3 Sätze) aus der Tagesbilanz.

## Reihenfolge
1 → 2 → 3 → 5 → 4 → 6.
