# Drittanbieter-Komponenten

Traffic Maniac selbst steht unter der Apache License 2.0 (siehe `LICENSE`). Der Installer und das ZIP enthalten
zusätzlich folgende Komponenten anderer Projekte:

## Java-Laufzeit: Eclipse Temurin (OpenJDK)

- Herkunft: Eclipse Adoptium, https://adoptium.net/
- Lizenz: GNU General Public License, Version 2, mit Classpath Exception
- Lizenztexte: im Programmordner unter `lib/runtime/legal/` (je Java-Modul)
- Genaue Version: `lib/runtime/release` (`JAVA_VERSION`)
- Quellcode: https://github.com/adoptium/jdk25u – Tag `jdk-<JAVA_VERSION>+<Build>`, z. B. `jdk-25.0.3+9`;
  Releases mit Quellarchiven unter https://github.com/adoptium/temurin25-binaries/releases

Die Laufzeit ist mit `jlink` auf die benötigten Module verkleinert, aber sonst unverändert. Die Classpath Exception
erlaubt, sie zusammen mit Programmen unter anderer Lizenz weiterzugeben.

## Bibliotheken (Apache License 2.0)

- Kotlin-Standardbibliothek, kotlinx.coroutines, kotlinx.serialization – JetBrains, https://kotlinlang.org/
- Compose Multiplatform und Skiko (inkl. Skia) – JetBrains / Google, https://www.jetbrains.com/compose-multiplatform/
- Objenesis, ReflectASM, MinLog (Abhängigkeiten von Kryo)

## Bibliotheken (andere Lizenzen)

- Kryo (Spielstände) – BSD 3-Clause License, https://github.com/EsotericSoftware/kryo
- Skia – BSD 3-Clause License, https://skia.org/

## Schriften (SIL Open Font License 1.1)

Bricolage Grotesque, Inter, JetBrains Mono – Lizenztexte in `licenses/fonts/`.

## Kartendaten

© OpenStreetMap-Mitwirkende, Open Database License (ODbL) 1.0 – siehe `cities/<stadt>/LICENSE-ODbL.txt` und
https://www.openstreetmap.org/copyright
