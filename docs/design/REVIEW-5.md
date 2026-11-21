# Design-Review 5 – Stand nach REVIEW-4

Grundlage: Screenshots vom 21.11. (dritte Runde). Reihenfolge = Priorität. Insgesamt: nur noch Feinschliff.

## Erledigt ✔
- **Buslinien-Ebene:** Linien sind wieder einzeln in Linienfarbe zu sehen, Bündel als farbige Streifen, Label „10 Linien“ nur einmal.
- **ÖPNV-Netzkarte:** gewählte Linie dick in Linienfarbe, andere grau, Stadtkarte als Hintergrund.
- **Einsätze:** eine Karte „Laufende Einsätze“ + Wachen & Fahrzeuge, Amtszeit über die volle Breite.
- **Posteingang:** Detail ohne doppelten Titel und ohne Platzhalter, „2× Zu wenig Geld“ gebündelt, Zähler stimmen (11 · 14 einzeln).
- **Touch:** KPIs als Chip-Zeile, Kämmerei mit Eyebrow „Rathaus“, Beträge mit „€“.
- **Tagesabschluss:** Artikeltext „Aus dem Rathaus“ in der linken Spalte, Saldo stimmt (38.400 − 46.900 = −8.500).

## 1. ÖPNV: „Verspätung an jedem Halt“ (seit REVIEW-3 offen)
Desktop und Touch zeigen weiterhin 7× „noch keine Fahrt“.
- Hat eine Richtung keine Fahrt: **eine** Zeile „Heute noch keine Fahrt in dieser Richtung“ und die Halteliste weglassen.
- Hat sie teilweise Daten (nur der erste Halt +0:00): Halte ohne Wert mit grauem Balken und „–“ statt Text.

## 2. Touch: Kämmerei und ÖPNV
- **Kämmerei (Touch)** zeigt noch den alten Inhalt (Haushalt/Woche, Reputation, PR-Agentur). Soll denselben Inhalt wie am Desktop zeigen: Verlauf Einnahmen/Ausgaben, nach Kategorie, nach Ort, Buchungen. **PR-Agentur gehört auf die Seite Presse & PR.**
- **Chip-Labels werden abgeschnitten** („Kassens…“, „Ausga…“, „Pünktlich h…“, „Autos stehen g…“). → Kurze Labels für Touch („Kasse“, „Ausgaben“, „Zuschüsse“, „Pünktlich“, „Umsteiger“) und Chips nach Inhalt breit machen; wenn sie nicht passen, die Zeile horizontal scrollen lassen statt zu kürzen.
- **ÖPNV (Touch):** Die Spalte mit den Werten in „Verspätung an jedem Halt“ ist zu schmal; „+0:00“ und „noch keine Fahrt“ brechen um. Werte einzeilig, Spalte mit Mindestbreite (≈ 96 dp). Bei wenig Breite die beiden Karten untereinander statt nebeneinander.

## 3. Buslinien-Ebene – Feinschliff
- **Reihenfolge der Streifen festhalten:** An Abschnittsgrenzen springen die Farben die Position (an Kreuzungen sieht man verdrehte Bänder und Lücken). Je Linie eine feste Spur innerhalb des Bündels über den ganzen zusammenhängenden Verlauf, an Knicken mit runden Verbindungen.
- **Breite begrenzen:** 8–10 Streifen nebeneinander ergeben bei dieser Zoomstufe ≈ 35 px breite Bänder, die Straßen und Namen verdecken. Strich bei vielen Linien auf 2 px verringern; Gesamtbreite höchstens ≈ 20 px, sonst bündeln („+n“).
- **Haltestellen einheitlich:** Neben den weißen Ringen gibt es noch gefüllte grüne und rote Punkte. In dieser Ebene nur ein Haltestellen-Stil (weißer Punkt mit Ring). Verspätung nur per Hover oder im Inspektor.

## 4. ÖPNV-Netzkarte – Beschriftungen
- „Kulturhaus Osterfeld“ liegt auf „Bahnhofstraße“ und auf Haltepunkten, „Gräfenhauser Straße“ wird von Punkten überdeckt. → Labels mit weißem Hintergrund **neben** die Linie setzen (nicht auf den Punkt), Kollisionsprüfung zwischen Labels. Wenn sich zwei Labels überlappen, nur Start und Ende zeigen.
- Auf die Linie zoomen: Die Linie füllt nur die linke Hälfte. Bounds der Linie + 10 % Rand.

## 5. Ordnungsamt (1440×900)
- Die linke Spalte ist weiterhin unten abgeschnitten („Mängel am Fahrzeug“ halb sichtbar). Prüfen, ob sie scrollt. Dazu unten 24 dp Luft und einen Scroll-Schatten oder eine Scrollleiste, damit man sieht, dass noch etwas kommt.
- Spalte „Klasse“ breiter (≈ 140 dp), damit „Mängel am Fahrzeug“ einzeilig bleibt.
- **Leere Werte:** „Aufklärungsquote 76 %“ bei 0 Fotos und „Ältester Fall 0 Tage“ bei leerem Stapel → „–“ mit Unterzeile „noch keine Fälle“.
- Der Knopf „+5“ neben dem Sachbearbeiter-Stepper ist unklar → „+5 einstellen“ oder Tooltip.

## 6. Einsätze – Kleinigkeiten
- Unter „Häufigkeit nach Art“ steht zuerst „noch keine“ und **darunter** die Tabellenköpfe „Art / heute · Amtszeit“. Bei leerem Zustand die Köpfe weglassen.
- „Laufende Einsätze“ hat im leeren Zustand ≈ 200 dp Leerraum → Kartenhöhe an Inhalt anpassen (Wachen-Tabelle bestimmt die Zeilenhöhe, links darf kürzer sein oder Inhalt vertikal mittig).

## 7. Posteingang
- Meldungen mit Ort (z. B. „Grüne Welle 50 km/h – Karlsruher Straße“) zeigen im Detail nur Titel und Text → kleinen Kartenausschnitt + Aktion „Auf der Karte zeigen“ (aus REVIEW-3, noch offen).

## 8. Tagesabschluss
- Im Artikel steht „Das Rathaus nahm **gestern** … ein“. Der Bericht gilt für den abgeschlossenen Tag → „heute“ bzw. „am Montag“.
- **Kasse prüfen:** In der Bilanz steht „Kasse −5.595 €“. Muss gelten: Kasse neu = Kasse alt + Saldo. Kasse wie bei Reputation als „alt → neu“ anzeigen. Ist die Kasse negativ, den Wert rot zeigen und darunter einen Hinweis („Haushaltssperre droht“ o. ä.).

## Reihenfolge
1 → 2 → 3 → 5 → 4 → 8 → 6 → 7. Danach wieder dasselbe Screenshot-Set, zusätzlich bitte `game-treasury-1920x1080.png` und `game-press-1920x1080.png`.
