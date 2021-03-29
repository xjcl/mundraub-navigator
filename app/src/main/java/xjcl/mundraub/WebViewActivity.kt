package xjcl.mundraub

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_webview.*


val treeIdToProfileUrl = hashMapOf(
    4 to "https://mundraub.org/apfel-steckbrief",
    5 to "https://mundraub.org/birne-steckbrief",
    6 to "https://mundraub.org/kirsche-steckbrief",
    7 to "https://mundraub.org/mirabelle-steckbrief",
    8 to "https://mundraub.org/pflaume-steckbrief",
    9 to "https://mundraub.org/quitte-steckbrief",
    10 to "https://mundraub.org/aprikose-marille-steckbrief",
    11 to "https://mundraub.org/maulbeere-steckbrief",
    12 to null,

    14 to "https://mundraub.org/haselnuss-baumhasel-steckbrief",
    15 to "https://mundraub.org/walnuss-steckbrief",
    16 to "https://mundraub.org/esskastanie-marone-steckbrief",
    17 to null,

    18 to "https://mundraub.org/brombeere-steckbrief",
    19 to "https://mundraub.org/walderdbeere-steckbrief",
    20 to "https://mundraub.org/heidelbeere-steckbrief",
    21 to "https://mundraub.org/holunder-steckbrief",
    22 to "https://mundraub.org/himbeere-steckbrief",
    23 to "https://mundraub.org/johannisbeere-steckbrief",
    24 to "https://mundraub.org/kornelkirsche-steckbrief",
    25 to "https://mundraub.org/felsenbirne-steckbrief",
    26 to "https://mundraub.org/sanddorn-steckbrief",
    27 to "https://mundraub.org/hagebutte-steckbrief",
    28 to "https://mundraub.org/schlehe-steckbrief",
    29 to "https://mundraub.org/weißdorn-steckbrief",
    30 to null,

    31 to "https://mundraub.org/bärlauch-wunderlauch-steckbrief",
    32 to "https://mundraub.org/wacholder-steckbrief",
    33 to "https://mundraub.org/minze-steckbrief",
    34 to "https://mundraub.org/rosmarin-steckbrief",
    35 to "https://mundraub.org/waldmeister-steckbrief",
    36 to "https://mundraub.org/thymian-steckbrief",
    37 to null,
)


class PlantProfile : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        val url = treeIdToProfileUrl[intent.extras?.getInt("tid")] ?: "https://mundraub.org/404"
        webView.loadUrl(url)
    }
}


class WebViewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        val url = intent.extras?.getString("url") ?: "https://mundraub.org/404"
        webView.loadUrl(url)
    }
}
