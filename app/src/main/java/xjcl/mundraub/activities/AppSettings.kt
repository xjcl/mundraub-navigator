package xjcl.mundraub.activities

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.akexorcist.googledirection.constant.TransportMode
import kotlinx.android.synthetic.main.activity_app_settings.*
import xjcl.mundraub.BuildConfig
import xjcl.mundraub.R
import xjcl.mundraub.data.mapTypeChanged

class AppSettings : AppCompatActivity() {
    val mapType_resToInt = mapOf(R.id.mapType_radio_1 to 1, R.id.mapType_radio_2 to 2, R.id.mapType_radio_3 to 3, R.id.mapType_radio_4 to 4)
    val mapType_intToRes = mapType_resToInt.entries.map { it.value to it.key }.toMap()

    val dirType_resToStr = mapOf(R.id.dirType_radio_1 to TransportMode.BICYCLING, R.id.dirType_radio_2 to TransportMode.WALKING, R.id.dirType_radio_3 to TransportMode.DRIVING)
    val dirType_strToRes = dirType_resToStr.entries.map { it.value to it.key }.toMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_settings)

        this.version_tv.text = "Mundraub Navigator v${BuildConfig.VERSION_NAME}"

        val sharedPref = this.getSharedPreferences("global", Context.MODE_PRIVATE)
        this.mapType_radio_group.check(mapType_intToRes[sharedPref.getInt("mapType", 1)] ?: R.id.mapType_radio_1)
        this.dirType_radio_group.check(dirType_strToRes[sharedPref.getString("dirType", TransportMode.BICYCLING)] ?: R.id.dirType_radio_1)
    }

    fun onSaveClick(view : View) {
        val sharedPref = this.getSharedPreferences("global", Context.MODE_PRIVATE)

        with (sharedPref.edit()) {
            putInt("mapType", mapType_resToInt[mapType_radio_group.checkedRadioButtonId] ?: 1)
            putString("dirType", dirType_resToStr[dirType_radio_group.checkedRadioButtonId] ?: TransportMode.BICYCLING)
            apply()
        }

        mapTypeChanged = true
        finish()
    }
}
