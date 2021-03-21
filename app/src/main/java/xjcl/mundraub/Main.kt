package xjcl.mundraub

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.akexorcist.googledirection.DirectionCallback
import com.akexorcist.googledirection.GoogleDirection
import com.akexorcist.googledirection.constant.AvoidType
import com.akexorcist.googledirection.constant.TransportMode
import com.akexorcist.googledirection.model.Direction
import com.akexorcist.googledirection.model.Step
import com.akexorcist.googledirection.util.DirectionConverter
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.*
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.squareup.picasso.Picasso
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.URL
import java.util.*
import kotlin.concurrent.thread


class Main : AppCompatActivity(), OnMapReadyCallback, OnCameraIdleListener, ActivityCompat.OnRequestPermissionsResultCallback {

    lateinit var mapFragment : FruitBarMapFragment
    var onCameraIdleEnabled : Boolean = true

    enum class ItemMenu(val value: Int) {
        PLANT_LIST(1),
        PLANT_FORM(2),
        IMPRINT(10),
        PRIVACY(11),
        SETTINGS(99),
    }

    private fun removePolylines() {
        if (polylinesOnScreen.isEmpty()) return
        runOnUiThread {
            for (polyline in polylinesOnScreen)
                polyline.remove()
            polylinesOnScreen.clear()
        }
    }

    // --- Place a single marker on the GoogleMap, and prepare its info window, using parsed JSON class ---
    private fun addMarkerFromFeatures(features: List<Feature>) {
        for (featureChunk in features.chunked(12)) {
            val new_markers = mutableListOf<Triple<LatLng, Marker, MarkerData>>()
            runOnUiThread {
                for (feature in featureChunk) {
                    val latlng = LatLng(feature.pos[0], feature.pos[1])
                    if (markers.contains(latlng)) continue
                    val md = featureToMarkerData(this, feature)
                    val mo = MarkerOptions().position(latlng).title(md.title).icon(md.icon).anchor(.5F, if (md.type == "cluster") .5F else 1F)
                    val mark = mMap.addMarker(mo)
                    new_markers.add(Triple(latlng, mark, md))
                }

                markerContext.execute {
                    new_markers.forEach {
                        val (latlng, mark, md) = it
                        markers[latlng] = mark
                        markersData[latlng] = md
                    }
                }
            }
        }
    }

