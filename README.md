
<table>
<tr>
<td width="35%">
<img src="demo_2020-06-21.jpg" alt="Demo of the app">
</td>
<td rowspan="2"  valign="top">

# <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Logo of the app" width="55px"> Mundraub Mobile

Mundraub Mobile is an unofficial Kotlin Android app for using https://mundraub.org, a map of public fruit trees.

Go foraging, find fresh fruit, discover your local neighborhood, reduce food waste, and gain a deeper appreciation for nature and her seasons!

Features:
- Find local fruit trees and shrubs in DACH countries
    - includes mini-calendar with seasonality information
- Google Maps integration for directions to markers
- Stays usable even when losing internet connection
- Languages: 🇺🇸/🇩🇪
- Not yet supported: User accounts (adding/editing markers)

</td>
</tr>
</table>

## I went to a marker but couldn't find anything

Mundraub has a visual bug where marker images are anchored at the center and not the tip/bottom. This might lead to users placing markers north of where they wanted to place them, so try checking the area directly south of the marker.

## How to build

Add your `google_maps_key` in a file called `app/src/debug/res/values/google_maps_api.xml` (and, for me, `app/src/release/res/values/google_maps_api.xml`)

    <resources>
        <string name="google_maps_key" templateMergeStrategy="preserve" translatable="false">yourKeyHere</string>
    </resources>

Then just build it in Android Studio.

[Dieses README gibt es auch auf Deutsch](README-de.md)
