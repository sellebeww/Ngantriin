#!/usr/bin/env bash
# Rebuilds the two bundled Bricolage Grotesque instances from the upstream
# variable font. Needs Python 3 with fonttools (dev-only; never shipped):
#   python3 -m venv .venv && .venv/bin/pip install fonttools
#   PYTHON=.venv/bin/python scripts/assets/build-fonts.sh
set -euo pipefail

PY="${PYTHON:-python3}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="$ROOT/assets-source/fonts"
OUT="$ROOT/app/src/main/res/font"
VF="$SRC/BricolageGrotesque[opsz,wdth,wght].ttf"

mkdir -p "$SRC"
if [[ ! -f "$VF" ]]; then
  curl -sSLo "$VF" "https://github.com/google/fonts/raw/main/ofl/bricolagegrotesque/BricolageGrotesque%5Bopsz,wdth,wght%5D.ttf"
  curl -sSLo "$SRC/OFL.txt" "https://github.com/google/fonts/raw/main/ofl/bricolagegrotesque/OFL.txt"
fi

# Latin + Latin-1 + the punctuation the copy actually uses (– — ‘ ’ “ ” • … −).
UNICODES="U+0020-007E,U+00A0-00FF,U+2013-2014,U+2018-201D,U+2022,U+2026,U+2212"

instance() { # name opsz wght wdth
  local tmp
  tmp="$(mktemp -t bricolage).ttf"
  "$PY" -m fontTools.varLib.instancer "$VF" opsz="$2" wght="$3" wdth="$4" -o "$tmp" -q
  "$PY" -m fontTools.subset "$tmp" --unicodes="$UNICODES" \
    --layout-features='kern,liga,tnum,lnum,case' --no-hinting --desubroutinize \
    --name-IDs='*' --output-file="$OUT/$1.ttf"
  rm -f "$tmp"
}

# Ticket numbers and the biggest display moments: condensed, heaviest.
instance bricolage_ticket 96 800 75
# Headlines and titles: full width, bold, mid optical size.
instance bricolage_display 36 700 100

ls -l "$OUT"/bricolage_*.ttf