    // --- Place a list of markers on the GoogleMap ("var markers"), using raw JSON String ---
    // Note that the HashMap 'markers' is only modified in the markerContext to avoid concurrency issues
    private fun addLocationMarkers(jsonStrPre: String) {
        markerContext.execute {
            Log.e("addLocationMarkers", "ENTER " + markers.size.toString() + " " + jsonStrPre)
            val jsonStr = if (jsonStrPre == "null") "{\"features\":[]}" else jsonStrPre

            // --- parse newly downloaded markers ---
            // API inconsistently either returns String or double/int... -> strip away double quotes
            val jsonStrClean = Regex(""""-?[0-9]+.?[0-9]+"""").replace(jsonStr) { it.value.substring(1, it.value.length - 1) }
            val root = Json.decodeFromString<Root>(jsonStrClean)

            // --- remove old markers not in newly downloaded set (also removes OOB markers) ---
            val featuresSet = root.features.map { LatLng(it.pos[0], it.pos[1]) }.toSet()
            for (markChunk in markers.toMap().entries.chunked(12)) {  // copy constructor
                for (mark in markChunk) {
                    if (featuresSet.contains(mark.key)) continue
                    markers.remove(mark.key)
                    markersData.remove(mark.key)
                    if (mark.key == polylinesLatLng)
                        removePolylines()
                }

                runOnUiThread {
                    for (mark in markChunk) {
                        if (featuresSet.contains(mark.key)) continue
                        mark.value.remove()
                    }
                }
            }

            // --- add newly downloaded markers not already in old set ---
            addMarkerFromFeatures(root.features)
            Log.e("addLocationMarkers", "EXIT " + markers.size.toString())
        }
    }

    // --- Update markers when user finished moving the map ---
    private fun updateMarkers(callback : () -> Unit = {}) {
        val zoom = mMap.cameraPosition.zoom
        Log.e("updateMarkers", "zoom $zoom")
        if (zoom == 2F || zoom == 3F) return  // Bugfix, do not remove, see commit message
        val bboxLo = mMap.projection.visibleRegion.latLngBounds.southwest
        val bboxHi = mMap.projection.visibleRegion.latLngBounds.northeast

        // API documented here: https://github.com/niccokunzmann/mundraub-android/blob/master/docs/api.md
        val url = "https://mundraub.org/cluster/plant?bbox=${bboxLo.longitude},${bboxLo.latitude},${bboxHi.longitude},${bboxHi.latitude}" +
             "&zoom=${(zoom + .25F).toInt()}&cat=${selectedSpeciesStr}"

        Log.e("updateMarkers", "GET $url")

        thread {
            val jsonStr = try { URL(url).readText() } catch (ex: Exception) {
                runOnUiThread { Toast.makeText(this,  getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show() }
                return@thread
            }
            addLocationMarkers(jsonStr)
            callback()
        }
    }

    override fun onCameraIdle() {
        if (onCameraIdleEnabled) updateMarkers()
    }

    // --- Download detailed node description and stick it into marker info window ---
    fun downloadMarkerData(marker : Marker) {
        val md = markersData[marker.position] ?: return
        if (md.description != null || md.nid == null) return

        thread {
            // --- Download number of finds and description ---
            val htmlStr = try { URL("https://mundraub.org/node/${md.nid}").readText() } catch (ex : Exception) {
                runOnUiThread { Toast.makeText(this,  getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show() }
                return@thread
            }

            fun extractUnescaped(htmlStr : String, after : String, before : String) : String {
                val extractEscaped = htmlStr.substringAfter(after).substringBefore(before, "(no data)")
                return unescapeHtml(extractEscaped)
            }

            val number = extractUnescaped(htmlStr, "Anzahl: <span class=\"tag\">", "</span>")
            val description = extractUnescaped(htmlStr, "<p>", "</p>")
            md.uploader = extractUnescaped(htmlStr.substringAfter("typeof=\"schema:Person\""), ">", "</span>")
            md.uploadDate = extractUnescaped(htmlStr.substringAfter("am <span>"), ", ", " - ")
            md.description = "[$number] $description"

            runOnUiThread { marker.showInfoWindow() }

            // --- Download image in lowest quality ---
            val imageURL = htmlStr.substringAfter("srcset=\"", "").substringBefore(" ")
            if (imageURL.isBlank() || md.image != null) return@thread

            runOnUiThread {
                Log.e("onMarkerClickListener", "Started Picasso on UI thread now ($imageURL)")
                picassoBitmapTarget.md = md
                picassoBitmapTarget.marker = marker
                Picasso.with(this@Main).load("https://mundraub.org/$imageURL").into(picassoBitmapTarget)
            }
        }
    }

    fun markerOnClickListener(marker : Marker): Boolean {
        if (markersData[marker.position]?.type ?: "" == "cluster") return true
        marker.showInfoWindow()

        // --- Click on FAB will give directions to Marker in Google Maps app ---
        fab.setOnClickListener {
            fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
                if (location == null) return@addOnSuccessListener
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                    "http://maps.google.com/maps?saddr=${location.latitude}, ${location.longitude}&daddr=${marker.position.latitude}, ${marker.position.longitude}"
                )))
            }
        }
        fab.animate().x(fabAnimationFromTo.second)

        // TODO only show if near the user
        // TODO delete all previous routes
        // TODO remove polyline if marker gets removed
        fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return@addOnSuccessListener
            if (location == null) return@addOnSuccessListener
            val distance = floatArrayOf(0F)
            Location.distanceBetween(marker.position.latitude, marker.position.longitude, location.latitude, location.longitude, distance)
            if (distance[0] > 20000) {
                Log.e("distance", "distance ${distance[0]} is larger than 20 km, not plotting")
                return@addOnSuccessListener removePolylines()
            }

