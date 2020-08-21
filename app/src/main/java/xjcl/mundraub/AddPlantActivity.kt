package xjcl.mundraub

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.setPadding
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputLayout
import khttp.async.Companion.get
import khttp.async.Companion.post
import kotlinx.android.synthetic.main.text_input_autocomplete.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import java.util.*
import kotlin.concurrent.thread


class AddPlantActivity : AppCompatActivity() {

    // TODO: MapFragment with preview of window :D
    //      this will let users know not to write too much text
    //      MF should NOT respond to touches
    // TODO: use filter bar for species selection?
    // TODO: handle GET error

    // TODO: what if someone puts an emoji into Fruchtfund (Gboard keeps suggesting them)
    // (TODO: dynamic icon preview on Fruchtfund using marker)
    // TODO: image upload

    // TODO: edit a node
    // TODO: delete a node
    // TODO: create an account

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

    fun LatLng(location : Location): LatLng = LatLng(location.latitude, location.longitude)

    var location : LatLng = LatLng(0.0, 0.0)
    lateinit var locationPicker : MaterialButton

    fun updateLocationPicker(latLng: LatLng) {
        if (!::locationPicker.isInitialized) return
        locationPicker.text = "(loading...)"
        thread {
            location = latLng
            val gcd = Geocoder(this@AddPlantActivity, Locale.getDefault())
            val addresses: List<Address> = gcd.getFromLocation(latLng.latitude, latLng.longitude, 1)
            runOnUiThread { locationPicker.text = if (addresses.isEmpty()) "???" else
                (0..addresses[0].maxAddressLineIndex).map { addresses[0].getAddressLine(it) }.joinToString("\n") }
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42 && resultCode == Activity.RESULT_OK && data != null) {
            val lat = data.getDoubleExtra("lat", 0.0)
            val lng = data.getDoubleExtra("lng", 0.0)
            updateLocationPicker(LatLng(lat, lng))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar!!.title = getString(R.string.addNode)

        val sharedPref = this.getPreferences(Context.MODE_PRIVATE)

        val lin = LinearLayout(this)
        lin.orientation = LinearLayout.VERTICAL
        lin.setPadding(24)

        // TODO save user and password
        TextInputLayout.inflate(this, R.layout.text_input_layout, lin)
        val userTIL = lin.children.last() as TextInputLayout
        userTIL.hint = getString(R.string.user)
        userTIL.editText!!.setText( sharedPref.getString("name", "") )

        // TODO hide
        TextInputLayout.inflate(this, R.layout.text_input_layout, lin)
        val passTIL = lin.children.last() as TextInputLayout
        passTIL.hint = getString(R.string.pass)
        passTIL.editText!!.setText( sharedPref.getString("pass", "") )

        TextInputLayout.inflate(this, R.layout.text_input_autocomplete, lin)
        val typeTIL = lin.children.last() as TextInputLayout
        typeTIL.hint = getString(R.string.type)
        val keys = treeIdToMarkerIcon.keys.map { it.toString() }
        val values = keys.map { key -> getString(resources.getIdentifier("tid${key}", "string", "xjcl.mundraub")) }
        val adapter = ArrayAdapter(this, R.layout.list_item, values)
        typeTIL.auto_text.setAdapter(adapter)
        fun updateType() : Unit {
            val typeIndex = values.indexOf( typeTIL.editText!!.text.toString() )
            if (typeIndex == -1) return
            val img: Drawable = ContextCompat.getDrawable(this, treeIdToMarkerIcon[keys[typeIndex].toInt()] ?: R.drawable.otherfruit)!!
            locationPicker.setCompoundDrawablesWithIntrinsicBounds(img, null, null, null)
        }
        typeTIL.auto_text.setOnFocusChangeListener { _, _ -> updateType() }
        typeTIL.auto_text.setOnItemClickListener { _, _, _, _ -> updateType() }

        LayoutInflater.from(this).inflate(R.layout.chip_group_number, lin)
        val chipGroup = (lin.children.last() as LinearLayout).children.last() as ChipGroup

        TextInputLayout.inflate(this, R.layout.text_input_layout, lin)
        val descriptionTIL = lin.children.last() as TextInputLayout
        descriptionTIL.hint = getString(R.string.desc)
        val edit = descriptionTIL.editText!!
        edit.inputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_CLASS_TEXT
        edit.minLines = 3
        edit.maxLines = 3
        lin.children.forEach { Log.e("ch", it.toString()) }

        LayoutInflater.from(this).inflate(R.layout.location_preview, lin)
        locationPicker = lin.children.last() as MaterialButton
        locationPicker.text = "???"

        fusedLocationClient.lastLocation.addOnSuccessListener(this) { location_ ->
            if (location_ == null) return@addOnSuccessListener
            updateLocationPicker(LatLng(location_))
        }

        locationPicker.setOnClickListener {
            val typeIndex = values.indexOf( typeTIL.editText!!.text.toString() )
            val intent = Intent(this, LocationPicker::class.java)
            intent.putExtra("tid", if (typeIndex == -1) 12 else keys[typeIndex].toInt() )
            intent.putExtra("lat", location.latitude)
            intent.putExtra("lng", location.longitude)
            startActivityForResult(intent, 42)
        }

        val btn = Button(this)
        btn.text = getString(R.string.upld)
        btn.setOnClickListener {

            if (userTIL.editText!!.text.toString().isBlank() || passTIL.editText!!.text.toString().isBlank()) {
                runOnUiThread { Toast.makeText(this@AddPlantActivity, getString(R.string.errMsgLoginInfos), Toast.LENGTH_LONG).show() }
                return@setOnClickListener
            }

            loginData["name"] = userTIL.editText!!.text.toString()
            loginData["pass"] = passTIL.editText!!.text.toString()

            with (sharedPref.edit()) {
                putString("name", loginData["name"])
                putString("pass", loginData["pass"])
                apply()
            }

            post("https://mundraub.org/user/login", data=loginData, allowRedirects=false) {
                if (this.statusCode != 303) {
                    runOnUiThread { Toast.makeText(this@AddPlantActivity, getString(R.string.errMsgLogin), Toast.LENGTH_LONG).show() }
                    return@post
                }

                val r0 = this
                Log.e("ResponseLogin0", r0.text)

                get("https://mundraub.org/node/add/plant", cookies=r0.cookies) {
                    val typeIndex = values.indexOf( typeTIL.editText!!.text.toString() )

                    val errors = listOf(
                        getString(R.string.errMsgDesc) to descriptionTIL.editText!!.text.toString().isBlank(),
                        getString(R.string.errMsgType) to (typeIndex == -1),
                        getString(R.string.errMsgLoc) to ((location.latitude == 0.0 && location.longitude == 0.0)
                                || locationPicker.text.toString() == "???")
                    )
                    errors.forEach { if (it.second) {
                        runOnUiThread { Toast.makeText(this@AddPlantActivity, it.first, Toast.LENGTH_LONG).show() }
                        return@get
                    } }

                    plantData["form_token"] = this.text.substringAfter("""form_token" value="""", "(missing)").substringBefore("\"")
                    plantData["body[0][value]"] = descriptionTIL.editText!!.text.toString()
                    plantData["field_plant_category"] = keys[typeIndex]
                    plantData["field_plant_count_trees"] = mapOf(
                        R.id.chip_0 to "0",
                        R.id.chip_1 to "1",
                        R.id.chip_2 to "2",
                        R.id.chip_3 to "3"
                    )[chipGroup.checkedChipId] ?: "0"
                    plantData["field_position[0][value]"] = "POINT(${location.longitude} ${location.latitude})"
                    plantData["field_plant_address[0][value]"] = locationPicker.text.toString()

                    Log.e("plantData", plantData.toString())

                    post("https://mundraub.org/node/add/plant", data=plantData, cookies=r0.cookies, allowRedirects=false) post2@ {
                        if (this.statusCode != 303) {
                            runOnUiThread { Toast.makeText(this@AddPlantActivity, getString(R.string.errMsgAdd), Toast.LENGTH_LONG).show() }
                            return@post2
                        }

                        Log.e("ResponseLogin2", this.text)
                        runOnUiThread { Toast.makeText(this@AddPlantActivity, getString(R.string.errMsgSuccess), Toast.LENGTH_LONG).show() }
                        finish()
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