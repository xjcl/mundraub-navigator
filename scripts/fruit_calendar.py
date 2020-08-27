
season = dict()
fruitname = dict()

for line in open('app/src/main/java/xjcl/mundraub/MapsActivity.kt'):
    line = line.strip()
    if 'to (' in line:
        key = line[:line.index(' ')]
        season[key] = line[line.index('('):line.index(')')+1]

for line in open('app/src/main/res/values-de/strings.xml'):
    if 'tid' in line:
        line = line[line.index('tid')+3:]
        key = line[:line.index('"')]
        fruitname[key] = line[line.index('>')+1:line.index('<')]

for fruit in sorted(season.keys(), key=int):
    print(fruit.rjust(2), '--', season[fruit], '--', fruitname[fruit])
