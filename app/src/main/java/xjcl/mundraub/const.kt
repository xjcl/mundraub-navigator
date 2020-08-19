package xjcl.mundraub

// Key: treeId (type of tree/fruit),  Value: Pair<Int, Int> with first and last month of season
// *** The following code represents January-start as 1, mid-January as 1.5, February-start as 2, and so on
val treeIdToSeason = hashMapOf(
    // https://www.hagebau.de/beratung-obst-ernten/
    // https://www.regional-saisonal.de/saisonkalender-obst
    // https://mundraub.org/sites/default/files/inline-files/Mundraub_Erntekalender.pdf  <-- main source
     4 to ( 8.0 to 11.0),
     5 to ( 8.0 to 11.5),
     6 to ( 6.0 to  9.5),
     7 to ( 7.0 to  9.5),
     8 to ( 7.0 to 10.5),
     9 to ( 8.0 to 11.5),
    10 to ( 7.0 to  9.0),
    11 to ( 6.5 to  8.5),
    12 to ( 0.0 to  0.0),

    14 to ( 8.5 to 11.5),
    15 to ( 9.0 to 11.5),
    16 to ( 9.5 to 11.0),
    17 to ( 0.0 to  0.0),

    18 to ( 8.0 to 11.0),
    19 to ( 6.0 to 10.0), // wild strawberry
    20 to ( 6.5 to 10.0),
    21 to ( 5.5 to 10.0), // note that berries and blossoms are both edible but have different seasons (5.5-10 vs 9-11)
    22 to ( 6.5 to  9.0),
    23 to ( 6.0 to  9.0),
    24 to ( 8.5 to 10.5),
    25 to ( 7.0 to  8.0), // https://www.plantura.garden/gartentipps/zierpflanzen/felsenbirne-pflanzen-und-pflegen
    26 to ( 8.0 to 11.0),
    27 to ( 9.0 to 12.0),
    28 to ( 9.0 to 13.0),
    29 to ( 9.0 to 11.0), // https://www.kneipp.com/de_de/kneipp-magazin/sebastian-kneipp/lexikon-pflanzen-inhaltsstoffe/pflanzenlexikon/weissdorn/
    30 to ( 0.0 to  0.0),

    // https://www.miss.at/pflanzkalender-2018-wann-man-welches-gemuese-pflanzen-kann/?cn-reloaded=1
    31 to ( 3.5 to  6.0),
    32 to ( 8.0 to 11.0), // https://www.pflanzen-vielfalt.net/b%C3%A4ume-str%C3%A4ucher-a-z/wacholder-gemeiner/
    33 to ( 7.0 to 10.0), // https://praxistipps.focus.de/minze-ernten-der-beste-zeitpunkt_107154
    34 to ( 5.0 to 11.0),
    35 to ( 3.0 to  7.0),
    36 to ( 6.0 to  9.0),
    37 to ( 0.0 to  0.0)
)

val treeIdToMarkerIcon = hashMapOf(
    4 to R.drawable.apple,
    5 to R.drawable.pear,
    6 to R.drawable.cherry,
    7 to R.drawable.mirabelle,
    8 to R.drawable.plum,
    9 to R.drawable.quince,
    10 to R.drawable.apricot,
    11 to R.drawable.mulberry,
    12 to R.drawable.otherfruit,

    14 to R.drawable.hazelnut,
    15 to R.drawable.walnut,
    16 to R.drawable.chestnut,
    17 to R.drawable.othernut,

    18 to R.drawable.blackberry,
    19 to R.drawable.wildstrawberry,
    20 to R.drawable.blueberry,
    21 to R.drawable.elderberry,
    22 to R.drawable.raspberry,
    23 to R.drawable.currant,
    24 to R.drawable.cornelcherry,
    25 to R.drawable.shadbush,
    26 to R.drawable.seabuckthorn,
    27 to R.drawable.rosehip,
    28 to R.drawable.blackthorn,
    29 to R.drawable.hawthorn,
    30 to R.drawable.otherfruitshrub,

    31 to R.drawable.ramson,
    32 to R.drawable.juniper,
    33 to R.drawable.mint,
    34 to R.drawable.rosemary,
    35 to R.drawable.woodruff,
    36 to R.drawable.thyme,
    37 to R.drawable.otherherb
)
val treeIdToMarkerIconSorted = treeIdToMarkerIcon.toSortedMap()
