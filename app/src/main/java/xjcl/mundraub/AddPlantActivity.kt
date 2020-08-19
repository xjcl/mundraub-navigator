package xjcl.mundraub

import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.core.view.setPadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputLayout
import khttp.async.Companion.get
import khttp.async.Companion.post
import kotlinx.android.synthetic.main.text_input_autocomplete.view.*
import java.util.*


class AddPlantActivity : AppCompatActivity() {

    // TODO: MapFragment with preview of window :D
    //      this will let users know not to write too much text
    //      MF should NOT respond to touches
    // TODO: use filter bar for species selection?
    // TODO: handle errors
    // TODO: handle status codes

    // TODO: what if someone puts an emoji into Fruchtfund (Gboard keeps suggesting them)
    // TODO: dynamic icon preview on Fruchtfund using marker

    val loginData = mutableMapOf(
        "form_id" to "user_login_form",
        "op" to "Anmelden"
    )

    val plantData = mutableMapOf(
        "changed" to "0",
        "form_build_id" to "",
        "form_id" to "node_plant_form",
        "body[0][format]" to "simple_text",
        "field_plant_image[0][_weight]" to "0",
        "field_plant_image[0][fids]" to "",
        "field_plant_image[0][display]" to "1",
        "address-search" to "",
        "accept_rules" to "1",  // (USER)
        "advanced__active_tab" to "",
        "op" to "Speichern"
    )

    fun locationToStr(location: Location) : String {
        val gcd = Geocoder(this, Locale.getDefault())
        val addresses: List<Address> = gcd.getFromLocation(location.latitude, location.longitude, 1)
        return (0..addresses[0].maxAddressLineIndex).map { addresses[0].getAddressLine(it) }.joinToString("\n")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val lin = LinearLayout(this)
        lin.orientation = LinearLayout.VERTICAL
        lin.setPadding(24)

        // TODO save user and password
        TextInputLayout.inflate(this, R.layout.text_input_layout, lin)
        val userTIL = lin.children.last() as TextInputLayout
        userTIL.hint = "Benutzername"

        // TODO hide
        TextInputLayout.inflate(this, R.layout.text_input_layout, lin)
        val passTIL = lin.children.last() as TextInputLayout
        passTIL.hint = "Passwort"

        TextInputLayout.inflate(this, R.layout.text_input_autocomplete, lin)
        val typeTIL = lin.children.last() as TextInputLayout
        typeTIL.hint = "Fruchtfund"
        val keys = treeIdToMarkerIcon.keys.map { it.toString() }
        val values = keys.map { key -> getString(resources.getIdentifier("tid${key}", "string", "xjcl.mundraub")) }
        val adapter = ArrayAdapter(this, R.layout.list_item, values)
        typeTIL.auto_text.setAdapter(adapter)

        LayoutInflater.from(this).inflate(R.layout.chip_group_number, lin)
        val chipGroup = (lin.children.last() as LinearLayout).children.last() as ChipGroup

        TextInputLayout.inflate(this, R.layout.text_input_layout, lin)
        val descriptionTIL = lin.children.last() as TextInputLayout
        descriptionTIL.hint = "Beschreibung"
        val edit = descriptionTIL.editText!!
        edit.inputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_CLASS_TEXT
        edit.minLines = 3
        edit.maxLines = 3
        lin.children.forEach { Log.e("ch", it.toString()) }

        LayoutInflater.from(this).inflate(R.layout.location_preview, lin)
        val locationPicker = lin.children.last() as MaterialButton

        var location : Location? = null
        fusedLocationClient.lastLocation.addOnSuccessListener(this) { location_ ->
            if (location_ == null) return@addOnSuccessListener
            location = location_
            locationPicker.text = locationToStr(location!!)
        }

        locationPicker.setOnClickListener {  }

        val btn = Button(this)
        btn.text = "Hochladen!"
        btn.setOnClickListener {

            Log.e("pos", "2")
//            fun String.utf8(): String = java.net.URLEncoder.encode(this, "UTF-8")
//            parameters.map {(k, v) -> "${k.utf8()}=${v.utf8()}"}.joinToString("&")
            loginData["name"] = userTIL.editText!!.text.toString()
            loginData["pass"] = passTIL.editText!!.text.toString()

            post("https://mundraub.org/user/login", data=loginData, allowRedirects=false) {
                val r0 = this
                Log.e("ResponseLogin0", r0.text)

                get("https://mundraub.org/node/add/plant", cookies=r0.cookies) {
                    plantData["form_token"] = this.text.substringAfter("""form_token" value="""", "(missing)").substringBefore("\"")
                    plantData["body[0][value]"] = descriptionTIL.editText!!.text.toString()
                    val ind = values.indexOf( typeTIL.editText!!.text.toString() )
                    plantData["field_plant_category"] = if (ind != -1) keys[ind] else "12"  // other fruit
                    plantData["field_plant_count_trees"] = mapOf(
                        R.id.chip_0 to "0",
                        R.id.chip_1 to "1",
                        R.id.chip_2 to "2",
                        R.id.chip_3 to "3"
                    )[chipGroup.checkedChipId] ?: "0"
                    if (location != null) {
                        plantData["field_position[0][value]"] = "POINT(${location!!.longitude} ${location!!.latitude})"
                        plantData["field_plant_address[0][value]"] = locationToStr(location!!)

                        Log.e("addr", plantData["field_plant_address[0][value]"]!!)
                    }

                    Log.e("plantData", plantData.toString())

                    post("https://mundraub.org/node/add/plant", data=plantData, cookies=r0.cookies, allowRedirects=false) {
                        //Log.e("ResponseLogin2", this.text)
                    }
                }
            }
        }
        lin.addView(btn)

        val scroll = ScrollView(this)
        scroll.addView(lin)

        setContentView(scroll)
    }
}