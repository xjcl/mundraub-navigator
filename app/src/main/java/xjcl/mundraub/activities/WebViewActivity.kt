package xjcl.mundraub.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_webview.*
import xjcl.mundraub.R
import xjcl.mundraub.data.treeIdToProfileUrl


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
