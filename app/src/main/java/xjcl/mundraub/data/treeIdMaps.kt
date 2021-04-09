package xjcl.mundraub.data

import xjcl.mundraub.R

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
    //12 to ( 0.0 to  0.0),

    14 to ( 8.5 to 11.5),
    15 to ( 9.0 to 11.5),
    16 to ( 9.5 to 11.0),
    //17 to ( 0.0 to  0.0),

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
    //30 to ( 0.0 to  0.0),

    // https://www.miss.at/pflanzkalender-2018-wann-man-welches-gemuese-pflanzen-kann/?cn-reloaded=1
    31 to ( 3.5 to  6.0),
    32 to ( 8.0 to 11.0), // https://www.pflanzen-vielfalt.net/b%C3%A4ume-str%C3%A4ucher-a-z/wacholder-gemeiner/
    33 to ( 7.0 to 10.0), // https://praxistipps.focus.de/minze-ernten-der-beste-zeitpunkt_107154
    34 to ( 5.0 to 11.0),
    35 to ( 3.0 to  7.0),
    36 to ( 6.0 to  9.0)
    //37 to ( 0.0 to  0.0)
)

val treeIdToFrequency = hashMapOf(
    4 to 10098,
    5 to  3783,
    6 to  7340,
    7 to  2587,
    8 to  4392,
    9 to  3783,
    10 to   53,
    11 to  520,
    12 to  859,

    14 to 9120,
    15 to 4945,
    16 to 1199,
    17 to  270,

    18 to 4917,
    19 to  110,
    20 to  282,
    21 to 2872,
    22 to  401,
    23 to  224,
    24 to  813,
    25 to  518,
    26 to  527,
    27 to 1286,
    28 to  991,
    29 to  377,
    30 to  334,

    31 to 1333,
    32 to   37,
    33 to   89,
    34 to   38,
    35 to  186,
    36 to   46,
    37 to 1114,
)

val treeIdToMarkerIcon = hashMapOf(
    4 to R.drawable.icon_apple,
    5 to R.drawable.icon_pear,
    6 to R.drawable.icon_cherry,
    7 to R.drawable.icon_mirabelle,
    8 to R.drawable.icon_plum,
    9 to R.drawable.icon_quince,
    10 to R.drawable.icon_apricot,
    11 to R.drawable.icon_mulberry,
    12 to R.drawable.icon_otherfruit,

    14 to R.drawable.icon_hazelnut,
    15 to R.drawable.icon_walnut,
    16 to R.drawable.icon_chestnut,
    17 to R.drawable.icon_othernut,

    18 to R.drawable.icon_blackberry,
    19 to R.drawable.icon_wildstrawberry,
    20 to R.drawable.icon_blueberry,
    21 to R.drawable.icon_elderberry,
    22 to R.drawable.icon_raspberry,
    23 to R.drawable.icon_currant,
    24 to R.drawable.icon_cornelcherry,
    25 to R.drawable.icon_shadbush,
    26 to R.drawable.icon_seabuckthorn,
    27 to R.drawable.icon_rosehip,
    28 to R.drawable.icon_blackthorn,
    29 to R.drawable.icon_hawthorn,
    30 to R.drawable.icon_otherfruitshrub,

    31 to R.drawable.icon_ramson,
    32 to R.drawable.icon_juniper,
    33 to R.drawable.icon_mint,
    34 to R.drawable.icon_rosemary,
    35 to R.drawable.icon_woodruff,
    36 to R.drawable.icon_thyme,
    37 to R.drawable.icon_otherherb
)

val treeIdToMarkerFrame = hashMapOf(
    4 to R.drawable.frame_apple,
    5 to R.drawable.frame_pear,
    6 to R.drawable.frame_cherry,
    7 to R.drawable.frame_mirabelle,
    8 to R.drawable.frame_plum,
    9 to R.drawable.frame_quince,
    10 to R.drawable.frame_apricot,
    11 to R.drawable.frame_mulberry,
    12 to R.drawable.frame_otherfruit,

    14 to R.drawable.frame_hazelnut,
    15 to R.drawable.frame_walnut,
    16 to R.drawable.frame_chestnut,
    17 to R.drawable.frame_othernut,

    18 to R.drawable.frame_blackberry,
    19 to R.drawable.frame_wildstrawberry,
    20 to R.drawable.frame_blueberry,
    21 to R.drawable.frame_elderberry,
    22 to R.drawable.frame_raspberry,
    23 to R.drawable.frame_currant,
    24 to R.drawable.frame_cornelcherry,
    25 to R.drawable.frame_shadbush,
    26 to R.drawable.frame_seabuckthorn,
    27 to R.drawable.frame_rosehip,
    28 to R.drawable.frame_blackthorn,
    29 to R.drawable.frame_hawthorn,
    30 to R.drawable.frame_otherfruitshrub,

    31 to R.drawable.frame_ramson,
    32 to R.drawable.frame_juniper,
    33 to R.drawable.frame_mint,
    34 to R.drawable.frame_rosemary,
    35 to R.drawable.frame_woodruff,
    36 to R.drawable.frame_thyme,
    37 to R.drawable.frame_otherherb
)
val treeIdToMarkerIconSorted = treeIdToMarkerIcon.toSortedMap()

lateinit var germanStringsToTreeId : Map<String, Int>
