#!/usr/bin/env bash
# Lädt den OSM-Extrakt für den Regierungsbezirk Karlsruhe (falls nötig) und erzeugt die Stadtdatei für Pforzheim.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RAW="$ROOT/data/raw/karlsruhe-regbez-latest.osm.pbf"
OUT="$ROOT/data/cities/pforzheim.city"
URL="https://download.geofabrik.de/europe/germany/baden-wuerttemberg/karlsruhe-regbez-latest.osm.pbf"

if [[ ! -f "$RAW" || "${1:-}" == "--refresh" ]]; then
    mkdir -p "$(dirname "$RAW")"
    echo "Lade $URL …"
    curl -fL --progress-bar -o "$RAW" "$URL"
fi

"$ROOT/gradlew" -p "$ROOT" -q :osm-import:run --args="$RAW $OUT --name Pforzheim"
