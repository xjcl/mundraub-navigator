
import json
import glob
import collections
import fruit_calendar

latest_file = glob.glob("all_markers_europe_*.json")[-1]
d = json.load(open(latest_file))

tids = [p['properties']['tid'] for p in d["features"]]
c = collections.Counter(tids)

[print(line) for line in c.most_common()]

print(fruit_calendar.fruitname)

for kv in c.most_common():
    print(str(kv[1]).ljust(4), fruit_calendar.fruitname[kv[0]])



