package xjcl.mundraub.activities

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PorterDuff
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.requests.upload
import com.github.kittinunf.result.Result
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.button.MaterialButton
import kotlinx.android.synthetic.main.activity_plant_form.*
import kotlinx.android.synthetic.main.chip_group_number.*
import xjcl.mundraub.R
import xjcl.mundraub.data.ActivityRequest
import xjcl.mundraub.data.fusedLocationClient
import xjcl.mundraub.data.markerDataManager
import xjcl.mundraub.data.treeIdToMarkerIcon
import xjcl.mundraub.utils.getInverse
import xjcl.mundraub.utils.scrapeFormToken
import java.io.IOException
import java.util.*
import kotlin.concurrent.thread


/**
 * TODO:  https://github.com/kittinunf/fuel/issues/594
 */
class PlantForm : AppCompatActivity() {

    val plantData = mutableMapOf<String, Any>(
        "form_id" to "node_plant_form",
        "changed" to "0",
        "body[0][format]" to "simple_text",
        "field_plant_image[0][_weight]" to "0",
        "field_plant_image[0][fids]" to "",
        "field_plant_image[0][display]" to "1",
        "address-search" to "",
        "accept_rules" to "1",  // (USER)
        "advanced__active_tab" to "",
        "op" to "Speichern"
    )

    val deleteData = mutableMapOf(
        "confirm" to "1",
        "form_id" to "node_plant_delete_form",
        "op" to "Löschen"
    )

    val chipMap = mapOf(R.id.chip_0 to "0", R.id.chip_1 to "1", R.id.chip_2 to "2", R.id.chip_3 to "3")

    var submitUrl = "https://mundraub.org/node/add/plant"
    var intentNid = -1
    lateinit var cookie : String

    fun LatLng(location : Location): LatLng = LatLng(location.latitude, location.longitude)

    var location : LatLng = LatLng(0.0, 0.0)
    lateinit var locationPicker : MaterialButton

