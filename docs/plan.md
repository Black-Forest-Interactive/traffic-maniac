# traffic-maniac – Grobplan

> Status: Entwurf / Ideensammlung – noch keine Implementierung.

## 1. Die Idee in einem Satz

Eine Parodie auf klassische Verkehrssimulatoren: Statt den Verkehr einer echten Stadt (z. B. **Pforzheim**) flüssig zu halten, bremst man ihn mit Ampeln, Tempolimits, Blitzern, Radspuren, Baustellen & Co. aus – bis hin zum **absoluten Stillstand**. Gewonnen hat aber nicht, wer die Stadt am besten lahmlegt, sondern wer dabei **am meisten Geld verdient und sich am besten präsentiert**. Ob die Stadt am Ende in Trümmern liegt oder strahlt, ist völlig egal.

## 2. Spielprinzip

### 2.1 Kern-Loop

1. Stadt wählen (Start: Pforzheim) → echtes Straßennetz wird geladen.
2. Die Simulation läuft: Pendler, Lieferverkehr, Busse fahren von A nach B.
3. Spieler platziert "Verkehrsberuhigungsmaßnahmen" und gibt dafür Budget aus.
4. Autofahrer reagieren: Sie bremsen, stauen sich, **weichen über Navi-Apps aus** (Schleichwege!).
5. Kennzahl "Stau-O-Meter" steigt, Bußgelder und Gebühren füllen die Kasse, PR poliert das Image.
6. Am Ende der Amtszeit zählen nur **Geld** und **Reputation** (siehe 2.14).

### 2.1a Sieg & Niederlage auf einen Blick

| | Bedingung |
|---|---|
| **Gewinnen** | Amtszeit überstehen und dabei möglichst viel Geld *und* eine glänzende Reputation haben → Punktzahl |
| **Verlieren** | Abwahlindex über dem Grenzwert → vorzeitig raus (2.10) |
| **Egal** | Zustand der Stadt: Stau, Dreck, eingestürzte Brücken – zählt nicht für die Wertung |

### 2.2 Werkzeuge (Auswahl, erweiterbar)

| Kategorie         | Beispiele                                                                 |
|-------------------|---------------------------------------------------------------------------|
| Ampeln            | Neue Ampel, "Rote Welle", Bedarfsampel ohne Bedarf, 3-Minuten-Rotphase    |
| Tempo             | Tempo 30 / 20 / Schrittgeschwindigkeit, Spielstraße                       |
| Überwachung       | Blitzer (fest, alle bremsen panisch davor, wer zu schnell ist zahlt), mobiles Lasermessgerät (braucht Personal, jederzeit umsetzbar, Überraschungseffekt), Section Control |
| Parken            | Parkgebühren, Parkplätze streichen, Politessen einstellen, Anwohnerparken  |
| Fahrbahn          | Radspur (nimmt eine Fahrspur weg), Busspur, Fahrbahnverengung, Poller     |
| Struktur          | Einbahnstraße (auch gerne gegeneinander), Sackgasse, Kreisverkehr         |
| Chaos             | Baustelle, Umleitung im Kreis, Blumenkübel, Zebrastreifen-Serie           |
| Umleitungen       | Umleitung bei Baustellen selbst festlegen – gerne durch enge Nebenstraßen oder verkehrsberuhigte Bereiche (siehe 2.13) |
| Events            | Stadtfest, Marathon, Wochenmarkt, Schwertransport                         |

Jedes Werkzeug hat: Kosten, Wirkung auf Kapazität/Geschwindigkeit, ggf. Nebenwirkungen (Beschwerden).

### 2.3 Gegenspieler & Parodie-Elemente

- **Bürgerzorn-Leiste**: Zu viel auf einmal → Beschwerden, Petitionen, Demo vorm Rathaus (Details zum Modell in 2.10).
- **Lokalzeitung**: Satirische Schlagzeilen zu den Aktionen des Spielers ("Dritter Blitzer in der Westlichen – Anwohner verwirrt").
- **Autofahrer-Lobby** (fiktiv, keine echten Vereine/Marken): Klagen, die Maßnahmen rückgängig machen.
- **Abwahlindex**: Politik funkt nur rein, im Mittelpunkt steht der Verkehr. Statt Wahlen gibt es einen Index – steigt er über einen Grenzwert, ist man raus (Game Over). Details in 2.10.
- **Navi-KI**: Autofahrer werden schlauer und finden Schleichwege → Spieler muss nachziehen.

### 2.4 Fortschritt

- Level = Stadtteile oder Szenarien ("Berufsverkehr Montagmorgen", "Adventssamstag").
- Freischaltbare Werkzeuge, Highscores (Punktzahl aus Geld × Reputation, siehe 2.14).
- Achievements für Stadt-Extreme, die nicht in die Wertung eingehen: "Totalstillstand", "Alle Brücken gesperrt", "Halb Pforzheim kam zu spät", "Schilderwald", …
- Später: weitere Städte (über OSM prinzipiell jede Stadt möglich).

### 2.5 Grafikstil: cartoonmäßig

- Karte bleibt erkennbar Pforzheim, aber in einem eigenen **Comic-Kartenstil**: kräftige, leicht übersättigte Farben, dicke Straßen mit Outline, vereinfachte Gebäude und viel Grün.
- Fahrzeuge als **kleine Cartoon-Sprites** (knubbelige Autos, Busse, Lastenräder) mit kleinen Animationen: wippen beim Bremsen, Hupen-Sprechblasen, Dampf aus der Motorhaube bei langem Stau.
- Maßnahmen als überzeichnete Icons: riesige Blitzer, Ampeln mit Gesicht, Baustellen mit Bagger und Schaufel.
- Feedback mit Comic-Effekten: "Wutwolken" über Staus, Emoji-Sprechblasen der Fahrer, Schlagzeilen im Comic-Zeitungslayout.
- Die Karte wird **selbst gezeichnet** (aus dem OSM-Straßengraphen), nicht über fertige Kartenkacheln. Das passt gut zum Cartoon-Stil, weil wir jede Linie und Farbe selbst bestimmen.
- Assets als Sprite-Sheets (SVG → PNG).
- **Ansicht: 2D-Draufsicht wie bei Google Maps / OpenStreetMap.** Norden oben, Zoom per Mausrad, Verschieben per Drag. Keine Schrägansicht.

### 2.6 Fahrzeuge & Fahrer

Jedes Fahrzeug hat:

| Eigenschaft      | Beschreibung                                                                 |
|------------------|------------------------------------------------------------------------------|
| ID               | eindeutige Nummer (z. B. für "Fahrzeug verfolgen" per Klick)                 |
| Farbe            | zufällig bzw. passend zur Klasse, für den Cartoon-Look                       |
| Geschwindigkeit  | aktuelle Geschwindigkeit (+ Wunschgeschwindigkeit je nach Fahrer)            |
| Fahrzeugklasse   | PKW, Lieferwagen, LKW, Bus                                                   |
| Antriebsart      | Benzin, Diesel, Elektro (Hybrid ggf. später)                                 |
| Fahrerstereotyp  | bestimmt, *wie* gefahren wird (siehe unten)                                  |
| Generation       | modern oder alt; bestimmt, *welche* Pannen passieren (siehe unten)           |
| Aufmerksamkeit   | 0–100 %, sinkt durch Reizüberflutung, erholt sich auf ruhigen Strecken (siehe unten) |
| (Elektro) Akku   | Ladestand; leer → muss zur Ladesäule                                         |

**Fahrzeugklassen**

- **PKW**: Standard.
- **Lieferwagen**: hält gerne mal kurz in zweiter Reihe (blockiert eine Spur).
- **LKW**: lang, langsam beim Beschleunigen, laut, viele Abgase, braucht viel Platz beim Abbiegen.
- **Bus**: hält an Haltestellen, fährt nach Fahrplan (ggf. erst später).

**Antriebsarten**

- **Verbrenner (Benzin/Diesel)**: Abgase und Motorlärm, besonders beim Anfahren und im Stop-and-go. Diesel-LKW sind am schlimmsten.
- **Elektro**: keine Abgase, leise. Dafür braucht es **Ladesäulen**: Bei niedrigem Akku fährt das Auto zur nächsten Säule, dort bilden sich **Warteschlangen**, die sich bis auf die Straße stauen können. Im Stau sinkt der Akku weiter (Heizung/Klima) → noch mehr Autos an der Säule.

