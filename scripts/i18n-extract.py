#!/usr/bin/env python3
"""Sammelt alle übersetzbaren Texte (tr("…") und translatable("…")) aus dem Code und gleicht lang/<code>.json ab.

Aufruf: scripts/i18n-extract.py [en]
- fehlende Texte kommen mit leerer Übersetzung ans Ende der Datei (dort übersetzen),
- Texte, die es im Code nicht mehr gibt, werden gemeldet und entfernt.
Leere Übersetzungen zeigt das Spiel auf Deutsch an.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCES = ["i18n", "city", "sim-engine", "game", "ui", "desktop-app"]
STRING = r'"(?:[^"\\]|\\.)*"'
CALL = re.compile(r"\b(?:tr|translatable)\(\s*(" + STRING + r"(?:\s*\+\s*" + STRING + r")*)")


def unescape(literal: str) -> str:
    body = literal[1:-1]
    out = []
    i = 0
    while i < len(body):
        c = body[i]
        if c == "\\":
            n = body[i + 1]
            if n == "u":
                out.append(chr(int(body[i + 2:i + 6], 16)))
                i += 6
                continue
            out.append({"n": "\n", "t": "\t", '"': '"', "\\": "\\", "$": "$", "'": "'"}.get(n, n))
            i += 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def keys() -> list[str]:
    found = []
    for module in SOURCES:
        src = ROOT / module / "src"
        for path in sorted(list(src.glob("*Main/**/*.kt")) + list(src.glob("main/**/*.kt"))):
            source = path.read_text()
            for match in CALL.finditer(source):
                text = "".join(unescape(lit) for lit in re.findall(STRING, match.group(1)))
                if text not in found:
                    found.append(text)
    return found


def main() -> None:
    code = sys.argv[1] if len(sys.argv) > 1 else "en"
    path = ROOT / "lang" / f"{code}.json"
    existing = json.loads(path.read_text()) if path.exists() else {}
    wanted = keys()
    merged = {k: existing.get(k, "") for k in wanted}
    gone = [k for k in existing if k not in merged]
    missing = [k for k in wanted if not existing.get(k)]
    path.parent.mkdir(exist_ok=True)
    path.write_text(json.dumps(merged, ensure_ascii=False, indent=2) + "\n")
    print(f"{len(wanted)} Texte, davon {len(missing)} ohne Übersetzung; {len(gone)} entfernt")
    for k in gone:
        print("  entfernt:", k[:80])


if __name__ == "__main__":
    main()
