# traffic-maniac

Eine Parodie auf klassische Verkehrssimulatoren: Statt den Verkehr einer echten Stadt flüssig zu halten, bremst man ihn mit Ampeln, Tempolimits, Blitzern, Radspuren und Baustellen aus – bis hin zum Stillstand.

Gewonnen hat aber nicht, wer die Stadt am besten lahmlegt, sondern wer dabei **am meisten Geld verdient und sich am besten präsentiert**. Ob die Stadt am Ende in Trümmern liegt oder strahlt, ist völlig egal.

Erste Stadt: **Pforzheim** (Straßennetz aus OpenStreetMap).

## Status

Planungsphase – noch kein Code.

## Dokumentation

- [Gesamtplan](docs/plan.md) – Spielprinzip, Architektur, Roadmap
- [Versionspläne](docs/versions/) – Umfang, Arbeitspakete und Abnahmekriterien pro Version, beginnend mit [Version 0.1](docs/versions/v0.1-kern.md)

## Technik (geplant)

- Kotlin Multiplatform, Gradle
- Desktop-Client mit Compose Multiplatform, 2D-Draufsicht im Cartoon-Stil
- Eigene Verkehrssimulation in reinem Kotlin
- Kartendaten aus OpenStreetMap
- Später optional: Micronaut-Backend und Web-Variante

## Lizenz

Apache License 2.0, siehe [LICENSE](LICENSE).

Kartendaten © OpenStreetMap-Mitwirkende, verfügbar unter der [Open Database License (ODbL)](https://www.openstreetmap.org/copyright).
