#!/usr/bin/env bash
# Erzeugt die Karte eines Stadtpakets: lädt den OSM-Auszug aus cities/<id>/city.json (falls nötig) und schreibt
# cities/<id>/map.city. Aufruf: scripts/import-city.sh <id> [--refresh]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ID="${1:?Aufruf: scripts/import-city.sh <id> [--refresh]}"
PACK="$ROOT/cities/$ID"
INFO="$PACK/city.json"
[[ -f "$INFO" ]] || { echo "$INFO fehlt"; exit 1; }

field() { sed -n "s/.*\"$1\": *\"\([^\"]*\)\".*/\1/p" "$INFO" | head -1; }
URL="$(field extract)"
BBOX="$(field bbox)"
NAME="$(field name)"
[[ -n "$URL" && -n "$BBOX" ]] || { echo "import.extract und import.bbox in $INFO angeben"; exit 1; }

RAW="$ROOT/data/raw/$(basename "$URL")"
if [[ ! -f "$RAW" || "${2:-}" == "--refresh" ]]; then
    mkdir -p "$(dirname "$RAW")"
    echo "Lade $URL …"
    curl -fL --progress-bar -o "$RAW" "$URL"
fi

"$ROOT/gradlew" -p "$ROOT" -q :osm-import:run --args="$RAW $PACK/map.city --name $NAME --bbox $BBOX"
echo "Fertig: $PACK/map.city"
