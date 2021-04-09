package xjcl.mundraub.activities

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import kotlinx.android.synthetic.main.activity_app_settings.*
import xjcl.mundraub.BuildConfig
import xjcl.mundraub.R
import xjcl.mundraub.data.mapTypeChanged

class AppSettings : AppCompatActivity() {
    val resToInt = mapOf(R.id.mapType_radio_1 to 1, R.id.mapType_radio_2 to 2, R.id.mapType_radio_3 to 3, R.id.mapType_radio_4 to 4)
    val intToRes = resToInt.entries.map { it.value to it.key }.toMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_settings)

        this.version_tv.text = "Mundraub Navigator v${BuildConfig.VERSION_NAME}"

        val sharedPref = this.getSharedPreferences("global", Context.MODE_PRIVATE)
        this.mapType_radio_group.check(intToRes[sharedPref.getInt("mapType", 1)] ?: R.id.mapType_radio_1)
    }

    fun onSaveClick(view : View) {
        val sharedPref = this.getSharedPreferences("global", Context.MODE_PRIVATE)
        val checked = this.mapType_radio_group.checkedRadioButtonId
        sharedPref.edit().putInt("mapType", resToInt[checked] ?: 1).apply()
        Log.e("AppSet", resToInt[checked].toString())
        mapTypeChanged = true
        finish()
    }
}