**Fahrerstereotypen** (Auswahl, erweiterbar)

Jeder Stereotyp ist nur eine Kombination von Parametern des Fahrmodells (siehe 3.4), z. B. Wunschabstand, Beschleunigung, Reaktionszeit:

| Stereotyp           | Verhalten                                                                      |
|---------------------|--------------------------------------------------------------------------------|
| Drängler            | kleiner Abstand, starkes Beschleunigen und Bremsen → löst Stauwellen aus       |
| Gemütlicher         | fährt deutlich unter dem Limit, großer Abstand, neigt selbst zur Unaufmerksamkeit; bildet Kolonnen hinter sich und **provoziert** riskante Überholmanöver und Frust bei anderen |
| Spätzünder          | lange Reaktionszeit, fährt bei Grün verspätet los                              |
| Träumer             | unaufmerksam, verpasst Ausfahrten, bremst plötzlich                            |
| Unsicherer          | bremst vor jeder Kreuzung, zögert beim Abbiegen                                |
| Normalo             | Durchschnitt                                                                   |

- Bewusst **nicht** drin: ein eigener "Raser"-Stereotyp, Handy am Steuer und ähnliche Moralthemen.
- **Überhöhte Geschwindigkeit** gibt es trotzdem – als **Wahrscheinlichkeit**, die vom Tempolimit abhängt und nicht vom Charakter:
  - Jede Straße hat eine "gefühlt angemessene" Geschwindigkeit (abhängig von Straßentyp, Breite, Ausbau – eine vierspurige Hauptstraße fühlt sich nach 50–70 an).
  - Je weiter das Limit darunter liegt, desto höher die Wahrscheinlichkeit, dass gefahren wird, was sich richtig anfühlt, und desto größer die Überschreitung.
  - Grobe Startwerte zum Balancen:

    | Limit                       | Anteil zu schnell | typische Überschreitung |
    |-----------------------------|-------------------|-------------------------|
    | 70                          | ~5 %              | +5–10 km/h              |
    | 50                          | ~10 %             | +5–10 km/h              |
    | 30-Zone                     | ~40 %             | +10–15 km/h             |
    | 20 / verkehrsberuhigt       | ~60 %             | +10–20 km/h             |
    | Schrittgeschwindigkeit      | ~90 %             | "Schritt" wird als 20–30 interpretiert |

  - Verstärkt durch Stress (Verspätung), Frust (hinter einem Gemütlichen) und niedrige Aufmerksamkeit (Schild übersehen); Gemütliche und Unsichere halten sich eher daran.
  - Direkt vor einem bekannten festen Blitzer bremsen alle runter – der Laser erwischt sie überall sonst.
  - Fürs Spiel heißt das: **Absurd niedrige Limits sind die Bußgeld-Goldgrube.**
- **Aufmerksamkeit**: Jeder Fahrer hat eine Aufmerksamkeit (Startwert je nach Stereotyp – Träumer niedrig, Unsichere hoch).
  - Sie **sinkt durch Reizdichte**: Schilder, Ampeln, Tempolimitwechsel, Vorfahrtsänderungen und querende Radwege pro 100 m. Jeder Reiz kostet ein bisschen, viele Reize auf kurzer Strecke kosten überproportional.
  - Sie **sinkt zusätzlich** durch Stress (Verspätung) und Frust.
  - Sie **erholt sich** langsam auf ruhigen, gleichförmigen Abschnitten.
  - Niedrige Aufmerksamkeit → Schilder werden übersehen (→ zu schnell → Bußgeld), späteres Reagieren, verpasste Abbiegungen, mehr Unfälle.
  - **Bewährte "Schilderwald"-Kombi** (fürs Tutorial / als Achievement): 50 → 30 → "30 nur nachts" → Vorfahrtsstraße mit querenden Radwegen – alles auf 500 m, mit zwei Blitzern zwischendrin.
  - Dafür braucht es **zeitabhängige Tempolimits** ("30 von 22–6 Uhr"), die die Simulation mit der Spieluhr auswertet.
- **Frust-Mechanik**: Wer länger hinter einem deutlich langsameren Fahrzeug hängt, baut Frust auf → wird unaufmerksamer, startet Überholversuche (auf mehrspurigen Straßen per Spurwechsel, auf einspurigen riskant über die Gegenspur) oder fährt dichter auf. Kein Stereotyp ist also "harmlos" – jeder richtet auf seine Weise Schaden an.
- Die Mischung der Stereotypen kann je nach Szenario variieren (z. B. "Ferienbeginn: viele Unsichere mit Wohnwagen").

**Generation & Pannen**

| Generation | Zuverlässigkeit | Typische Panne                                                                                     |
|------------|-----------------|----------------------------------------------------------------------------------------------------|
| Modern     | regelmäßig Pannen | Elektronik- oder Softwarefehler → Auto bleibt **sofort stehen** und blockiert die Spur ("Bitte Neustart durchführen …", "Update wird installiert") |
| Alt        | sehr zuverlässig | fährt weiter, verliert aber ab und zu **Öl** → Ölspur auf dem Straßenabschnitt                      |

- **Liegengebliebenes Auto**: blockiert eine Spur, bis der Abschleppdienst kommt (der natürlich auch im Stau steht).
- **Ölspur**: Alle Fahrzeuge auf dem Abschnitt fahren langsamer und vorsichtiger, bis die Straßenmeisterei sie beseitigt hat.
- Pannenwahrscheinlichkeit ist pro Tick klein und hängt von der Generation ab (bei modernen Autos ggf. höher, je länger sie im Stau stehen – "Systemüberlastung").
- Im Cartoon-Stil: Warnblinker, Rauchwölkchen, Sprechblase mit Ladekringel bzw. eine glänzende Ölpfütze.
- Generation und Antriebsart werden unabhängig voneinander zugeordnet, aber Elektroautos sind fast immer "modern".

### 2.7 Umwelt: Verschmutzung & Lärm

Jedes Fahrzeug erzeugt pro Tick **Emissionen** und **Lärm**, abhängig von:

- Klasse und Antriebsart (LKW-Diesel ≫ PKW-Benzin ≫ Elektro)
- Fahrzustand: Leerlauf im Stau, Anfahren und Bremsen sind viel schlimmer als gleichmäßiges Rollen
- Zeit auf der Straße: Wer länger braucht, verbraucht mehr Sprit

Dadurch entsteht die gewollte **Abwärtsspirale**: mehr Stau → längere Fahrzeiten → mehr Verbrauch, Abgase und Lärm.

**Feinstaub: nicht nur aus dem Auspuff**

Feinstaub setzt sich aus drei Quellen zusammen:

| Quelle    | Entsteht durch                                   | Besonderheit                                                                 |
|-----------|--------------------------------------------------|------------------------------------------------------------------------------|
| Abgas     | Verbrennung (Benzin/Diesel)                      | Elektro: null                                                                |
| Reifen    | Abrieb, v. a. beim Beschleunigen, Bremsen, Abbiegen | Abhängig vom **Fahrzeuggewicht** → LKW sehr viel, Elektroautos (schwerer Akku) mehr als vergleichbare Verbrenner |
| Bremsen   | Abrieb bei jedem Bremsvorgang                    | Stop-and-go ist am schlimmsten; Elektroautos bremsen viel über den Motor (Rekuperation) → deutlich weniger Bremsstaub |

- Damit sind auch Elektroautos **nicht sauber**, nur anders schmutzig.
- Drängler (starkes Beschleunigen/Bremsen) erzeugen *selbst* spürbar mehr Reifen- und Bremsstaub als Gemütliche.
- Gemütliche verursachen dafür *indirekt* Emissionen: Die Kolonne hinter ihnen bremst ständig, fährt länger, und frustrierte Überholer beschleunigen hart.
- Jede Ampel, jeder Blitzer und jede Ölspur, die zum Bremsen zwingt, erhöht den Feinstaub.

Erfasste Kennzahlen pro Rasterzelle und gesamt:

