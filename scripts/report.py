
import json
import collections
import fruit_calendar

d = json.load(open('all_markers_europe_2020-09-01.json'))
d = json.load(open('all_markers_europe_2020-07-09.json'))

tids = [p['properties']['tid'] for p in d["features"]]
c = collections.Counter(tids)

[print(line) for line in c.most_common()]

print(fruit_calendar.fruitname)

for kv in c.most_common():
    print(str(kv[1]).ljust(4), fruit_calendar.fruitname[kv[0]])