            GoogleDirection.withServerKey(getString(R.string.google_maps_key))
                .from(LatLng(location.latitude, location.longitude))
                .to(marker.position)
                .transportMode(TransportMode.DRIVING)
                .avoid(AvoidType.TOLLS)
                .avoid(AvoidType.FERRIES)
                .execute(object : DirectionCallback {
                    override fun onDirectionSuccess(direction: Direction?) {
                        Log.e("onDirectionSuccess", direction.toString())
                        if (direction == null || !direction.isOK)
                            return

                        try {
                            removePolylines()
                            val stepList: List<Step> = direction.routeList[0].legList[0].stepList
                            val polylines = DirectionConverter.createTransitPolyline(
                                this@Main, stepList, 5, Color.RED, 3, Color.RED)
                            for (polyline in polylines)
                                polylinesOnScreen.add(mMap.addPolyline(polyline))
                            polylinesLatLng = marker.position
                        } catch (e: java.lang.Exception) {
                            Log.e("polylines", e.stackTraceToString())
                        }
                    }

                    override fun onDirectionFailure(t: Throwable) {
                        Log.e("onDirectionFailure", t.toString())
                    }
                })
        }

        val targetPosition = vecAdd(marker.position, vecMul(.25, vecSub(mMap.projection.visibleRegion.farLeft, mMap.projection.visibleRegion.nearLeft)))
        mMap.animateCamera(CameraUpdateFactory.newLatLng(targetPosition), 300, null)

        downloadMarkerData(marker)
        return true
    }

    // --- OnInternetConnected: Automatically update (download) markers ---
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun setUpNetworking() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = runOnUiThread { mMap.animateCamera( CameraUpdateFactory.zoomBy(0F) ) }
        })
    }

    // --- On startup: Prepare map and cause onRequestPermissionsResult to be called ---
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.mapType = getSharedPreferences("global", Context.MODE_PRIVATE).getInt("mapType", MAP_TYPE_NORMAL)
        mMap.setOnCameraIdleListener(this)
        mMap.setPadding(totalLeftPadding, 0, 0, 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) setUpNetworking()

        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        ActivityCompat.requestPermissions(this, permissions, 0)

        // --- Build a vertical layout to provide an info window for a marker ---
        // https://stackoverflow.com/a/31629308/2111778
        mMap.setInfoWindowAdapter(object : InfoWindowAdapter {
            override fun getInfoWindow(arg0: Marker): View? = null

            override fun getInfoContents(marker: Marker): View {
                val md = markersData[marker.position] ?: return TextView(this@Main)

                val months = mapFragment.createMonthsBar(md)
                months.measure(0, 0)
                val masterWidth = months.measuredWidth

                val info = LinearLayout(this@Main)
                info.orientation = LinearLayout.VERTICAL

                val photo = ImageView(this@Main)
                photo.setImageBitmap(scaleToWidth(md.image, masterWidth))
                if (md.image != null) info.addView(photo)

                val description = TextView(this@Main)
                description.width = masterWidth
                description.textSize = 12F
                if (md.type != "cluster") info.addView(description)
                if (md.description != null) {
                    description.text = md.description
                    val byLine = LinearLayout(this@Main)

                    val uploader = TextView(this@Main)
                    uploader.textSize = 10.5F
                    uploader.text = md.uploader
                    uploader.gravity = Gravity.LEFT
                    uploader.maxWidth = masterWidth
                    uploader.measure(0, 0)

                    val uploadDate = TextView(this@Main)
                    uploadDate.textSize = 10.5F
                    uploadDate.text = md.uploadDate
                    uploadDate.gravity = Gravity.RIGHT
                    uploadDate.maxWidth = masterWidth
                    uploadDate.measure(0, 0)

                    val whitespace = TextView(this@Main)
                    whitespace.width = masterWidth - uploader.measuredWidth - uploadDate.measuredWidth

                    byLine.addView(uploader)
                    byLine.addView(whitespace)
                    byLine.addView(uploadDate)

                    info.addView(byLine)
                } else {
                    description.text = this@Main.getString(R.string.loading)
                }

                val title = TextView(this@Main)
                title.setTextColor(md.fruitColor)
                title.gravity = Gravity.CENTER
                title.setTypeface(null, Typeface.BOLD)
                title.text = marker.title
                info.addView(title)

                // no month/season information in this case so return early
                if (md.monthCodes.all { it == '_' })
                    return info

                val seasonText = TextView(this@Main)
                seasonText.setTextColor(Color.BLACK)
                seasonText.text = this@Main.getString(if (md.isSeasonal) R.string.inSeason else R.string.notInSeason)
                info.addView(seasonText)

                val day = RelativeLayout(this@Main)
                val tv = TextView(this@Main)
                tv.text = Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString()
                tv.setTextColor(if (md.isSeasonal) md.fruitColor else Color.GRAY)
                tv.setTypeface(null, Typeface.BOLD)
                tv.measure(0, 0)
                val params = RelativeLayout.LayoutParams(tv.measuredWidth, tv.measuredHeight)
                params.leftMargin = ( masterWidth / 12F * (md.curMonth - 1) - tv.measuredWidth / 2F ).toInt().coerceIn(0, masterWidth - tv.measuredWidth)
                day.addView(tv, params)

                info.addView(day)
                info.addView(months)
                return info
            }
        })

        // --- Disappear the navigation button once window closes ---
        mMap.setOnInfoWindowCloseListener {
            fab.animate().x(fabAnimationFromTo.first)
        }

        mMap.setOnInfoWindowLongClickListener {
            markersData[it.position]?.let { md ->
                runOnUiThread { editOrReportLauncher(this, md.nid ?: -1) }
        }}

        // --- Custom zoom to marker at a *below-center* position to leave more space for its large info window ---
        mMap.setOnMarkerClickListener { marker -> markerOnClickListener(marker) }
    }

    // --- When user rotates phone, re-download markers for the new screen size ---
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        mapFragment.mapViewPost()

        // dummy zoom to trigger onCameraIdle with *correct* orientation  https://stackoverflow.com/a/61993030/2111778
        mMap.animateCamera( CameraUpdateFactory.zoomBy(0F) )
    }

    // --- On startup: If GPS enabled, then zoom into user, else zoom into Germany ---
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        fusedLocationClient.lastLocation.addOnFailureListener(this) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(51.17, 10.45), 6F))  // Germany
        }
        fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
            if (location == null) return@addOnSuccessListener
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 13F))
        }

        if (grantResults.isEmpty() || grantResults[0] == PackageManager.PERMISSION_DENIED) return

        mMap.isMyLocationEnabled = true  // show blue circle on map
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (mapTypeChanged) {
            mapTypeChanged = false
            mMap.mapType = getSharedPreferences("global", Context.MODE_PRIVATE).getInt("mapType", MAP_TYPE_NORMAL)
        }
        if (requestCode == ActivityRequest.PlantList.value) return mMap.animateCamera( CameraUpdateFactory.zoomBy(0F) )  // might have modifed markers
        if (!(requestCode == ActivityRequest.PlantForm.value && resultCode == Activity.RESULT_OK && data != null)) return

        // if we add or edit a marker, resume at its location with open info window (= simulate click)
        val lat = data.getDoubleExtra("lat", 0.0)
        val lng = data.getDoubleExtra("lng", 0.0)
        val nid = data.getStringExtra("nid") ?: ""

        onCameraIdleEnabled = false
        mapFragment.handleFilterClick(null, 99) {true}
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 18F), 1, object : CancelableCallback {
            override fun onFinish() = updateMarkers {
                // The problem here is that addMarkerFromFeature does not wait for the UI thread to finish (and I found no good way
                //  to do this) so instead we dirtily just *sleep* here
                Thread.sleep(250)
                onCameraIdleEnabled = true

                // simulate click on just-uploaded/updated marker
                markerContext.execute {
                    markers.filter { markersData[it.key]?.nid.toString() == nid }.forEach { runOnUiThread { markerOnClickListener(it.value) }}
                }
            }
            override fun onCancel() {}
        })
    }

    private fun openUrl(link: String) =
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))

    // Handle ActionBar option selection
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            ItemMenu.PLANT_LIST.value -> startActivityForResult(Intent(this, PlantList::class.java), ActivityRequest.PlantList.value)
            ItemMenu.PLANT_FORM.value -> startActivityForResult(Intent(this, PlantForm::class.java), ActivityRequest.PlantForm.value)
            ItemMenu.SETTINGS.value -> startActivityForResult(Intent(this, AppSettings::class.java), ActivityRequest.IRRELEVANT.value)
            ItemMenu.IMPRINT.value -> openUrl(getString(R.string.imprint_url))
            ItemMenu.PRIVACY.value -> openUrl(getString(R.string.privacy_url))
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun addMenuItem(menu: Menu, id: Int, titleId: Int, icon: Int?) {
        menu.add(id, id, id, getString(titleId)).apply {
            if (icon == null) setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)
            else {
                val icon_ = ContextCompat.getDrawable(this@Main, icon) ?: return
                icon_.setColorFilter(resources.getColor(R.color.colorPrimary), PorterDuff.Mode.SRC_IN)
                setIcon(icon_).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
        }
    }

    // Create the ActionBar options menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        addMenuItem(menu, ItemMenu.PLANT_LIST.value, R.string.title_activity_plant_list, R.drawable.material_list)
        addMenuItem(menu, ItemMenu.PLANT_FORM.value, R.string.addNode, R.drawable.material_add_location)
        addMenuItem(menu, ItemMenu.SETTINGS.value, R.string.title_activity_app_settings, null)
        addMenuItem(menu, ItemMenu.IMPRINT.value, R.string.imprint, null)
        addMenuItem(menu, ItemMenu.PRIVACY.value, R.string.privacy, null)
        return true
    }

    // TODO: introduced in v24, delete in v25
    private fun testerInvitation() {
        val sharedPref = this.getSharedPreferences("global", Context.MODE_PRIVATE)
        val dialogShown = sharedPref.getBoolean("testerDialogShown", false)
        if (dialogShown) return

        fun suppressDialog() =
            with (sharedPref.edit()) {
                putBoolean("testerDialogShown", true)
                apply()
            }

        AlertDialog.Builder(this).setTitle("Beta-Tester gesucht!")
            .setMessage("Ich (Jan) suche Beta-Tester für neue Features im Mundraub Navigator. " +
                        "Du kriegst die nächste Version automatisch auf dein Gerät " +
                        "und kannst mit mir direkt auf Discord sprechen und Feedback geben. " +
                        "Es würde uns freuen wenn du mitmachst!" +
                        "\n\nVG Jan (Entwickler) und Kai (mundraub.org)")
            .setNegativeButton("Discord beitreten") { _, _ -> suppressDialog(); openUrl("https://discord.gg/SAVF66ZKQn") }
            .setPositiveButton("Nein danke") { _, _ -> suppressDialog() }
            .create().show()
    }

    // --- On startup: Prepare classes ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        Log.e("UA", System.getProperty("http.agent")?:"")
        supportActionBar?.apply {
            val trueWidth = resources.displayMetrics.widthPixels / resources.displayMetrics.density
            Log.e("trueWidth", trueWidth.toString())
            val navStr = if (trueWidth > 500) "Navigator" else if (trueWidth > 400) "Nav." else ""
            title = primaryColorTitle("$navStr v${BuildConfig.VERSION_NAME}")
            setHomeAsUpIndicator(R.drawable.mundraub_logo_bar_48dp)  // export with 15px border
            displayOptions = ActionBar.DISPLAY_SHOW_HOME or ActionBar.DISPLAY_SHOW_TITLE or ActionBar.DISPLAY_HOME_AS_UP or ActionBar.DISPLAY_USE_LOGO
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mapFragment = supportFragmentManager.findFragmentById(R.id.map) as FruitBarMapFragment
        mapFragment.getMapAsync(this)
        // retains markers if user rotates phone etc. (useful offline)  https://stackoverflow.com/a/22058966/2111778
        mapFragment.retainInstance = true

        if (resources.getBoolean(R.bool.force_portrait))
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        Log.e("force_portrait", resources.getBoolean(R.bool.force_portrait).toString())

        testerInvitation()
    }

    override fun onBackPressed() {
        moveTaskToBack(true)  // do not call onCreate after user accidentally hits Back (useful offline)
    }
}