- **CO₂** (Klima, nur Verbrenner)
- **Stickoxide (NOx)** (Abgas, vor allem Diesel)
- **Feinstaub** (Abgas + Reifen + Bremsen, im Detail nach Quelle aufschlüsselbar)
- **Lärm** (Motor + Rollgeräusch; bei Elektro nur Rollgeräusch)

Umsetzung:

- Die Stadt wird in ein Raster eingeteilt (z. B. 50 × 50 m). Emissionen und Lärm werden pro Zelle aufsummiert und bauen sich langsam wieder ab (Wind, Zeit).
- Anzeige als **umschaltbare Karten-Ebene** (Heatmap) je Kennzahl, im Cartoon-Stil als Smogwolken, Staubschwaden bzw. Schallwellen.
- Gesamtwerte im HUD: Sprit verbraucht, Strom geladen, CO₂, NOx, Feinstaub, Lärm-Durchschnitt.

### 2.8 Unfälle & Einsatzfahrzeuge

**Unfälle**

- Entstehen zufällig, die Wahrscheinlichkeit steigt mit:
  - Fahrerstereotyp (Drängler und Träumer häufiger; Gemütliche verursachen selten direkt einen Unfall, sind aber oft Auslöser – riskante Überholmanöver hinter ihnen – oder selbst unaufmerksam beteiligt)
  - Frust der Fahrer (siehe Frust-Mechanik in 2.6)
  - Ölspuren, dichtem Verkehr, häufigem Bremsen, verwirrenden Kreuzungen (z. B. viele Ampeln hintereinander)
- Ein Unfall blockiert eine oder mehrere Spuren, bis Polizei, Rettungswagen und Abschleppdienst fertig sind.
- **Gaffer-Effekt**: Auf der Gegenfahrbahn wird langsamer gefahren, weil alle hinschauen.
- Ton bleibt cartoonhaft: Blechschaden, Dampfwölkchen, wütende Fahrer – keine Verletzten im Bild.

**Einsatzfahrzeuge** (Rettungswagen, Polizei, Feuerwehr, Abschleppdienst, Straßenmeisterei)

- Starten an echten Standorten (Feuerwache, Polizeirevier, Rettungswache aus OSM) und fahren mit Blaulicht zum Einsatzort.
- Andere Fahrzeuge versuchen, **Platz zu machen bzw. eine Rettungsgasse** zu bilden – das klappt nur, wenn noch Platz ist. Im völligen Stau kommt niemand mehr durch.
- **Anfahrtszeit** wird gemessen und angezeigt. Dauert sie zu lange, gibt es eine Schlagzeile ("Rettungswagen steckt 40 Minuten in der Zerrennerstraße fest") – den Bürgerzorn lässt das aber kalt, solange es nur Einzelne betrifft (siehe 2.10).
- Dadurch entsteht die nächste Spirale: Stau → Unfälle → Einsatzfahrzeuge stecken fest → Unfallstellen bleiben länger blockiert → mehr Stau.

### 2.9 Pendler & Zuspätkommer

- Jede Fahrt hat einen **Zweck** (Arbeit, Einkaufen, Lieferung, Schule, Freizeit) und bei Arbeit, Schule und Lieferung eine **Soll-Ankunftszeit**.
- Wer später ankommt, ist **zu spät** – das wird pro Fahrt erfasst und aufsummiert:
  - Anzahl Zuspätkommer und verlorene Arbeitsstunden
  - daraus ein satirischer "volkswirtschaftlicher Schaden" in Euro im HUD
  - verpasste Lieferfenster bei Lieferwagen und LKW
- **Stress-Effekt** (Vorschlag): Wer merkt, dass es knapp wird, fährt nervöser – kürzere Abstände, abruptere Manöver, häufigere Spurwechsel (aber weiterhin ohne Rasen). Mehr Unfälle, mehr Brems- und Reifenstaub.
- Cartoon-Feedback: Sprechblasen wie "Chef ruft an …", "Meeting ohne mich", Uhren-Icon über dem Fahrzeug.
- Folgen fürs Spiel: Zuspätkommer erscheinen in der Statistik (und ggf. als Achievement). Den Bürgerzorn treiben sie erst, wenn ein großer Teil der Stadt betroffen ist (siehe 2.10). Die Lokalzeitung meldet Rekorde ("Halb Pforzheim kam heute zu spät").
- **Lerneffekt** (später): Pendler, die oft zu spät kommen, fahren am nächsten Spieltag früher los → Berufsverkehr verteilt sich anders, der Spieler muss nachziehen.

### 2.10 Bürgerzorn: Jeder ist sich selbst der Nächste

Grundprinzip: Die Bürger sind **Egoisten**. Was Einzelnen passiert, interessiert niemanden – auch nicht der Verletzte, der nicht rechtzeitig versorgt wird, oder das liegengebliebene Auto in der Nachbarstraße. Der Zorn steigt erst, **wenn es für (fast) alle schlimm ist**.

Modell:

**Bedürfnisse pro Haushalt**

Jeder Haushalt (Wohnort in einer Zone) hat **Bedürfnisse**, die jeweils nur aus der *eigenen* Erfahrung bewertet werden:

| Bedürfnis           | Gemessen an                                                          |
|---------------------|----------------------------------------------------------------------|
| Ruhe                | Lärm am eigenen Wohnort                                              |
| Frische Luft        | Feinstaub, NOx am eigenen Wohnort                                    |
| Schnell am Ziel     | eigene Fahrzeit und Verspätung im Vergleich zum Gewohnten            |
| Erreichbarkeit      | kommt man überhaupt noch zu Arbeit, Einkauf, Schule (Sperrungen, eingestürzte Brücken)? |
| Laden (Elektro)     | eigene Wartezeit an der Ladesäule                                    |
| Mobilität           | darf man überhaupt noch fahren? (Führerschein weg = großer Einbruch); Bußgelder schmerzen auch |

- Jedes Bedürfnis hat einen **Erfüllungsgrad** (0–100 %). Wird er nicht erreicht, steigt die **Unzufriedenheit** des Haushalts.
- Haushalte gewichten die Bedürfnisse unterschiedlich, je nach **Haushaltstyp**, z. B.:
  - Pendler: "schnell am Ziel" ist alles, Lärm egal
  - Familie: Ruhe und frische Luft wichtig, Erreichbarkeit (Schule, Kita) auch
  - Rentner: Ruhe über alles, Zeit spielt kaum eine Rolle
  - Gewerbe/Lieferdienst: Erreichbarkeit und Lieferzeiten
- Lage spielt mit: Wer an einer Hauptstraße wohnt, leidet unter Lärm und Abgasen, wer im Grünen wohnt, eher unter langen Wegen.
- Ein Haushalt gilt als **"unzufrieden"**, wenn seine gewichtete Unzufriedenheit einen Schwellwert überschreitet.

**Vom einzelnen Haushalt zum Bürgerzorn**
- Der Bürgerzorn hängt am **Anteil der unzufriedenen Haushalte** an der ganzen Stadt, und zwar stark nichtlinear:
  - unter ca. 30–40 % Unzufriedene: praktisch kein Zorn ("Ist doch nicht mein Problem")
  - darüber: Zorn steigt schnell an
  - ab einer Mehrheit: Petitionen, Demos, Lobby-Klagen
- Einzelereignisse (Unfall, steckengebliebener Rettungswagen, Panne) wirken **nicht direkt** auf den Zorn, sondern nur über ihre Folgen für viele (der Stau, den sie auslösen).
- Anzeige: Zufriedenheit als umschaltbare Karten-Ebene pro Zone (wo brodelt es?) und als Aufschlüsselung nach Bedürfnis und Haushaltstyp ("Die Rentner in der Nordstadt sind kurz vorm Leserbrief").
- Die Lokalzeitung berichtet trotzdem über Einzelschicksale – die Leserbriefe darunter sind aber satirisch gleichgültig ("Hauptsache, MEIN Parkplatz bleibt").

- **PR wirkt auf die Wahrnehmung**: Eine gekaufte Jubel-Story senkt den Zorn bzw. hebt die Schwelle vorübergehend an (siehe 2.11).

**Abwahlindex**

