package xjcl.mundraub.activities

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import com.akexorcist.googledirection.constant.TransportMode
import xjcl.mundraub.BuildConfig
import xjcl.mundraub.R
import xjcl.mundraub.data.mapTypeChanged
import xjcl.mundraub.databinding.ActivityAppSettingsBinding

class AppSettings : AppCompatActivity() {
    private lateinit var binding: ActivityAppSettingsBinding

    private val mapType_resToInt = mapOf(R.id.mapType_radio_1 to 1, R.id.mapType_radio_2 to 2, R.id.mapType_radio_3 to 3, R.id.mapType_radio_4 to 4)
    private val mapType_intToRes = mapType_resToInt.entries.associate { it.value to it.key }

    private val dirType_resToStr = mapOf(R.id.dirType_radio_1 to TransportMode.BICYCLING, R.id.dirType_radio_2 to TransportMode.WALKING, R.id.dirType_radio_3 to TransportMode.DRIVING)
    private val dirType_strToRes = dirType_resToStr.entries.associate { it.value to it.key }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_activity_app_settings)

        binding.versionTv.text = "Mundraub Navigator v${BuildConfig.VERSION_NAME}"

        val sharedPref = this.getSharedPreferences("global", Context.MODE_PRIVATE)
        binding.mapTypeRadioGroup.check(mapType_intToRes[sharedPref.getInt("mapType", 1)] ?: R.id.mapType_radio_1)
        binding.dirTypeRadioGroup.check(dirType_strToRes[sharedPref.getString("dirType", TransportMode.BICYCLING)] ?: R.id.dirType_radio_1)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    fun onSaveClick(view : View) {
        val sharedPref = this.getSharedPreferences("global", Context.MODE_PRIVATE)

        with (sharedPref.edit()) {
            putInt("mapType", mapType_resToInt[binding.mapTypeRadioGroup.checkedRadioButtonId] ?: 1)
            putString("dirType", dirType_resToStr[binding.dirTypeRadioGroup.checkedRadioButtonId] ?: TransportMode.BICYCLING)
            apply()
        }

        mapTypeChanged = true
        finish()
    }
}
