# Design-Review 3 – Stand nach REVIEW-2

Grundlage: neue Screenshots (21.11.), u. a. `map-transit-lines.png`. Reihenfolge = Priorität.

## Erledigt ✔ (gut geworden)
- Vollbildseiten: Posteingang, Ordnungsamt, ÖPNV, Einsätze mit Kopf, KPI-Kacheln und „Zur Karte M“.
- Karte: Kartenansicht oben links mit allen 7 Ebenen + Legende + „Einblenden“; Lage + Posteingang-Hinweis oben rechts; alte Meldungsliste weg.
- Werkzeug-Popover auf 2 Reihen, Pfeil aufs Werkzeug.
- Toasts einheitlich (Streifen, Icon, ✕, Zeitbalken), unten links, Link zum Posteingang.
- Tagesabschluss: schließbar, Bilanz, Ton der Zeitung, echte Stundenkurve, blauer Primärknopf.
- Inspektor nicht mehr abgeschnitten.

## 1. Buslinien-Ebene ist nicht lesbar
`map-transit-lines.png`: alle Linien liegen **übereinander** auf derselben Straße – breite rot/lila Bänder, man erkennt keine einzelne Linie. Dazu doppelte Haltestellen (gelbe „H“-Icons + grüne/rote Punkte).
- **Linien nebeneinander versetzt** zeichnen (paralleler Offset je Linie, 3–4 px Strich, 1 px Abstand). Ab 4 Linien auf einem Abschnitt: Bündel mit Zahl („7 Linien“).
- **Zwei Färbungen**, umschaltbar in der Legende: *nach Linie* (Linienfarbe, Standard) und *nach Verspätung* (grün/gelb/rot). Nicht beides gleichzeitig.
- **Basiskarte dämpfen**, solange die Ebene aktiv ist (Straßen grau, Gebäude blass) – wie bei Zufriedenheit.
- Haltestellen **einmal**: weißer Punkt mit Ring in Linienfarbe; die gelben „H“-Icons in dieser Ebene ausblenden. Verspätung am Halt nur per Hover/Inspektor.
- Legende der Ebene = Liste der Linien (Badge + Name), Klick hebt eine Linie hervor, alle anderen werden grau.

## 2. ÖPNV-Seite: Netzkarte
- Die Netzkarte ist ein kleines Linienknäuel ohne Hintergrund in einer großen leeren Fläche. Soll: **gedämpfte Stadtkarte als Hintergrund**, auf die **gewählte Linie gezoomt**, diese farbig + Haltestellen beschriftet, alle anderen Linien grau (siehe `V2-OePNV.dc.html`).
- Gewählte Linie (717) ist in der Karte nicht hervorgehoben – alle Linien bunt.
- „Verspätung an jedem Halt“: wenn noch keine Fahrt, **einmal** „Heute noch keine Fahrt auf dieser Linie“ statt 8× „noch keine Fahrt“.
- KPI „Sorgenkind“: wenn alles pünktlich (100 %), „keins – alles pünktlich“ statt einer Linie mit +37 s und oranger Umrandung.

## 3. Ordnungsamt: Inhalt noch nicht wie im Entwurf
- „Weg eines Falls“ wiederholt nur Aufklärungsquote + Aktenstapel (stehen schon in den KPIs). Soll: **Trichter** Geblitzt → Im Stapel → Bearbeitet → Bezahlt mit Anzahl + Anteil, darunter Eingestellt / Verjährt / Fahrverbote und der **Kapazitäts-Hinweis** (Fälle/Tag vs. Eingang, wann verjährt etwas).
- Fehlt: **Fotos pro Stunde** (Balken), in „Wie viel zu schnell“ die zweite Spalte **Anteil Geld**, in „Standorte“ die Spalten **Bekannt in Warn-Apps** und **Ø Tempo-Effekt**.
- Aufteilung: links Statistik, **rechts feste Spalte „Steuerung“** (Sachbearbeiter, Frist, Schärfe, Fahrer nicht ermittelbar, Warn-Apps). Heute sind Steuer-Karten und Statistik-Karten gemischt und die Seite läuft unten aus dem Bild.

## 4. Einsätze: leerer Zustand ist noch leer
„Alles ruhig“ + „Fahrzeuge im Dienst (0)“ auf sonst leerer Seite. Auch ohne Einsatz zeigen:
- **Fahrzeugpark** je Art mit frei / unterwegs / im Einsatz (z. B. Polizei 6 frei) – „im Dienst 0“ liest sich wie „keine Fahrzeuge“.
- **Amtszeit-Statistik**: erledigte Einsätze, Ø Anfahrt, Stau durch Einsätze, Häufigkeit nach Art, letzte Einsätze.

## 5. Tagesabschluss – Feinschliff
- Zeitung belegt nur das obere Drittel, darunter leer → Zeitung über die volle Höhe: größere Schlagzeile, Unterzeile, 2 Spalten Text, Zeichnung, Leserbrief, „Kurz gemeldet“.
- Rechte Spalte läuft unten über (Diagramm-Achse abgeschnitten) → scrollen oder „Verkehr“ einklappbar.
- **Daten-Bug:** „Fahrten gestartet 33.840 · angekommen 41.000“ – angekommen > gestartet.

## 6. Posteingang – Kleinigkeiten
- Detail einer Meldung wiederholt nur den Titel. Mehr zeigen: Ort mit Kartenausschnitt, Folge (Geld/Ruf), passende Aktion (z. B. „Zu den Erfolgen“, „In der Kämmerei ansehen“).
- Gleichartige Meldungen bündeln („3× Zu wenig Geld für …“).
- „Achievement“ → „Erfolg“ (deutsch, wie in der Navigation).

## 7. Kleinigkeiten
- Toast-Titel wird abgeschnitten („hat sich bezahlt ge…“) → Titel 2-zeilig erlauben oder kürzen („Blitzer Goethestraße bezahlt“).
- Tempo-Knopf „⏩“ hinter 64× ist unklar → Beschriftung/Tooltip („Nacht überspringen“).
- Blitzer-Symbol auf der Karte mit schwarzem Kästchen „– –“ darüber überlappt das Tempo-Schild → Beschriftung versetzen.
- Touch-Ansicht: Seiten noch als halbes Panel mit „BERICHT“ und ✕ → auch hier Vollbildseite mit Zurück.

## Reihenfolge
1 → 2 → 3 → 4 → 5 → 6 → 7.
