#!/usr/bin/env python3
"""يطبع «المعرّف<TAB>الرابط» لكل مؤذن له مصدر في Data.kt (يستخدمه البناء لدمج الأصوات)."""
import re
import sys
from urllib.parse import quote

BASES = {
    "athan": "https://raw.githubusercontent.com/abodehq/Athan-MP3/cf1a9dd4f1e5e10009439099c5af74479ad332c0/Sounds/",
    "kiwi": "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/b9180a2bb769f74cff8e378ef2cc7aaf8db5cd5b/",
}
src = open(sys.argv[1], encoding="utf-8").read()
pattern = re.compile(r'Muezzin\("([a-z0-9_]+)".*?\b(athan|kiwi)\("([^"]+)"\)\)')
count = 0
for mid, helper, name in pattern.findall(src):
    print(f"{mid}\t{BASES[helper]}{quote(name, safe='')}")
    count += 1
if count == 0:
    sys.exit("no voices found")