- Keine Wahlen, kein Wahlkampf – nur ein **Abwahlindex** (0–100), gut sichtbar im HUD.
- Er sammelt den Bürgerzorn über die Zeit auf: Hoher Zorn lässt ihn steigen, bei niedrigem Zorn sinkt er langsam wieder. Ein kurzer Ausrutscher ist also verkraftbar, Dauerzorn nicht.
- Überschreitet der Index den **Grenzwert** (z. B. 100), ist man raus → Game Over ("Der Gemeinderat hat Ihnen das Vertrauen entzogen").
- Vorher gibt es Warnstufen (z. B. ab 60 und 80) mit Schlagzeilen und Rücktrittsforderungen, damit der Spieler gegensteuern kann (PR, Maßnahmen zurücknehmen).
- Grenzwert und Geschwindigkeit des Anstiegs sind pro Szenario einstellbar (Schwierigkeitsgrad).

Folge fürs Gameplay: Der Spieler kann lange "unter dem Radar" agieren und einzelne Straßen gezielt lahmlegen und abkassieren. Gefährlich wird es erst, wenn das Chaos flächendeckend wird. Die zentrale Spannung: so viel Geld wie möglich herausholen, ohne dass der Abwahlindex überkocht.

### 2.11 Haushalt, Infrastruktur & Imagepflege

Als Stadtplaner muss man regelmäßig (in der **Haushaltsrunde**, z. B. jeden Sonntagabend, siehe 2.15) entscheiden, wohin das Geld fließt:

Einnahmen: fester Haushalt pro Runde + **Bußgelder** aus Blitzern (siehe unten) + **Parkgebühren** und **Strafzettel** + ggf. Fördermittel.

**Parken**

- Am Ziel muss geparkt werden. Der **Spieler legt fest, wie viele Parkplätze es gibt** – oder eben keine. Startwert sind die echten Parkhäuser und Parkplätze (aus OSM, `amenity=parking`); der Spieler kann Kapazität erhöhen, kürzen oder Parkplätze komplett abschaffen.
- Pro Parkplatz/Parkhaus legt der Spieler auch die **Gebühr** fest.
- Wenig Parkplätze = wenig Parkeinnahmen, aber viel Parksuchverkehr und viele Falschparker (= Strafzettel). Viele Parkplätze = Parkeinnahmen, aber flüssigeres Parken. Die Abwägung liegt beim Spieler.
- **Parkgebühren hoch / Parkplätze weg** → Fahrer suchen länger oder günstiger → **Parksuchverkehr**: Autos kreisen um den Block und verstopfen zusätzlich die Straßen.
- **Strafzettel**: Das Ordnungsamt (Politessen als eigene Einheiten, die man einstellen kann) verteilt Knöllchen an Falschparker – z. B. Lieferwagen in zweiter Reihe oder Leute, die nach langer Parkplatzsuche aufgeben und "nur kurz" irgendwo stehen bleiben.
- **Aufklärungsquote**: Nicht jeder Verstoß wird auch erwischt. Die Quote hängt von der **Größe des Ordnungsamts** ab:
  - Der Spieler bestimmt die Zahl der Mitarbeiter – jeder kostet Gehalt pro Haushaltsrunde.
  - Mehr Personal → höhere Quote → mehr Strafzettel-Einnahmen, aber mit abnehmendem Nutzen (die ersten Politessen bringen viel, die fünfzigste kaum noch etwas).
  - Gleiches Prinzip für die **Bußgeldstelle** hinter den Blitzern: Blitzerfotos müssen auch bearbeitet werden. Zu wenig Personal → Fälle bleiben liegen und **verjähren** ("Bußgeldstelle überlastet: 12.000 Blitzerfotos verjährt").
  - **Ausweg ohne Personal: Bearbeitungszeit verlängern.** Statt mehr Leute einzustellen, setzt der Spieler einfach die Bearbeitungszeit hoch (z. B. von 2 Wochen auf 6 Monate) und schiebt so die Verjährung hinaus:
    - Weniger Fälle verjähren, das Geld kommt trotzdem – nur später (Einnahmen verschieben sich in spätere Haushaltsrunden).
    - Der Stapel unbearbeiteter Fälle wächst sichtbar im Büro der Bußgeldstelle (Cartoon: Aktenberge bis zur Decke).
    - Nebeneffekt: Weil der Bescheid erst Monate später kommt, ändert niemand sein Verhalten → dieselben Leute werden immer wieder geblitzt. Lukrativ – bis plötzlich alle Bescheide auf einmal eintreffen und reihenweise Führerscheine weg sind (Zufriedenheit bricht schlagartig ein).
    - Nach oben gibt es eine gesetzliche Grenze; wer die reißt, verliert die Fälle trotzdem.
  - Die Quote wird im HUD angezeigt; Personal aufstocken braucht Zeit (Einstellung, Einarbeitung).
- Werkzeuge: Parkgebühren erhöhen, Parkplätze in Radspuren/Blumenkübel umwandeln, Brötchentaste abschaffen, Anwohnerparken ausweiten.
- Bedürfnisse: Parkplatzsuche zählt zu "schnell am Ziel", Gebühren und Knöllchen zu "Mobilität".

- **Balancing-Grundsatz: Überwachung muss sehr lukrativ sein** (wie in der Realität). Blitzer, Lasermessungen und Strafzettel spielen ihre Kosten schnell wieder ein und sind die mit Abstand beste Rendite im Spiel – der Haken liegt nur beim Unmut und beim nötigen Personal (Aufklärungsquote, siehe unten).
- Bußgelder sind die **Geldmaschine** des Spielers: Je mehr Blitzer an absurden Tempolimits, desto voller die Stadtkasse – und desto mehr Geld für noch mehr Maßnahmen oder PR.
- Der Preis dafür: unzufriedene Haushalte (Bußgelder, Führerscheinentzug). Klassischer Kreislauf: abkassieren → mit dem Geld PR kaufen → weiter abkassieren.

| Topf                     | Wirkung                                                                                          |
|--------------------------|--------------------------------------------------------------------------------------------------|
| Verkehrs"beruhigung"     | die eigentlichen Werkzeuge: Ampeln, Blitzer, Radspuren, …                                         |
| Infrastruktur-Erhalt     | hält Straßen, Ampeln und Brücken in Schuss                                                        |
| Imagepflege (PR)         | Agentur schreibt eine tolle Story für die Zeitung → alle finden einen erst mal super, der Zorn sinkt |

**Infrastruktur verfällt**

- Jeder Straßenabschnitt, jede Ampel und jede Brücke hat einen **Zustand** (0–100 %), der mit der Zeit und mit der Belastung sinkt.
- LKW nutzen Straßen und Brücken überproportional ab (Faustregel aus der Praxis: Schaden ~ Achslast hoch 4 → ein LKW "zählt" wie Tausende PKW).
- Folgen bei schlechtem Zustand:
  - **Schlaglöcher** → Tempo runter, mehr Bremsen (Feinstaub!), bei alten Autos mehr Ölverlust
  - **Ampelausfall** → Ampel blinkt gelb, Kreuzung wird zum Rechts-vor-links-Chaos
  - **Brücke marode** → erst Gewichtsbeschränkung (LKW müssen Umwege fahren), dann Sperrung, im Extremfall **Einsturz** (Kante fällt dauerhaft weg). Pforzheim hat mit Enz und Nagold reichlich Brücken.
- Verfall ist für das Spielziel praktisch, aber **für alle sichtbar** → er trifft viele gleichzeitig und treibt den Bürgerzorn. PR kann das eine Weile überdecken – bis die Wahrheit ans Licht kommt.

**Reaktionspflicht bei Schäden**

Wird ein Schaden gemeldet (Schlagloch, kaputte Ampel, marode Brücke), **muss** der Spieler innerhalb einer Frist reagieren:

| Reaktion                                   | Kosten       | Wirkung                                                                                   |
|--------------------------------------------|--------------|-------------------------------------------------------------------------------------------|
| Reparieren                                 | je nach Weg  | Schaden weg (siehe unten), währenddessen Baustelle                                        |
| Schild "Vorsicht Straßenschäden"           | sehr günstig | rechtlich abgesichert; gilt mit Tempolimit (z. B. 30) → mehr Stau. Der Schaden wächst aber weiter, und das Schild steht gerne jahrelang |
| Nichts tun                                 | erst mal 0 € | Unfälle durch Schlaglöcher, Schadensersatzklagen (kosten Geld), Schlagzeile "Stadt ignoriert Krater in der …" |

