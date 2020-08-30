package xjcl.mundraub

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.core.text.HtmlCompat
import androidx.core.view.children
import androidx.core.view.setPadding
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.result.Result
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputLayout
import kotlinx.android.synthetic.main.text_input_autocomplete.view.*
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.concurrent.thread


class PlantForm : AppCompatActivity() {

    val loginData = mutableMapOf(
        "form_id" to "user_login_form",
        "op" to "Anmelden"
    )

    val plantData = mutableMapOf(
        "form_id" to "node_plant_form",
        "changed" to "0",
        "form_build_id" to "",
        "body[0][format]" to "simple_text",
        "field_plant_image[0][_weight]" to "0",
        "field_plant_image[0][fids]" to "",
        "field_plant_image[0][display]" to "1",
        "address-search" to "",
        "accept_rules" to "1",  // (USER)
        "advanced__active_tab" to "",
        "op" to "Speichern"
    )

    val chipMap = mapOf(
        R.id.chip_0 to "0",
        R.id.chip_1 to "1",
        R.id.chip_2 to "2",
        R.id.chip_3 to "3"
    )

    var submitUrl = "https://mundraub.org/node/add/plant"

    fun LatLng(location : Location): LatLng = LatLng(location.latitude, location.longitude)

    var location : LatLng = LatLng(0.0, 0.0)
    lateinit var locationPicker : MaterialButton

