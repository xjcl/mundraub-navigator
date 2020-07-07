
<table>
<tr>
<td width="35%">
<img src="demo_2020-07-07.jpg" alt="Demo of the app">
</td>
<td rowspan="2"  valign="top">

# <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="Logo of the app" width="55px"> Mundraub Navigator

Mundraub Navigator is a Kotlin Android app for using https://mundraub.org, a map of public fruit trees.

Go foraging, find fresh fruit, discover your local neighborhood, reduce food waste, and gain a deeper appreciation for nature and her seasons!

Features:
- Find local fruit trees and shrubs in DACH countries
    - includes mini-calendar with seasonality information
- Google Maps integration for directions to markers
- Stays usable even when losing internet connection
- Languages: 🇺🇸/🇩🇪
- Not yet supported: User accounts (adding/editing markers)

<a href='https://play.google.com/store/apps/details?id=xjcl.mundraub&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' width="200px"/></a>

</td>
</tr>
</table>

## I went to a marker but couldn't find anything

Mundraub has a visual bug where marker images are anchored at the center and not the tip/bottom. This might lead to users placing markers north of where they wanted to place them, so try checking the area directly south of the marker.

## Is this app official?

I've been in contact with Kai from Mundraub, and he approves of the app (and the name and asset use). But I did all the development and am in charge of all development decisions.

## How to build

Add your `google_maps_key` in a file called `app/src/debug/res/values/google_maps_api.xml` (and, for me, `app/src/release/res/values/google_maps_api.xml`)

    <resources>
        <string name="google_maps_key" templateMergeStrategy="preserve" translatable="false">yourKeyHere</string>
    </resources>

Then just build it in Android Studio.

[Discord server](https://discord.gg/M9Scjre)

[Dieses README gibt es auch auf Deutsch](README-de.md)