- Das Schild ist der klassische Stadtplaner-Trick: billig, bremst den Verkehr und verschiebt das Problem. Mit der Zeit stehen überall Schilder – im Cartoon-Stil gern schief und verblasst.
- Bei Brücken reicht ein Schild nur bis zu einem bestimmten Zustand, danach muss gesperrt oder repariert werden.
- **Kombi "Schild + absurdes Tempo + Blitzer"**: Das Tempolimit am Schild ist frei wählbar – gerne auch absurd niedrig (20, 10, Schrittgeschwindigkeit). Dazu ein Blitzer direkt dahinter:
  - Der Verkehr kriecht → viel Stau, viel Bremsstaub.
  - Je absurder das Limit, desto mehr Fahrer sind zu schnell (siehe Überschreitungs-Wahrscheinlichkeit in 2.6) → geblitzt → **Bußgeld-Einnahmen** fürs Stadtbudget.
  - Ohne laufende PR findet die Lokalzeitung es irgendwann heraus ("Abzocke-Blitzer am Schlagloch"). Mit PR-Vertrag wird daraus "Stadt investiert in Verkehrssicherheit".
  - **Punkte & Führerscheinentzug**: Wer zu oft geblitzt wird, sammelt Punkte und ist irgendwann den Führerschein los. Der Haushalt ist dann sehr unzufrieden (Bedürfnis "Mobilität", siehe 2.10). Zweischneidig fürs Spielziel: Ein Auto weniger auf der Straße heißt auch **weniger Stau** – der Fahrer steigt auf Bus, Taxi, Fahrrad oder das Auto des Nachbarn um (anfangs vereinfacht: Fahrt fällt weg bzw. wird zur Mitfahrt).

**Imagepflege (PR)**

- **Politik und Presse sind sich einig**: Wer regelmäßig über den Dienstleister gute Nachrichten liefert, hat eine freundliche Lokalzeitung. Kritische Presse gibt es nur, wenn man die PR einspart.
- PR ist deshalb eher ein **laufender Vertrag** als eine Einmalzahlung: Solange bezahlt wird, schreibt die Zeitung Jubel-Stories ("Pforzheim wird zur Modellstadt der Verkehrsberuhigung"). Kündigt man, kippt der Ton schnell.
- Wirkung: PR beeinflusst die **Wahrnehmung**, nicht die Realität. Sie dämpft den Zorn bzw. hebt die Schwelle an – aber die Haushalte spüren Lärm, Abgase und Verspätung trotzdem selbst. Ab einem gewissen Leidensdruck hilft auch die schönste Story nichts mehr ("Steht doch in der Zeitung, dass alles super ist – warum steh ich dann seit zwei Stunden hier?").
- **Totschlagargumente**: Eigene PR-Kampagne gegen den Unmut über Blitzer, Laser und Strafzettel. Wer regelmäßig mit Sprüchen in der Presse präsent ist, senkt die Unzufriedenheit beim Bedürfnis "Mobilität" (Bußgelder, Punkte):
  - "Wer sich an die Geschwindigkeit hält, muss sich um Blitzer keine Sorgen machen."
  - "Es geht uns ausschließlich um die Sicherheit unserer Kinder."
  - "Die Einnahmen fließen vollständig in die Verkehrssicherheit."
  - Wirkt nur, wenn regelmäßig wiederholt – ein einmaliger Spruch verpufft.
- Die PR-Kosten steigen mit der Lücke zwischen Story und Realität: Je schlimmer es wirklich ist, desto teurer wird der Dienstleister.

**Reparieren: drei Wege**

| Weg                          | Dauer                         | Kosten   | Haken                                                                 |
|------------------------------|-------------------------------|----------|-----------------------------------------------------------------------|
| Normale Ausschreibung        | einige Wochen/Monate          | mittel   | Baustelle während der Arbeiten → noch mehr Stau (aus Spielersicht ein Bonus) |
| Turbo-Baukonsortium (fiktiv) | über Nacht                    | hoch     | macht die Stadt in einer Nacht wieder fit; Qualität/Folgekosten fraglich, ggf. Abhängigkeit ("Wartungsvertrag") |
| Planfeststellungsverfahren   | ca. 40 Jahre                  | gering   | Fortschrittsbalken mit "Fertigstellung voraussichtlich 2066"; Gutachten, Einsprüche, Neuplanung als Zufallsereignisse |

- Der Turbo-Dienstleister wird bewusst als **fiktive Firma** dargestellt (kein echtes Land, keine echte Firma) – passt zur Regel "keine echten Marken/Personen".
- Das Planfeststellungsverfahren ist vor allem ein Gag, kann aber strategisch sein: Die Straße ist "in Planung" und darf so lange marode bleiben, ohne dass man Ärger bekommt ("Wir kümmern uns ja schon").

### 2.12 Bußgeldwesen: Die kleinen Fische bringen das Geld

**Überschreitungs-Stufen** (angelehnt an einen Bußgeldkatalog, Beträge fiktiv und zum Balancen)

| Überschreitung | Folge                         | Häufigkeit | Beitrag zur Kasse |
|----------------|-------------------------------|------------|-------------------|
| bis 10 km/h    | Verwarnungsgeld, keine Punkte | sehr hoch  | **der Großteil**  |
| 11–20 km/h     | Verwarnungsgeld/Bußgeld       | mittel     | ordentlich        |
| 21–30 km/h     | Bußgeld + Punkt               | selten     | mittel            |
| > 30 km/h      | Bußgeld, Punkte, Fahrverbot   | sehr selten | kaum der Rede wert |

- **Transparenz**: Die Blitzer-Statistik zeigt pro Gerät und gesamt, wie viele Verstöße in welcher Stufe gemessen wurden und wie viel Geld jede Stufe gebracht hat (Histogramm). Man sieht auf einen Blick: Das Geld kommt von den 5–10 km/h zu Schnellen.
- **Erfolgsmeldungen** in der Presse greifen dagegen immer die Ausreißer heraus: "Wir haben einen mit 90 in der 30er-Zone erwischt!" – perfekt kombinierbar mit den Totschlagargumenten aus 2.11.

**Wie scharf ist das Ordnungsamt?**

Der Spieler stellt die **Schärfe** ein, z. B. in drei Stufen (kulant / normal / gnadenlos):

| Stellschraube            | kulant                   | gnadenlos                                   |
|--------------------------|--------------------------|---------------------------------------------|
| Messtoleranz             | großzügig                | gesetzliches Minimum                        |
| Strafzettel beim Parken  | erst nach 15 Min.        | ab der ersten Minute, auch für "nur kurz"   |
| Einsprüche               | werden oft durchgewunken | werden grundsätzlich abgelehnt              |
| Wirkung                  | weniger Geld, weniger Unmut | mehr Geld, mehr Unmut, mehr Personalbedarf |

**Fahrer nicht ermittelbar** (unscharfes Foto, Halter schweigt)

| Reaktion                     | Wirkung                                                                                  |
|------------------------------|------------------------------------------------------------------------------------------|
| Verfahren einstellen         | kein Geld, kein Aufwand, kein Unmut                                                      |
| Zeugenfragebogen an Halter   | etwas Personal, ein Teil der Fälle wird doch bezahlt                                     |
| Fahrtenbuchauflage androhen  | die meisten Halter "erinnern sich" plötzlich → hohe Quote, aber deutlich mehr Unmut und Personalaufwand |

- Die Reaktion ist eine Grundeinstellung des Ordnungsamts und kann jederzeit geändert werden.
- Alles hängt an der Aufklärungsquote und der Bearbeitungszeit der Bußgeldstelle (siehe 2.11).

### 2.13 Umleitungen & Ereignisse

**Umleitungen**