// TODO assets
//    - use svg instead of xhdpi markers

// TODO testing
//    - Firebase Labs Robo Script
//    - Firebase Labs credentials on site

// TODO branding
//    - 'About app' and 'Rate this app' options
//    - explain Mundraub, mission, rules, ...
//    - change app icon to Manni Mundraub (?)

// TODO publishing
//    - write blog post about it
//    - publish to reddit about it
//    - official email newsletter

// TODO QA
//    - Full automated UI scripts using Espresso
//    - Node test with image: https://mundraub.org/node/75327
//    - Ensure correct season information
//    - Test different pixel densities by setting screen rezo on my phone
//    - Test both supported languages (EN/DE)
//    - Test rotation
//    - Test offline use
//    - Test with special characters: HTML-escaped (<>&) and German (ae, oe, ue)


// TODO clusters
//    - use Material design cluster icon with shadow
//        - should fix font centering issue too
//    - request max zoom level earlier (clusters are a useless anti-affordance)

// TODO marker filter
//    - apply darkened markers to map too
//    - animation / FloatingActionButton (slides out when tapped, also can be used to reset filtering)
//    - fix phone rotation  -> put 'val linear' into own singleton class that has an updateHeight function
//         -> either remove drawer, shrink it (???) or put it on the x axis (bottom) (?)
//         -> minor priority as the app is not really usable in landscape mode

