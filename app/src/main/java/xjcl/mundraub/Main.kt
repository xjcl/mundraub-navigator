package xjcl.mundraub

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
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
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.*
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.squareup.picasso.Picasso
import kotlinx.serialization.json.Json
import java.net.URL
import java.util.*
import kotlin.concurrent.thread


class Main : AppCompatActivity(), OnMapReadyCallback, OnCameraIdleListener, ActivityCompat.OnRequestPermissionsResultCallback {

    lateinit var mapFragment : JanMapFragment
    var onCameraIdleEnabled : Boolean = true

    // --- Place a single marker on the GoogleMap, and prepare its info window, using parsed JSON class ---
    private fun addMarkerFromFeature(feature: Feature) {
        val latlng = LatLng(feature.pos[0], feature.pos[1])
        val md = featureToMarkerData(this, feature)
        val mo = MarkerOptions().position(latlng).title(md.title).icon(md.icon).anchor(.5F, if (md.type == "cluster") .5F else 1F)

        runOnUiThread {
            val mark = mMap.addMarker(mo)

            markerContext.execute {
                markers[latlng] = mark
                markersData[latlng] = md
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
            val root = Json.parse(Root.serializer(), jsonStrClean)

            // --- remove old markers not in newly downloaded set (also removes OOB markers) ---
            val featuresSet = root.features.map { LatLng(it.pos[0], it.pos[1]) }.toSet()
            for (mark in markers.toMap()) {  // copy constructor
                if (featuresSet.contains(mark.key)) continue
                runOnUiThread { mark.value.remove() }
                markers.remove(mark.key)
                markersData.remove(mark.key)
            }

            // --- add newly downloaded markers not already in old set ---
            for (feature in root.features) {
                val latlng = LatLng(feature.pos[0], feature.pos[1])
                if (markers.contains(latlng)) continue
                addMarkerFromFeature(feature)
            }
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

                // 12 month circles of 13 pixels width -- ugly but WRAP_CONTENT just would not work =(
                val density = resources.displayMetrics.density
                var masterWidth = (12 * 13 * density).toInt()

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

                    val uploader = TextView(this@Main)
                    uploader.textSize = 12F
                    uploader.text = md.uploader
                    uploader.gravity = Gravity.RIGHT
                    uploader.maxWidth = masterWidth
                    info.addView(uploader)

                    val uploadDate = TextView(this@Main)
                    uploadDate.textSize = 12F
                    uploadDate.text = md.uploadDate
                    uploadDate.gravity = Gravity.RIGHT
                    uploadDate.maxWidth = masterWidth
                    info.addView(uploadDate)
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

                val months = mapFragment.createMonthsBar(md)
                months.measure(0, 0)
                Log.e("width change", masterWidth.toString() + " -> " + months.measuredWidth)
                masterWidth = months.measuredWidth
                description.width = masterWidth
                photo.setImageBitmap(scaleToWidth(md.image, masterWidth))

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
                startActivityForResult(Intent(this, PlantForm::class.java).putExtra("nid", md.nid), 33)
        }}

        // --- Custom zoom to marker at a *below-center* position to leave more space for its large info window ---
        mMap.setOnMarkerClickListener { marker -> markerOnClickListener(marker) }
    }

    // --- When user rotates phone, re-download markers for the new screen size ---
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

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

    /**
     * Master table of Activity Request codes (startActivityForResult)
     *  PlantForm 33
     *      Add/Edit marker
     *      @param nid (-1 for Add, actual nid for Edit)
     *      @return lat/lng+nid of new marker in Add case
     *  ReportPlant 35
     *      if not my plant, then ability to report, else forward to editing (PlantForm 33)
     *      @param nid
     *      @return None
     *  LocationPicker 42
     *      @param tid (icon to use) + lat/lng (position to start view)
     *      @return lat/lng (position user inputted)
     *  Login 55
     *      No I/O, this writes to the sharedPreferences object
     *  Register 56
     *      No I/O, no details stored, user sets password later anyway
     *  PlantList 60
     *      Used to reload markers if edited through the list
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 60) { mMap.animateCamera( CameraUpdateFactory.zoomBy(0F) ); return }  // might have modifed markers
        if (!(requestCode == 33 && resultCode == Activity.RESULT_OK && data != null)) return

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

    // Handle ActionBar option selection
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            9 -> { startActivityForResult(Intent(this, PlantForm::class.java), 33); true }
            7 -> { startActivityForResult(Intent(this, PlantList::class.java), 60); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Create the ActionBar options menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val icon9 = ContextCompat.getDrawable(this, R.drawable.material_add_location) ?: return true
        icon9.setColorFilter(resources.getColor(R.color.colorPrimary), PorterDuff.Mode.SRC_IN)
        val icon7 = ContextCompat.getDrawable(this, R.drawable.material_list) ?: return true
        icon7.setColorFilter(resources.getColor(R.color.colorPrimary), PorterDuff.Mode.SRC_IN)

        menu.add(7, 7, 7, "List").setIcon(icon7).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(9, 9, 9, "Add").setIcon(icon9).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    // --- On startup: Prepare classes ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.apply {
            val navStr = if (resources.displayMetrics.widthPixels  / resources.displayMetrics.density > 500) "Navigator" else "Nav."
            title = primaryColorTitle("$navStr v${BuildConfig.VERSION_NAME}")
            setBackgroundDrawable(ColorDrawable(Color.WHITE))
            setHomeAsUpIndicator(R.drawable.mundraub_logo_bar_48dp)  // export with 15px border
            displayOptions = ActionBar.DISPLAY_SHOW_HOME or ActionBar.DISPLAY_SHOW_TITLE or ActionBar.DISPLAY_HOME_AS_UP or ActionBar.DISPLAY_USE_LOGO
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        mapFragment = supportFragmentManager.findFragmentById(R.id.map) as JanMapFragment
        mapFragment.getMapAsync(this)
        // retains markers if user rotates phone etc. (useful offline)  https://stackoverflow.com/a/22058966/2111778
        mapFragment.retainInstance = true
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
//    * show season information in info window (only a single row)
//    - all markers jump when pressing filter?
//    * immediately download info when tapping marker (1 instead of 2 taps)
//    - "force reload" button

// TODO marker persistence
//    - startup: load markers from last time
//    - keep markers near user location always
//    - favorite markers that get permanently stored

// TODO user profiles
//    - allow login
//    - allow adding a node
//    - allow editing a node
//    - allow reporting a problem with a node

// TODO meta
//    - make img/ and docs/ dir


// TODO wontfix
//    - rarely used marker types: groups, actions, cider, saplings
//    - draw in sorted order and/or with z-score so front markers are in front -> rarely needed

/*
Kleine Sachen über die ich noch nachgedacht habe:
- Eventuell direkt auf der Karte die Marker dunkler machen die nicht saisonal sind
- Direkter Download von Marker-Infos beim ersten Berühren. Da ist die Frage wie sehr es das Backend belastet

Größere Features:
- Marker als "Favoriten" makieren. Die werden dann am besten permanent auf dem Gerät gespeichert. Ich dachte zuerst an eine Liste aber ein Filter mit grauem Icon wie die anderen beiden wäre sogar intuitiver denke ich.
- Marker-History. Die am besten automatisch bemerkt an welchen Markern man vorbei gelaufen ist. Daraus kann man dann Gamification machen und schauen ob man alle Sorten besuchen kann. Da braucht man auch ein Feature um fälschlich besuchte Marker wieder zu entfernen.

Error message
*/