- Bei jeder Baustelle (eigene oder Reparatur) legt der Spieler die **Umleitungsstrecke** selbst fest, per Klick Abschnitt für Abschnitt auf der Karte.
- Die Umleitung darf auch über **Nebenstraßen** laufen, die sonst nicht simuliert werden (siehe 3.5) – sie werden für die Dauer der Umleitung "zugeschaltet".
- **LKW bleiben stecken**: Nebenstraßen haben Breite, Höchstgewicht, Durchfahrtshöhe und enge Kurven. Passt der LKW nicht, bleibt er hängen und blockiert die Umleitung komplett (Cartoon: LKW eingeklemmt zwischen zwei Häusern, Fahrer telefoniert verzweifelt). Befreiung nur per Abschleppdienst/Kran – der natürlich auch erst durch den Stau muss.
- **Blitzer-Umleitung**: Umleitung durch einen verkehrsberuhigten Bereich (Schrittgeschwindigkeit, ~90 % zu schnell – siehe 2.6) mit Laser und Blitzern → Kasse klingelt.
- Navi-Effekt: Ortskundige ignorieren die Umleitung und suchen eigene Schleichwege, Navis leiten teils mitten durch die Baustelle.

**Ereignisse** (zufällig oder durch die Lage ausgelöst)

| Ereignis                   | Wirkung                                                                                     |
|----------------------------|---------------------------------------------------------------------------------------------|
| Militante Radfahrer        | Rad-Korso blockiert eine Hauptstraße im Schritttempo, ignoriert Ampeln und **grundsätzlich auch Radwege** – gefahren wird auf der Fahrbahn, auch wenn daneben ein frisch gebauter Radweg liegt. Tritt auf, wenn **ständig Stau** ist (Dauerstau über mehrere Spieltage) oder die Leute **zu viel Freizeit** haben – z. B. Wochenende/Ferien oder viele Bürger, die ihren Führerschein los sind und jetzt aufs Rad umgestiegen sind |
| Spontandemo                | sperrt eine Straße oder Kreuzung für eine Weile; je höher der Bürgerzorn, desto häufiger und größer – zum Glück ist die Demo selbst auch ein Stau-Bringer |
| Stadtfest, Marathon, Wochenmarkt | geplante Sperrungen, im Spielkalender vorher sichtbar                                |
| Schwertransport            | kriecht nachts mit Polizeibegleitung über die Hauptachsen                                   |

- Ereignisse sind fiktiv und cartoonhaft – keine echten Gruppen, Organisationen oder Personen.

### 2.14 Wertung: Geld & Reputation

**Amtszeit**: Ein Spiel dauert eine feste Anzahl Spieltage (z. B. 8 Wochen = 56 Tage, siehe 2.15). Wer bis zum Ende durchhält, wird gewertet.

**Geld**

- Zählt das bis zum Ende **eingenommene Geld** abzüglich Ausgaben (Kassenstand am Ende der Amtszeit).
- Quellen: Bußgelder, Strafzettel, Parkgebühren, ggf. Fördermittel (siehe 2.11, 2.12).

**Reputation**

- Reputation ist das **öffentliche Image** des Spielers – nicht zu verwechseln mit der echten Zufriedenheit der Haushalte:
  - **Zufriedenheit / Bürgerzorn** = wie es den Leuten wirklich geht → treibt den Abwahlindex.
  - **Reputation** = was in der Zeitung steht und wie man dasteht → zählt für den Sieg.
- Reputation steigt durch PR-Verträge, Erfolgsmeldungen ("90 in der 30er erwischt!"), Totschlagargumente, Spatenstiche und Eröffnungen (auch von Dingen, die nie fertig werden).
- Reputation sinkt durch kritische Presse (wenn die PR eingespart wird), sichtbare Katastrophen ohne passende Story (Brückeneinsturz) und Enthüllungen.
- Damit ist PR doppelt wertvoll: Sie dämpft den Zorn *und* bringt Punkte.

**Punktzahl**

- Vorschlag: **Punkte = Geld × Reputationsfaktor** (z. B. Reputation 0–100 % → Faktor 0,5–2,0). Reich, aber verhasst gibt wenig Punkte, beliebt, aber pleite auch.
- Ergebnisbildschirm im Stil einer Abschiedsrede mit Ehrenbürgerschaft – oder eben nicht.

**Spannungen, die daraus entstehen**

- **Totaler Stillstand bringt kein Geld**: Wer steht, fährt nicht zu schnell und wird nicht geblitzt. Die lukrativste Stadt ist also eine, in der *gerade noch* gefahren wird – zäh, aber mit vielen kleinen Überschreitungen.
- **PR kostet Geld**: Jeder Euro für die Agentur fehlt am Ende in der Kasse – aber ohne PR gibt es keine Reputation und einen steigenden Abwahlindex.
- **Infrastruktur verrotten lassen** spart Geld, kostet aber Reputation, sobald etwas Sichtbares passiert.

### 2.15 Zeitmodell: Es werden Tage simuliert

- Die Simulation läuft **Tag für Tag** durch, jeder Tag mit vollem 24-Stunden-Verlauf: Berufsverkehr morgens und abends, Mittagsflaute, Nachtruhe (wichtig für "30 nur nachts").
- **Zeitraffer**: Pause, 1x, 2x, 4x, 8x; ruhige Phasen (Nacht) laufen automatisch schneller. Ziel: ein Spieltag dauert ca. 5–10 Minuten echte Zeit.
- **Wochentage** unterscheiden sich: Werktage mit Pendlern, Samstag mit Einkaufsverkehr, Sonntag mit Freizeitverkehr (→ militante Radfahrer).
- **Haushaltsrunde** einmal pro Spielwoche (z. B. Sonntagabend): Budget verteilen, Personal einstellen, PR-Verträge verlängern.
- **Amtszeit** in Spieltagen (z. B. 8 Wochen), pro Szenario einstellbar.
- **Langsame Prozesse werden auf Spieltage skaliert** – die Spielwelt ist ein "gestauchter" Kalender:
  - Verfall: Schlaglöcher entstehen innerhalb weniger Spieltage, Brücken verfallen über Wochen.
  - Ausschreibung/Reparatur: einige Spieltage; Turbo-Baukonsortium: über Nacht.
  - Bearbeitungszeit Bußgeldstelle: Tage statt Monate.
  - **Planfeststellungsverfahren**: bleibt bei "40 Jahren" – wird also in einer Amtszeit nie fertig. Genau das ist der Witz.
- Zwischen den Tagen gibt es einen kurzen **Tagesabschluss**: Zeitung vom nächsten Morgen, Einnahmen des Tages, Bürgerzorn-Trend.

## 3. Technische Architektur (grob)

### 3.1 Grundsatzentscheidung: Kotlin first, Web später

**Entscheidung:** Das Spiel wird zuerst als **Kotlin-Desktop-Anwendung** gebaut. Eine Webapp kommt später ggf. obendrauf.
Die **Simulation wird komplett selbst geschrieben** (reines Kotlin, keine fremde Verkehrssimulations-Engine wie SUMO oder MATSim).

Damit der Weg ins Web offen bleibt, wird das Projekt als **Kotlin Multiplatform (KMP)** aufgesetzt:

- `sim-engine` liegt in `commonMain` → reines Kotlin ohne Plattformabhängigkeiten.
- Derselbe Code kann später laufen:
  - im Desktop-Client (JVM) – **jetzt**
  - im Browser (Kotlin/Wasm bzw. JS) – **später**, ohne die Logik neu zu schreiben
  - auf einem Micronaut-Server (JVM) – **später**, falls Online-Highscores, Multiplayer oder Server-Simulation gewünscht

Die Frage "Simulation im Browser oder auf dem Server?" wird damit vertagt, statt sie jetzt entscheiden zu müssen.

### 3.2 Client-Technik: Optionen

| Option                              | Pro                                                                 | Contra                                                        |
|-------------------------------------|---------------------------------------------------------------------|---------------------------------------------------------------|
| **Compose Multiplatform** ✅ (Vorschlag) | Von JetBrains, stabil; super für UI (Menüs, Werkzeugleiste, Zeitung); Skia-Canvas für Karte & Sprites; Desktop jetzt, Web (Wasm) später | Kein Game-Framework → Game-Loop, Kamera, Sprites selbst bauen |
| KorGE                               | Kotlin-Game-Engine, Sprites/Animationen/Tweening eingebaut, Multiplatform inkl. Web | Kleinere Community, UI-Komponenten weniger komfortabel        |
| LibGDX (mit Kotlin/KTX)             | Sehr ausgereift und schnell, große Community                        | Java-zentriert, Web-Export (TeaVM) umständlich                |

