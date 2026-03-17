package xjcl.mundraub.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import xjcl.mundraub.R
import xjcl.mundraub.data.treeIdToProfileUrl
import xjcl.mundraub.databinding.ActivityWebviewBinding


class PlantProfile : AppCompatActivity() {
    private lateinit var binding: ActivityWebviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val url = treeIdToProfileUrl[intent.extras?.getInt("tid")] ?: "https://mundraub.org/404"
        binding.webView.loadUrl(url)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}


class WebViewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWebviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val url = intent.extras?.getString("url") ?: "https://mundraub.org/404"
        binding.webView.loadUrl(url)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
