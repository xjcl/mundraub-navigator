
<table>
<tr>
<td>
<img src="demo2.jpg" alt="Demo of the app" width="500px">
</td>
<td rowspan="2"  valign="top">

# Mundraub Mobile

Mundraub Mobile is an unofficial Kotlin Android app for using https://mundraub.org, a map of public fruit trees.

Features:
- Seasonality information for each species
- Keeps location markers when internet lost, even when backgrounded
- Can track location and use Google Maps to navigate to markers
- Supported languages: German and English
- TODO features: Marker description texts, status and images
- Not features: User accounts, adding/editing markers


</td>
</tr>
</table>

## How to build

Add your `google_maps_key` in a file called `app/src/debug/res/values/google_maps_api.xml` (and, for me, `app/src/release/res/values/google_maps_api.xml`)

    <resources>
        <string name="google_maps_key" templateMergeStrategy="preserve" translatable="false">yourKeyHere</string>
    </resources>

Then just build it in Android Studio.
