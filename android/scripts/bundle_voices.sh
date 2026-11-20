#!/usr/bin/env bash
# يحمّل أصوات المؤذنين ويضعها في res/raw قبل البناء
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p app/src/main/res/raw
python3 scripts/voice_urls.py app/src/main/java/app/prayertimes/azan/Data.kt > build-voices.tsv
while IFS=$'\t' read -r id url; do
  curl -fsSL --retry 3 -o "app/src/main/res/raw/$id.mp3" "$url" || { echo "BROKEN: $id $url"; exit 1; }
  echo "OK $id ($(stat -c %s "app/src/main/res/raw/$id.mp3") bytes)"
done < build-voices.tsv
count=$(ls app/src/main/res/raw/*.mp3 | wc -l)
echo "Bundled $count voices"
test "$count" -eq "$(wc -l < build-voices.tsv)"