    /**
     * Continuously try getting location in background because sometimes it randomly fails :(
     *  (or there might be weak signal or no internet)
     */
    fun geocodeLocation(latLng: LatLng) {
        location = latLng
        if (!::locationPicker.isInitialized) return
        locationPicker.text = getString(R.string.geocoding)

        thread {
            repeat(10) {
                val gcd = Geocoder(this@PlantForm, Locale.getDefault())
                Log.e("geocodeLocation", "geolocating...")
                val address = try { gcd.getFromLocation(location.latitude, location.longitude, 1).firstOrNull() }
                    catch (ex : IOException) { null }
                address?.let {
                    val addressStr = (0..address.maxAddressLineIndex).map { address.getAddressLine(it) }.joinToString("\n")
                    Log.e("geocodeLocation", addressStr)
                    runOnUiThread { locationPicker.text = addressStr }
                    return@thread
                }
            }
            Log.e("geocodeLocation", "geolocating failed")
            runOnUiThread { locationPicker.text = getString(R.string.geocoding_failed) }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.e("onActivityResult", "onActivityResult ${requestCode} ${resultCode}")
        if (requestCode == ActivityRequest.LocationPicker.value && resultCode == Activity.RESULT_OK && data != null) {
            val lat = data.getDoubleExtra("lat", 0.0)
            val lng = data.getDoubleExtra("lng", 0.0)
            geocodeLocation(LatLng(lat, lng))
        }
        if (requestCode == ActivityRequest.Login.value && resultCode == Activity.RESULT_OK) recreate()
        if (requestCode == ActivityRequest.Login.value && resultCode != Activity.RESULT_OK) finish()
    }

    private fun finishSuccess(nid : String = "") {
        Log.e("exitToMain", "exit to main at $location with $nid")
        val output = Intent().putExtra("lat", location.latitude).putExtra("lng", location.longitude).putExtra("nid", nid)
        setResult(Activity.RESULT_OK, output)
        finish()
    }

    private fun plantDeleteDialog() {
        AlertDialog.Builder(this).setMessage(R.string.reallyDelete)
            .setPositiveButton(R.string.yes) { _, _ -> plantDelete() }
            .setNegativeButton(R.string.no) { _, _ -> }
            .create().show()
    }

    private fun plantDelete() {
        val deleteUrl = "https://mundraub.org/node/$intentNid/delete"
        Log.e("plantDelete", deleteUrl)
        Fuel.get(deleteUrl).header(Headers.COOKIE to cookie).allowRedirects(false).responseString { request, response, result ->

            deleteData["form_token"] = scrapeFormToken(result.get())

            Log.e("plantDelete", " ${result.get()}")
            Log.e("plantDelete", " $deleteData")

            Fuel.post(deleteUrl, deleteData.toList()).header(Headers.COOKIE to cookie).allowRedirects(false).responseString { request, response, result ->

                Log.e("plantDelete", result.get())
                when (response.statusCode) {
                    -1 -> return@responseString runOnUiThread { Toast.makeText(this@PlantForm, getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show() }
                    303 -> {}
                    else -> return@responseString runOnUiThread { Toast.makeText(this@PlantForm, getString(R.string.errMsgOpFail), Toast.LENGTH_SHORT).show() }
                }

                markerDataManager.invalidateMarker(this, intentNid.toString())
                runOnUiThread {
                    Toast.makeText(this@PlantForm, getString(R.string.errMsgDeleteSuccess), Toast.LENGTH_SHORT).show()
                    finishSuccess()
                }
            }
        }
    }

    // Handle ActionBar option selection
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            9 -> { plantDeleteDialog(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Create the ActionBar options menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (intentNid == -1) return true
        val icon9 = ContextCompat.getDrawable(this, R.drawable.material_delete) ?: return true
        icon9.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        menu.add(9, 9, 9, getString(R.string.delete)).setIcon(icon9).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    /**
     * ADD Form if intentNid == -1 or not specified
     * EDIT Form if intentNid > -1
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plant_form)

        intentNid = intent.getIntExtra("nid", -1)
        supportActionBar?.title = if (intentNid > -1) getString(R.string.editNode) else getString(R.string.addNode)

        doWithLoginCookie(this, loginIfMissing = true, callback = { doCreate() })
    }

    private fun doCreate() {
        val sharedPref = this.getSharedPreferences("global", Context.MODE_PRIVATE)
        cookie = sharedPref.getString("cookie", null) ?: return finish()

        // TODO make this a proper map and call getInverse() on it
        val keys = treeIdToMarkerIcon.keys.map { it.toString() }
        val values = keys.map { key -> getString(resources.getIdentifier("tid${key}", "string", packageName)) }
        typeTIED.setAdapter( ArrayAdapter(this, R.layout.activity_plant_form_item, values) )
        fun updateType() {
            val typeIndex = values.indexOf( typeTIED.text.toString() )
            if (typeIndex == -1) return
            val left = ContextCompat.getDrawable(this, treeIdToMarkerIcon[keys[typeIndex].toInt()] ?: R.drawable.icon_otherfruit)
            locationPicker.setCompoundDrawablesWithIntrinsicBounds(left, null, null, null)
        }
        typeTIED.setOnFocusChangeListener { _, _ -> updateType() }
        typeTIED.setOnItemClickListener { _, _, _, _ -> updateType() }

        locationPicker = button_loc.apply {
            setOnClickListener {
                val typeIndex = values.indexOf(typeTIED.text.toString())
                val intent = Intent(context, LocationPicker::class.java).putExtra("tid", if (typeIndex == -1) 12 else keys[typeIndex].toInt())
                    .putExtra("lat", location.latitude).putExtra("lng", location.longitude)
                startActivityForResult(intent, ActivityRequest.LocationPicker.value)
            }
        }

        fusedLocationClient.lastLocation.addOnSuccessListener(this) { it?.let { location ->
            geocodeLocation(LatLng(location))
        }}

        // ----

        fun plantSubmit(result : Result<String, FuelError>) {
            plantData["changed"] = result.get().substringAfter("name=\"changed\" value=\"").substringBefore("\"")
            plantData["form_token"] = scrapeFormToken(result.get())
            plantData["body[0][value]"] = descriptionTIED.text.toString()
            plantData["field_plant_category"] = keys[values.indexOf( typeTIED.text.toString() )]
            plantData["field_plant_count_trees"] = chipMap[chipGroup.checkedChipId] ?: "0"
            plantData["field_position[0][value]"] = "POINT(${location.longitude} ${location.latitude})"
            plantData["field_plant_address[0][value]"] = locationPicker.text.toString()

            Log.e("POSTplantData", plantData.toString())

            val img = BitmapFactory.decodeResource(resources, R.drawable.frame_apple)

            //plantData["files[field_plant_image_0][]"] = FileDataPart.from("meal.jpg", name="image")
            //plantData["files[field_plant_image_0][]"] = assets.open("meal.jpg").readBytes().decodeToString()

            Log.e("POSTplantData", plantData.toString())

//            Content-Disposition: form-data; name="files[field_plant_image_0][]"; filename="IMG-20200526-WA0002(1).jpg"
//            Content-Type: image/jpeg

            Fuel.post(submitUrl, plantData.toList()).header(Headers.COOKIE to cookie)
                //.data { request, url -> listOf(DataPart(File(FILE_URL), type = "file")) }
                .upload()
                .allowRedirects(false).responseString { request, response, result ->

                    Log.e("POSTrequest", request.body.toStream().readBytes().decodeToString())
                    Log.e("POSTresponse", result.get())

                    when (response.statusCode) {
                        -1 -> return@responseString runOnUiThread { Toast.makeText(this@PlantForm, getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show() }
                        303 -> {}
                        else -> return@responseString runOnUiThread { Toast.makeText(this@PlantForm, getString(R.string.errMsgOpFail), Toast.LENGTH_SHORT).show() }
                    }

                    if (intentNid > -1) markerDataManager.invalidateMarker(this, intentNid.toString())

                    val nid_ = result.get().substringAfter("?nid=", "").substringBefore("\"")
                    val nid = if (nid_.isNotEmpty()) nid_ else result.get().substringAfter("node/").substringBefore("\"")
                    runOnUiThread { Toast.makeText(this@PlantForm, getString(R.string.errMsgSuccess), Toast.LENGTH_LONG).show() }
                    finishSuccess(nid)
                }
        }

        if (intentNid > -1) {
            submitUrl = "https://mundraub.org/node/${intentNid}/edit"

            Fuel.get(submitUrl).header(Headers.COOKIE to cookie).responseString { request, response, result ->

                when (response.statusCode) {
                    -1 -> runOnUiThread { Toast.makeText(this@PlantForm, getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show(); finish() }
                    200 -> {}
                    else -> {
                        // TODO delete this, code has been moved to function
                        // No access to this resource -> try ReportPlant form instead
                        startActivityForResult(Intent(this, ReportPlant::class.java).putExtra("nid", intentNid), ActivityRequest.ReportPlant.value)
                        finish()
                    }
                }

                val description_ = result.get().substringAfter("body[0][value]").substringAfter(">").substringBefore("<")
                val description = HtmlCompat.fromHtml(description_, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()

                val type_ = result.get().substringAfter("field_plant_category").substringBefore("\" selected=\"selected\"").takeLast(2)
                val type = if (type_[0] == '"') type_.takeLast(1) else type_

                val count = result.get().substringAfter("field_plant_count_trees").substringBefore("\"  selected=\"selected\"").takeLast(1)
                val locationList = result.get().substringAfter("POINT (").substringBefore(")").split(' ')
                plantData["form_id"] = "node_plant_edit_form"
                plantData["field_plant_image[0][fids]"] = result.get().substringAfter("field_plant_image[0][fids]\" value=\"").substringBefore("\"")
                Log.e("result get", result.get())

                typeTIL.postDelayed({
                    typeTIED.setText( values[keys.indexOf(type)] ); updateType()
                    descriptionTIED.setText(description)
                    chipGroup.check(chipMap.getInverse(count) ?: R.id.chip_0)
                    geocodeLocation( LatLng(locationList[1].toDouble(), locationList[0].toDouble()) )
                }, 30)
            }
        }

        upld_button.setOnClickListener {

            val typeIndex = values.indexOf( typeTIED.text.toString() )

            val errors = listOf(
                getString(R.string.errMsgDesc) to descriptionTIED.text.toString().isBlank(),
                getString(R.string.errMsgType) to (typeIndex == -1),
                getString(R.string.errMsgLoc) to ((location.latitude == 0.0 && location.longitude == 0.0) ||
                        locationPicker.text.toString() == getString(R.string.geocoding) ||
                        locationPicker.text.toString() == getString(R.string.geocoding_failed))
            )
            errors.filter { it.second }.forEach {
                return@setOnClickListener runOnUiThread { Toast.makeText(this@PlantForm, it.first, Toast.LENGTH_SHORT).show() }
            }

            Fuel.get(submitUrl).header(Headers.COOKIE to cookie).responseString { request, response, result ->
                when (response.statusCode) {
                    -1 -> return@responseString runOnUiThread { Toast.makeText(this@PlantForm, getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show(); finish() }
                    200 -> {}
                    else -> return@responseString runOnUiThread { Toast.makeText(this@PlantForm, getString(R.string.errMsgLogin), Toast.LENGTH_SHORT).show(); finish() }
                }

                plantSubmit(result)
            }
        }
    }
}

/**
 * Long-click on marker
 *  -> edit if it is my own marker
 *  -> report if it is someone else's marker (= no edit rights)
 */
private fun editOrReportLauncherLoggedIn(activity : Activity, intentNid : Int) {
    val submitUrl = "https://mundraub.org/node/${intentNid}/edit"

    val sharedPref = activity.getSharedPreferences("global", Context.MODE_PRIVATE)
    val cook = sharedPref.getString("cookie", null) ?:
        return Toast.makeText(activity, activity.getString(R.string.errMsgAccess), Toast.LENGTH_SHORT).show()

    Fuel.get(submitUrl).header(Headers.COOKIE to cook).responseString { request, response, result ->
        when (response.statusCode) {
            -1 -> Toast.makeText(activity, activity.getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show()
            200 -> activity.startActivityForResult(Intent(activity, PlantForm::class.java).putExtra("nid", intentNid), ActivityRequest.PlantForm.value)
            else -> activity.startActivityForResult(Intent(activity, ReportPlant::class.java).putExtra("nid", intentNid), ActivityRequest.ReportPlant.value)
        }
    }
}

fun editOrReportLauncher(activity : Activity, intentNid : Int) {
    doWithLoginCookie(activity, loginIfMissing = true, callback = { editOrReportLauncherLoggedIn(activity, intentNid) })
}

// TODO: MapFragment with preview of window :D
//      this will let users know not to write too much text
//      MF should NOT respond to touches
// TODO: use filter bar for species selection?
// TODO: what if someone puts an emoji into Fruchtfund (Gboard keeps suggesting them)

// TODO: add icons to fruitTIL
// TODO: image upload