Vorschlag: **Compose Multiplatform**, weil das Spiel viel UI hat (Werkzeuge, Statistiken, Zeitung, Dialoge) und die Grafik eher 2D-Karte + Sprites ist. Früh per Spike prüfen, ob der Compose-Canvas einige Tausend animierte Fahrzeuge flüssig schafft. Falls nicht, wäre KorGE der Plan B.

### 3.3 Architekturübersicht

```
┌──────────────────────── Desktop-App (JVM) ─────────────────────────┐
│  ui (Compose Multiplatform)                                        │
│   ├─ Karten-Renderer: Straßen, Gebäude, Fahrzeuge im Cartoon-Stil  │
│   ├─ Kamera: Zoom/Pan, Interpolation der Fahrzeuge                 │
│   └─ HUD: Werkzeugleiste, Stau-O-Meter, Bürgerzorn, Zeitung        │
│                 │ Befehle ("Ampel an Kreuzung X")   ▲ Zustand      │
│                 ▼                                   │              │
│  game (Spielregeln: Budget, Bürgerzorn, Abwahlindex, Events, Level)│
│                 │                                   ▲              │
│                 ▼                                   │              │
│  sim-engine (reines Kotlin, eigener Thread, fester Tick-Takt)      │
│   ├─ Straßengraph, Fahrzeuge, Kreuzungen, Ampeln                   │
│   └─ Routing + Re-Routing ("Navi")                                 │
└────────────────────────────────────────────────────────────────────┘
                ▲
                │ vorab erzeugte Stadtdatei (z. B. pforzheim.city)
        osm-import (Kotlin-CLI) ◄── OpenStreetMap-Daten (Geofabrik-Extract)
```

- **Schichten:** `sim-engine` kennt nur Verkehr. `game` kennt die Spielregeln und übersetzt Werkzeuge in Änderungen am Straßennetz. `ui` zeigt nur an und schickt Befehle.
- **Kommunikation** zwischen UI und Simulation über Befehle und Zustands-Snapshots, nicht über direkten Zugriff. Dadurch kann die Simulation später ohne Umbau hinter ein Netzwerk (Micronaut + WebSocket) wandern.
- **Speichern:** Spielstände lokal als Datei (z. B. JSON über kotlinx.serialization).
- **Micronaut** wird erst gebraucht, wenn es Online-Funktionen gibt (Highscores, Städte-Download, Multiplayer, Web-Variante mit Server-Simulation).

### 3.4 Simulation – Ansatz

- **Mikroskopisch light**: Fahrzeuge als Agenten auf Kanten eines Graphen, vereinfachtes Fahrzeugfolgemodell (z. B. IDM – Intelligent Driver Model).
- Fahrerstereotypen = Parametersätze des IDM (Wunschgeschwindigkeit, Mindestabstand, Zeitlücke, max. Beschleunigung, komfortable Bremsung) + zusätzlich Reaktionszeit und "Unaufmerksamkeits-Ereignisse".
- Fahrzeugklasse liefert Länge, max. Beschleunigung und Höchstgeschwindigkeit.
- Emissions- und Lärmmodell als eigener, einfacher Rechenschritt pro Tick (Verbrauch ≈ f(Klasse, Antrieb, Geschwindigkeit, Beschleunigung)). Reifenabrieb ≈ f(Gewicht, |Beschleunigung|, Kurven), Bremsabrieb ≈ f(Gewicht, Bremsleistung, Rekuperationsanteil). Fahrzeugklasse und Antrieb liefern dafür das Gewicht. Keine wissenschaftliche Genauigkeit, nur glaubwürdige Größenordnungen.
- Ladesäulen als eigene Knoten mit begrenzter Anzahl Ladeplätze und Warteschlange.
- Pannen als Zufallsereignisse (mit festem Seed, damit es deterministisch bleibt): erzeugen temporäre Hindernisse (gesperrte Spur) bzw. Abschnittsmodifikatoren (Ölspur → niedrigere Geschwindigkeit). Abschleppdienst und Straßenmeisterei sind normale Fahrzeuge in der Simulation.
- Infrastruktur-Zustand als Attribut an Kanten (Straßenabschnitt, Brücke) und Knoten (Ampel). Verschleiß pro Tick aus Verkehrsbelastung (gewichtet nach Achslast), Schwellwerte lösen Effekte aus (Tempo-Malus, Ampel aus, Gewichtslimit, Sperrung).
- Unfälle funktionieren genauso (Hindernis für mehrere Spuren). Einsatzfahrzeuge haben Vorrang: Fahrzeuge vor ihnen weichen aus bzw. fahren an den Rand, wenn auf der Kante Platz ist. Einfaches Modell: Ausweichen nur bei mehrspurigen Straßen oder geringer Dichte.
- Routing: A* / Dijkstra auf dem Graphen, periodisches Re-Routing (Navi-Effekt).
- Verkehrsnachfrage: Fahrten werden aus einem Tagesplan pro Zone erzeugt (Start-Zone, Ziel-Zone, Zweck, Abfahrtszeit, Soll-Ankunft). Verspätung = Ist-Ankunft − Soll-Ankunft. Stress = Funktion der erwarteten Verspätung, verschiebt die Fahrparameter.
- Frust pro Fahrzeug: steigt, solange die eigene Geschwindigkeit deutlich unter der Wunschgeschwindigkeit liegt und ein langsameres Fahrzeug vorausfährt; sinkt bei freier Fahrt. Ab Schwellwerten: Überholversuch, Aufmerksamkeit sinkt, Abstand sinkt. Stress und Frust addieren sich.
- Wunschgeschwindigkeit pro Fahrzeug und Kante = Limit + ggf. Überschreitung. Die Überschreitung wird beim Betreten der Kante gewürfelt (Wahrscheinlichkeit aus Abstand Limit ↔ "gefühlt angemessen", modifiziert durch Stereotyp, Stress, Frust). Blitzer/Laser prüfen gegen das Limit inkl. Toleranz und protokollieren jede Messung (Fahrzeug-ID, Überschreitung, Uhrzeit) für Statistik und Bußgeldstelle.
- Aufmerksamkeit pro Fahrzeug: wird pro Tick aus der Reizdichte der aktuellen Kante (vorberechnet beim Bauen von Schildern/Ampeln) reduziert und regeneriert sonst. Wahrscheinlichkeit, ein Schild zu übersehen = f(Aufmerksamkeit).
- Tempolimits können zeitabhängig sein (Zeitfenster) und werden gegen die Spieluhr geprüft.
- Kreuzungen: Vorfahrt, Ampelphasen, Kapazitätsgrenzen, Abbiegebeziehungen.
- Fester Tick-Takt (z. B. 10 Ticks/s), deterministisch (wichtig für Tests, Replays, später ggf. Server-Validierung).
- Pause und Zeitraffer (1x bis 8x) über die Tick-Rate bzw. mehrere Ticks pro Frame; Spieluhr mit Tag, Wochentag und Uhrzeit (siehe 2.15).
- Datenstrukturen performance-bewusst (Arrays statt vieler kleiner Objekte).
- Ziel: einige Tausend Fahrzeuge gleichzeitig.
- Wirklich realistisch muss es nicht sein – es muss **glaubwürdig und lustig** sein.

### 3.5 Kartendaten

- Quelle: OpenStreetMap (Lizenz **ODbL** → Attribution im Spiel Pflicht).
- `osm-import` als eigenes Kotlin-CLI: Straßen, Spuren, Einbahnstraßen, Ampeln, Tempolimits, Ladesäulen (`amenity=charging_station`), Parkhäuser/Parkplätze (`amenity=parking`), Feuerwachen/Polizei/Rettungswachen (`amenity=fire_station`, `amenity=police`, `emergency=ambulance_station`), Gebäude, Grünflächen, Gewässer (die Enz!) extrahieren.
- **Erst mal nur Hauptstraßen.** Nebenstrecken werden vorerst ignoriert. Simuliert werden diese OSM-Klassen:
  - `trunk` / `trunk_link` (Bundesstraßen-Achsen wie B10, B294, B463)
  - `primary` / `primary_link`
  - `secondary` / `secondary_link`
  - `tertiary` (ggf. erst später, falls das Netz sonst zu dünn wirkt)
  - Die A8 nur als **Rand**: Dort kommt Verkehr in die Stadt und verlässt sie wieder (Quellen/Senken), sie wird nicht selbst simuliert.