    fun updateLocationPicker(latLng: LatLng) {
        if (!::locationPicker.isInitialized) return
        locationPicker.text = getString(R.string.loading)
        location = latLng
        thread {
            val gcd = Geocoder(this@PlantForm, Locale.getDefault())
            val addresses: List<Address> = try { gcd.getFromLocation(latLng.latitude, latLng.longitude, 1) }
                catch (ex : IOException) { listOf() }
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

    private fun exitToMain(nid : String) {
        Log.e("exitToMain", "exit to main at $location with $nid")
        val output = Intent().putExtra("lat", location.latitude).putExtra("lng", location.longitude).putExtra("nid", nid)
        setResult(Activity.RESULT_OK, output)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO add/edit marker
        supportActionBar?.title = getString(R.string.addNode)

        val sharedPref = this.getPreferences(Context.MODE_PRIVATE)

        ensureCookie(this)
        val cook2 = sharedPref.getString("cookie", null)
        if (cook2 == null) { finish(); return }

        val lin = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24) }

        TextInputLayout.inflate(this, R.layout.text_input_layout, lin)
        val userTIL = (lin.children.last() as TextInputLayout).apply {
            hint = getString(R.string.user)
            editText?.setText(sharedPref.getString("name", ""))
        }

        TextInputLayout.inflate(this, R.layout.text_input_layout, lin)
        val passTIL = (lin.children.last() as TextInputLayout).apply {
            hint = getString(R.string.pass)
            editText?.setText(sharedPref.getString("pass", ""))
        }

        TextInputLayout.inflate(this, R.layout.text_input_autocomplete, lin)
        val typeTIL = (lin.children.last() as TextInputLayout).apply { hint = getString(R.string.type) }
        // TODO make this a proper map and call getInverse() on it
        val keys = treeIdToMarkerIcon.keys.map { it.toString() }
        val values = keys.map { key -> getString(resources.getIdentifier("tid${key}", "string", "xjcl.mundraub")) }
        typeTIL.auto_text.setAdapter( ArrayAdapter(this, R.layout.list_item, values) )
        fun updateType() : Unit {
            val typeIndex = values.indexOf( typeTIL.editText?.text.toString() )
            if (typeIndex == -1) return
            val left = ContextCompat.getDrawable(this, treeIdToMarkerIcon[keys[typeIndex].toInt()] ?: R.drawable.otherfruit)
            locationPicker.setCompoundDrawablesWithIntrinsicBounds(left, null, null, null)
        }
        typeTIL.auto_text.setOnFocusChangeListener { _, _ -> updateType() }
        typeTIL.auto_text.setOnItemClickListener { _, _, _, _ -> updateType() }

        LayoutInflater.from(this).inflate(R.layout.chip_group_number, lin)
        val chipGroup = (lin.children.last() as LinearLayout).children.last() as ChipGroup

        TextInputLayout.inflate(this, R.layout.text_input_layout, lin)
        val descriptionTIL = (lin.children.last() as TextInputLayout).apply {
            hint = getString(R.string.desc)
            editText?.apply {
                inputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_CLASS_TEXT
                minLines = 3
                maxLines = 3
            }
        }

        LayoutInflater.from(this).inflate(R.layout.location_preview, lin)
        locationPicker = (lin.children.last() as MaterialButton).apply {
            text = "???"
            setOnClickListener {
                val typeIndex = values.indexOf(typeTIL.editText?.text.toString())
                val intent = Intent(context, LocationPicker::class.java).putExtra("tid", if (typeIndex == -1) 12 else keys[typeIndex].toInt())
                    .putExtra("lat", location.latitude).putExtra("lng", location.longitude)
                startActivityForResult(intent, 42)
            }
        }

        // ----

        fusedLocationClient.lastLocation.addOnSuccessListener(this) { it?.let { location ->
            updateLocationPicker(LatLng(location))
        }}

        fun doSubmit(result : Result<String, FuelError>, cook : String) {
            plantData["changed"] = result.get().substringAfter("name=\"changed\" value=\"").substringBefore("\"")
            //plantData["form_build_id"] = result.get().substringAfter("name=\"form_build_id\" value=\"").substringBefore("\"")
            plantData["form_token"] = result.get().substringAfter("""form_token" value="""", "(missing)").substringBefore("\"")
            plantData["body[0][value]"] = descriptionTIL.editText?.text.toString()
            plantData["field_plant_category"] = keys[values.indexOf( typeTIL.editText?.text.toString() )]
            plantData["field_plant_count_trees"] = chipMap[chipGroup.checkedChipId] ?: "0"
            plantData["field_position[0][value]"] = "POINT(${location.longitude} ${location.latitude})"
            plantData["field_plant_address[0][value]"] = locationPicker.text.toString()

            Log.e("plantData", plantData.toString())

            Fuel.post(submitUrl, plantData.toList()).header(Headers.COOKIE to cook)
                .allowRedirects(false).responseString { request, response, result ->

                    Log.e("nid", result.get())

                    when (response.statusCode) {
                        -1 -> {runOnUiThread { Toast.makeText(this@PlantForm, getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show() }; return@responseString}
                        303 -> {}
                        else -> {runOnUiThread { Toast.makeText(this@PlantForm, getString(R.string.errMsgAdd), Toast.LENGTH_SHORT).show() }; return@responseString}
                    }

                    val nid_ = result.get().substringAfter("?nid=", "").substringBefore("\"")
                    val nid = if (nid_.isNotEmpty()) nid_ else result.get().substringAfter("node/").substringBefore("\"")
                    runOnUiThread { Toast.makeText(this@PlantForm, getString(R.string.errMsgSuccess), Toast.LENGTH_LONG).show() }
                    exitToMain(nid)
                }
        }

        // TODO This will be reworked with a separate login page and cookie handling once necessary changes on the backend are made

        // TODO write down data
        // TODO make upload pass correctly
        // TODO if nid in intent
        // TODO add image round-trip
        val intentNid = intent.getIntExtra("nid", -1)
        if (userTIL.editText?.text.toString().isNotBlank() && passTIL.editText?.text.toString().isNotBlank() && intentNid > -1) {
            submitUrl = "https://mundraub.org/node/${intentNid}/edit"
//            loginData["name"] = userTIL.editText?.text.toString()
//            loginData["pass"] = passTIL.editText?.text.toString()

            Fuel.post("https://mundraub.org/user/login", loginData.toList()).allowRedirects(false).responseString { request, response, result ->

                when (response.statusCode) {
                    -1 -> {runOnUiThread { Toast.makeText(this@PlantForm, getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show() }; finish()}
                    303 -> {}
                    else -> {runOnUiThread { Toast.makeText(this@PlantForm, getString(R.string.errMsgLogin), Toast.LENGTH_SHORT).show() }; finish()}
                }

                val cook = response.headers["Set-Cookie"].first()  // TODO: look at expiration date

                Fuel.get(submitUrl).header(Headers.COOKIE to cook).responseString { request, response, result ->

                    when (response.statusCode ) {
                        -1 -> {runOnUiThread { Toast.makeText(this@PlantForm, getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show() }; finish()}
                        200 -> {}
                        else -> {runOnUiThread { Toast.makeText(this@PlantForm, getString(R.string.errMsgAccess), Toast.LENGTH_SHORT).show() }; finish()}
                    }

                    val description_ = result.get().substringAfter("body[0][value]").substringAfter(">").substringBefore("<")
                    val description = HtmlCompat.fromHtml(description_, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()

                    val type_ = result.get().substringAfter("field_plant_category").substringBefore("\" selected=\"selected\"").takeLast(2)
                    val type = if (type_[0] == '"') type_.takeLast(1) else type_

                    val count = result.get().substringAfter("field_plant_count_trees").substringBefore("\"  selected=\"selected\"").takeLast(1)
                    val locationList = result.get().substringAfter("POINT (").substringBefore(")").split(' ')
                    plantData["form_id"] = "node_plant_edit_form"
                    plantData["field_plant_image[0][fids]"] = result.get().substringAfter("field_plant_image[0][fids]\" value=\"").substringBefore("\"")

                    typeTIL.postDelayed({
                        typeTIL.auto_text_input.editText?.setText( values[keys.indexOf(type)] ); updateType()
                        descriptionTIL.editText?.setText(description)
                        chipGroup.check(chipMap.getInverse(count) ?: R.id.chip_0)
                        updateLocationPicker( LatLng(locationList[1].toDouble(), locationList[0].toDouble()) )
                    }, 30)
                }
            }
        }

        Button(this).apply { text = getString(R.string.upld); lin.addView(this) }.setOnClickListener {

            loginData["name"] = userTIL.editText?.text.toString()
            loginData["pass"] = passTIL.editText?.text.toString()

            with (sharedPref.edit()) {
                putString("name", loginData["name"])
                putString("pass", loginData["pass"])
                apply()
            }
            val typeIndex = values.indexOf( typeTIL.editText?.text.toString() )

            val errors = listOf(
                getString(R.string.errMsgLoginInfos) to (loginData["name"]!!.isBlank() || loginData["pass"]!!.isBlank()),
                getString(R.string.errMsgDesc) to descriptionTIL.editText?.text.toString().isBlank(),
                getString(R.string.errMsgType) to (typeIndex == -1),
                getString(R.string.errMsgLoc) to ((location.latitude == 0.0 && location.longitude == 0.0) || locationPicker.text.toString() == "???")
            )
            errors.forEach { if (it.second) {
                runOnUiThread { Toast.makeText(this@PlantForm, it.first, Toast.LENGTH_SHORT).show() }
                return@setOnClickListener
            } }

            Fuel.post("https://mundraub.org/user/login", loginData.toList()).allowRedirects(false).responseString { request, response, result ->

                when (response.statusCode) {
                    -1 -> {runOnUiThread { Toast.makeText(this@PlantForm, getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show() }; return@responseString}
                    303 -> {}
                    else -> {runOnUiThread { Toast.makeText(this@PlantForm, getString(R.string.errMsgAccess), Toast.LENGTH_SHORT).show() }; return@responseString}
                }

                val cook = response.headers["Set-Cookie"].first()  // TODO: look at expiration date

                Fuel.get(submitUrl).header(Headers.COOKIE to cook).responseString { request, response, result ->
                    when (response.statusCode) {
                        -1 -> {runOnUiThread { Toast.makeText(this@PlantForm, getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show() }; return@responseString}
                        200 -> {}
                        else -> {runOnUiThread { Toast.makeText(this@PlantForm, getString(R.string.errMsgLogin), Toast.LENGTH_SHORT).show() }; return@responseString}
                    }

                    doSubmit(result, cook)
                }
            }
        }

        setContentView( ScrollView(this).apply { addView(lin) } )
    }
}

// TODO: MapFragment with preview of window :D
//      this will let users know not to write too much text
//      MF should NOT respond to touches
// TODO: use filter bar for species selection?
// TODO: handle GET error

// TODO: what if someone puts an emoji into Fruchtfund (Gboard keeps suggesting them)
// (TODO: dynamic icon preview on Fruchtfund using marker)
// TODO: image upload

// TODO login
//    - edit node
//    - delete node
//    - report node
//    - create account (need to allow it on backend first)
