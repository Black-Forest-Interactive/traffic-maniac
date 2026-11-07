# Grafiken (SVG)

Liegt hier eine SVG-Datei mit einem der folgenden Namen, verwendet das Spiel sie statt des selbst gezeichneten
Symbols. Fehlt sie oder ist sie nicht lesbar, wird wie bisher gezeichnet – Grafiken können also nach und nach kommen.
Beim Start meldet das Spiel, wie viele Grafiken es gefunden hat.

Vorgaben: transparenter Hintergrund, kein Text, Motiv zentriert mit etwa 5 % Rand. Fahrzeuge in exakter Draufsicht,
**Front nach rechts**. Das Spiel skaliert auf die Fahrzeuggröße und dreht in Fahrtrichtung; Bremslichter, Blaulicht,
Warnblinker und Pannen-Symbole zeichnet es weiterhin selbst darüber.

## Fahrzeuge (Draufsicht, Front rechts)

| Datei | Inhalt |
|-------|--------|
| `car_red.svg`, `car_blue.svg`, `car_yellow.svg`, `car_green.svg`, `car_purple.svg`, `car_white.svg` | PKW; die Fahrzeugfarbe wird der nächstliegenden zugeordnet |
| `car_electric.svg` | Elektroauto |
| `van.svg` | Lieferwagen |
| `truck.svg` | LKW |
| `ambulance.svg` | Rettungswagen |
| `police.svg` | Streifenwagen |
| `fire_engine.svg` | Löschfahrzeug |
| `tow_truck.svg` | Abschleppwagen |
| `road_service.svg` | Straßenmeisterei |
| `bike_corso.svg` | Rad-Korso (Gruppe Radfahrer) |
| `heavy_transport.svg` | Schwertransport |

## Karte (Ansicht wie beschrieben)

| Datei | Inhalt |
|-------|--------|
| `signal_red.svg`, `signal_red_yellow.svg`, `signal_yellow.svg`, `signal_green.svg`, `signal_off.svg` | Ampel von vorn, deutsches Gehäuse (hochkant, etwa 2 : 5) |
| `camera_fixed.svg`, `camera_laser.svg` | Blitzer, Lasermessung |
| `camera_trailer.svg` | Blitzeranhänger (Seitenansicht, etwa 3 : 2); fehlt er, wird ein Anhänger mit Blitzer gezeichnet |
| `excavator.svg` | Baustelle |
| `oil_spill.svg`, `accident.svg`, `breakdown.svg` | Vorfälle (quadratisch) |
| `truck_stuck.svg` | eingeklemmter LKW (2 : 1) |
| `anger_cloud.svg` | Wutwolke (4 : 3) |
| `parking.svg`, `charging_station.svg` | Parkplatz, Ladesäule |

`tool_trailer.svg` ist das Werkzeug-Icon für den Anhänger (sonst wird `tool_camera.svg` verwendet).

Weitere Namen aus der Gesamtliste (Schilder, Bauamt, Ereignisse, Werkzeugleiste, HUD) werden eingebunden, sobald
es die Grafiken gibt.
