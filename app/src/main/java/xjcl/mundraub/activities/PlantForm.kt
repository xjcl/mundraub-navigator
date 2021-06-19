package xjcl.mundraub.activities

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.text.HtmlCompat
import androidx.exifinterface.media.ExifInterface
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FileDataPart
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.result.Result
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.button.MaterialButton
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_plant_form.*
import kotlinx.android.synthetic.main.chip_group_number.*
import xjcl.mundraub.R
import xjcl.mundraub.data.*
import xjcl.mundraub.utils.getInverse
import xjcl.mundraub.utils.scrapeFormToken
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import kotlin.concurrent.thread


class PlantForm : AppCompatActivity() {

    val plantData = mutableMapOf(
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
    var hasImageURL = false
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
        Log.e("onActivityResult", "onActivityResult $requestCode $resultCode")

        if (requestCode == ActivityRequest.LocationPicker.value && resultCode == Activity.RESULT_OK && data != null) {
            val lat = data.getDoubleExtra("lat", 0.0)
            val lng = data.getDoubleExtra("lng", 0.0)
            geocodeLocation(LatLng(lat, lng))
        }

        // Cache image for upload
        if ((requestCode == ActivityRequest.ImagePick.value || requestCode == ActivityRequest.ImageCapture.value) &&
                resultCode == Activity.RESULT_OK) {

            val bytes =
                if (requestCode == ActivityRequest.ImagePick.value) {
                    val uri = data?.data ?: return
                    contentResolver.openInputStream(uri)?.readBytes() ?: return
                } else
                    File("${getExternalFilesDir(null)}/imgShot").readBytes()

            // Fix orientation byte issues: https://stackoverflow.com/a/15341203/2111778
            val exif = ExifInterface(bytes.inputStream())
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }

            val tmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val bitmap = Bitmap.createBitmap(tmp, 0, 0, tmp.width, tmp.height, matrix, true)

            upld_image.setImageBitmap(bitmap)
            FileOutputStream("$cacheDir/imgPicked").use {
                    out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
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

        if (File("$cacheDir/imgPicked").exists())
            File("$cacheDir/imgPicked").delete()

        // TODO make this a proper map and call getInverse() on it
        val keys = treeIdToMarkerIcon.keys.map { it.toString() }
        val values = keys.map { key -> getString(resources.getIdentifier("tid${key}", "string", packageName)) }
        typeTIED.setAdapter( ArrayAdapter(this, R.layout.activity_plant_form_item, values) )
        fun updateType() {
            val typeIndex = values.indexOf( typeTIED.text.toString() )
            if (typeIndex == -1) return
            val left = ContextCompat.getDrawable(this, treeIdToMarkerIcon[keys[typeIndex].toInt()] ?: R.drawable.icon_otherfruit)
            locationPicker.setCompoundDrawablesWithIntrinsicBounds(left, null, null, null)
            if (!hasImageURL && !File("$cacheDir/imgPicked").exists())
                upld_image.setImageDrawable(
                    ContextCompat.getDrawable(this, treeIdToMarkerFrame[keys[typeIndex].toInt()] ?: R.drawable.frame_otherfruit)
                )
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

        fusedLocationClient?.lastLocation?.addOnSuccessListener(this) { it?.let { location ->
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

            Log.e("plantData", plantData.toString())

            var uploadRequest = Fuel.upload(submitUrl, parameters=plantData.toList())

            if (File("$cacheDir/imgPicked").exists())
                uploadRequest = uploadRequest.add(FileDataPart(
                    File("$cacheDir/imgPicked"), name="files[field_plant_image_0][]", filename="meal.jpg")
                )

            upld_button.text = getString(R.string.uplding)

            uploadRequest
                .header(Headers.COOKIE to cookie)
                .allowRedirects(false)
                .responseString { request, response, result ->

                    Log.e("plantSubmit result", result.get())
                    upld_button.text = getString(R.string.upld)

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
                plantData["field_plant_image[0][fids]"] = result.get().substringAfter("field_plant_image[0][fids]\" value=\"", "").substringBefore("\"")

                val imageURL = result.get().substringAfter("\"edit-field-plant-image-0-preview\" src=\"", "").substringBefore("\"")
                Log.e("imageURL", "($imageURL)")
                if (imageURL.isNotBlank()) {
                    hasImageURL = true
                    runOnUiThread {
                        Log.e("onMarkerClickListener", "Started Picasso on UI thread now ($imageURL)")
                        Picasso.get().load("https://mundraub.org/$imageURL").placeholder(R.drawable.progress_animation).into(upld_image)
                    }
                }

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

        btn_img_pick.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            startActivityForResult(intent, ActivityRequest.ImagePick.value)
        }

        btn_img_capture.setOnClickListener {
            val f = File("${getExternalFilesDir(null)}/imgShot")
            val photoURI = FileProvider.getUriForFile(this, "${packageName}.fileprovider", f)
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply { putExtra(MediaStore.EXTRA_OUTPUT, photoURI) }
            startActivityForResult(intent, ActivityRequest.ImageCapture.value)
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
// TODO: add icons to fruitTIL