// TODO latlng boundaries
//    - extend boundaries to go slightly offscreen so less re-loading needed?

// TODO bugs
//    - when tapping a marker, markers reload, so it sometimes deletes the marker in focus
//    - better workaround for initial Maps download issue

// * TODO pokemon
//    * favorite markers
//        * in a cardview list  -> wait no on the main map!!
//            * um we need a cardview of added markers first
//        * store offline
//    - detect when someone "visits" a marker
//    - list of recently visited or starred markers
//    - list of which marker types have ever been visited (including link to most recent one)
//    - list of how common each marker type is

// TODO UI
//    - all markers jump when pressing filter?
//    - "force reload" button
//    - from PlantList: land user on marker view rather than edit view
//    - show marker icons in AutocompleteTextView

// TODO marker persistence
//    - startup: load markers from last time
//    - keep markers near user location always
//    - favorite markers that get permanently stored

// TODO meta
//    - make docs/ dir


// TODO wontfix
//    - rarely used marker types: groups, actions, cider, saplings
//    - draw in sorted order and/or with z-score so front markers are in front -> rarely needed



// TODO immediate
//    - admins unpublished
//    - map marker icons

// TODO reset password in-app
// TODO impressum


// TODO IDEAS 2021-03-07 (inspiration from yelp app)
// - impressum
// - git fix on website: anchor + jitter
// - rotate screen on tablet -> infobar -> use chips like yelp?
// - bug: minimum example markers staying on
// - allow image upload
// - link to flora incognita app
// - don't change messagebox size (marker placeholder for img)
//     -> separate page with full description and editing options (+ gmaps + latlng + addr)
// - download/fav markers for offline use -> map?
// - long-term: up- and downvotes tied to accounts
// - git zugriff und zoom bug auf der webseite?
// - get beta testers (private or public? google email? agree to send them stuff)