- Die Klassen sind im Import **konfigurierbar**, damit man später Nebenstraßen einfach dazunehmen kann.
- **Wohngebiete als Zonen:** Verkehr entsteht und endet in Zonen (Wohnen, Arbeit, Einkaufen), die an die nächste Hauptstraße angebunden werden. Die Nebenstraßen darin werden nicht simuliert.
- Nebenstraßen und Gebäude dürfen trotzdem **als Kulisse** gezeichnet werden, damit die Karte nicht leer aussieht.
- Nebenstraßen werden außerdem als **passive Kanten** importiert (mit `maxwidth`, `maxweight`, `maxheight`, Kurvenradien soweit ableitbar). Sie werden nur aktiviert, wenn der Spieler eine Umleitung darüber legt (siehe 2.13).
- Auswirkung aufs Spiel: Die "Schleichwege" der Navi-KI laufen vorerst nur über alternative Hauptstraßen. Echte Schleichwege durch Wohngebiete kommen dazu, sobald Nebenstraßen simuliert werden (gutes Erweiterungs-Feature).
- Vereinfachen: Graph ausdünnen (Zwischenpunkte ohne Kreuzung zusammenfassen), Koordinaten in ein lokales metrisches System umrechnen.
- Ergebnis: kompakte Stadtdatei pro Stadt, die das Spiel mitliefert. Später kann man die Dateien auch von einem Server laden.

### 3.6 Repo-Struktur (Vorschlag)

```
traffic-maniac/
├─ docs/                    # Pläne, Konzepte, ADRs
├─ sim-engine/              # KMP, commonMain: reine Simulation
├─ game/                    # KMP, commonMain: Spielregeln, Werkzeuge, Level
├─ ui/                      # Compose Multiplatform: Renderer, HUD
├─ desktop-app/             # JVM-Einstiegspunkt, Packaging (Windows/Linux/macOS)
├─ osm-import/              # JVM-CLI: OSM → Stadtdatei
└─ (später) server/         # Micronaut: Highscores, Städte, Multiplayer
   (später) web-app/        # Compose Wasm oder Angular
```

- Ein Gradle-Multimodul-Projekt (Kotlin DSL, Version Catalog).
- Tests vor allem in `sim-engine` und `game` (deterministisch → gut testbar).

## 4. Roadmap

Jede Version hat einen eigenen Plan mit Ziel, Umfang, Arbeitspaketen, Abnahmekriterien, Risiken und offenen Fragen:

| Version | Thema                    | Kurz                                                                                     | Plan |
|---------|--------------------------|------------------------------------------------------------------------------------------|------|
| 0.1     | Der spielbare Kern       | Hauptstraßen, PKW/LKW, 3 Stereotypen, Tempolimit/Ampel/Blitzer, Bußgelder, Zufriedenheit, Abwahlindex, PR, Wertung, Tage | [v0.1-kern.md](versions/v0.1-kern.md) |
| 0.2     | Fahrer & Überwachung     | alle Stereotypen, Aufmerksamkeit, Frust, Stress, Schilderwald, Laser, Bußgeldstelle, Ordnungsamt, Führerscheinentzug | [v0.2-fahrer-ueberwachung.md](versions/v0.2-fahrer-ueberwachung.md) |
| 0.3     | Bürger & Umwelt          | Zensus-Daten, Haushaltstypen, alle Bedürfnisse, Pendler, Emissionen, Feinstaub, Lärm, Heatmaps | [v0.3-buerger-umwelt.md](versions/v0.3-buerger-umwelt.md) |
| 0.4     | Fahrzeugvielfalt & Chaos | Lieferwagen, Bus, Elektro + Ladesäulen, Generationen + Pannen, Unfälle, Einsatzfahrzeuge | [v0.4-vielfalt-chaos.md](versions/v0.4-vielfalt-chaos.md) |
| 0.5     | Stadtverwaltung          | Verfall, Reaktionspflicht, Reparaturwege, Parken & Strafzettel, Umleitungen              | [v0.5-stadtverwaltung.md](versions/v0.5-stadtverwaltung.md) |
| 0.6     | Ereignisse & Presse      | Rad-Korsos, Demos, Stadtfeste, Pressemodell, Reputation, Achievements                    | [v0.6-ereignisse-presse.md](versions/v0.6-ereignisse-presse.md) |
| 0.7     | Cartoon-Look & Sound     | Kartenstil, Sprites, Animationen, Effekte, UI-Theme, Sound                               | [v0.7-cartoon-look.md](versions/v0.7-cartoon-look.md) |
| 1.0     | Release                  | Tutorial, Szenarien, Spielstände, Balancing, Packaging                                   | [v1.0-release.md](versions/v1.0-release.md) |
| später  | Online & Web             | Micronaut-Backend, weitere Städte, Web-Variante, Multiplayer                             | [spaeter-online-web.md](versions/spaeter-online-web.md) |

Reihenfolge-Logik: Zuerst alles, was die **Geldmaschine** (0.2) und den **Gegendruck** (0.3) vertieft, denn das ist der Kern des Spiels. Chaos und Verwaltung (0.4–0.6) bauen darauf auf. Die Optik kommt bewusst spät, damit nicht für Systeme gezeichnet wird, die sich noch ändern.

## 5. Risiken

- **Performance** von Simulation und Rendering bei realem Straßennetz (deutlich entschärft, weil vorerst nur Hauptstraßen simuliert werden).
- **Netz zu dünn**: Nur mit Hauptstraßen gibt es evtl. zu wenige Ausweichrouten → zu schneller Stillstand, zu wenig Spieltiefe. Gegenmittel: `tertiary` dazunehmen.
- **Compose als Game-Renderer**: nicht dafür gemacht → früh testen, Plan B KorGE.
- **OSM-Datenqualität** (Spuren, Abbiegebeziehungen oft unvollständig) → sinnvolle Defaults nötig.
- **Balancing**: Es darf keine einzelne dominante Strategie geben (z. B. "nur Blitzer + PR"). Geld, Reputation und Abwahlindex müssen sich gegenseitig in Schach halten.
- **Web-Portierung später**: Gelingt nur, wenn `sim-engine` und `game` wirklich plattformfrei bleiben (keine JVM-only-Bibliotheken in `commonMain`).
- **Rechtliches**: ODbL-Attribution; Parodie ohne echte Marken/Vereine/Personen.

## 6. Offene Fragen

- Länge der Amtszeit und genaue Formel für die Punktzahl (Geld × Reputationsfaktor?) → im Balancing festlegen.
- Neue Werkzeuge rund um Elektro: Ladesäule bauen (an ungünstiger Stelle), Ladesäule "defekt", Ladesäule nur mit App XY?
- Haushalte: Wie viele werden simuliert? Vorschlag: nicht jeden echten Haushalt, sondern repräsentative Haushalte pro Zone mit einer Gewichtung (z. B. 1 simulierter Haushalt = 50 echte).
- Fahrzeugmix (Anteil Elektro, LKW, modern/alt usw.): fest pro Szenario oder ändert er sich im Spielverlauf?
- Wo genau liegt die Schwelle für den Bürgerzorn (Anteil Betroffener)? → im Balancing ermitteln, pro Szenario einstellbar.
- Kann der Spieler Pannen beeinflussen? (z. B. Werkzeug "Funkloch" → mehr Softwarefehler, oder "Schlaglöcher" → mehr Ölverlust bei alten Autos)
- Welche Desktop-Plattformen sollen unterstützt werden (Linux, Windows, macOS)?
- Wie stark sollen ÖPNV, Radfahrer und Fußgänger simuliert werden (oder nur als Hindernis)?
- Multiplayer irgendwann gewünscht? (Würde später Micronaut + Server-Simulation bedeuten.)
- Spätere Web-Variante: Compose Wasm (gleicher Code) oder Angular (separates Frontend)?
