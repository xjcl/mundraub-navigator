package xjcl.mundraub

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.text.HtmlCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.InfoWindowAdapter
import com.google.android.gms.maps.GoogleMap.OnCameraIdleListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet


@Serializable
data class Properties(val nid: Int, val tid: Int)  // node id, tree type id

@Serializable
data class Feature(val pos: List<Double>, val properties: Properties? = null, val count: Int? = null)

@Serializable
data class Root(val features: List<Feature>)

// Key: treeId (type of tree/fruit),  Value: Pair<Int, Int> with first and last month of season
// *** The following code represents January-start as 1, mid-January as 1.5, February-start as 2, and so on
val treeIdToSeason = hashMapOf(
    // https://www.hagebau.de/beratung-obst-ernten/
    // https://www.regional-saisonal.de/saisonkalender-obst
    // https://mundraub.org/sites/default/files/inline-files/Mundraub_Erntekalender.pdf  <-- main source
     4 to ( 8.0 to 11.0),
     5 to ( 8.0 to 11.5),
     6 to ( 6.0 to  9.5),
     7 to ( 7.0 to  9.5),
     8 to ( 7.0 to 10.5),
     9 to ( 8.0 to 11.5),
    10 to ( 7.0 to  9.0),
    11 to ( 6.5 to  8.5),
    12 to ( 0.0 to  0.0),

    14 to ( 8.5 to 11.5),
    15 to ( 9.0 to 11.5),
    16 to ( 9.5 to 11.0),
    17 to ( 0.0 to  0.0),

    18 to ( 7.5 to 11.0), // changed this cos i'm 100% certain they grow around my bday
    19 to ( 0.0 to  0.0), // wild strawberry
    20 to ( 6.5 to 10.0),
    21 to ( 5.5 to 10.0), // note that berries and blossoms are both edible but have different seasons (5.5-10 vs 9-11)
    22 to ( 6.5 to  9.0),
    23 to ( 6.0 to  9.0),
    24 to ( 8.5 to 10.5),
    25 to ( 7.0 to  8.0), // https://www.plantura.garden/gartentipps/zierpflanzen/felsenbirne-pflanzen-und-pflegen
    26 to ( 8.0 to 11.0),
    27 to ( 9.0 to 12.0),
    28 to ( 9.0 to 13.0),
    29 to ( 9.0 to 11.0), // https://www.kneipp.com/de_de/kneipp-magazin/sebastian-kneipp/lexikon-pflanzen-inhaltsstoffe/pflanzenlexikon/weissdorn/
    30 to ( 0.0 to  0.0),

    // https://www.miss.at/pflanzkalender-2018-wann-man-welches-gemuese-pflanzen-kann/?cn-reloaded=1
    31 to ( 3.5 to  6.0),
    32 to ( 8.0 to 11.0), // https://www.pflanzen-vielfalt.net/b%C3%A4ume-str%C3%A4ucher-a-z/wacholder-gemeiner/
    33 to ( 7.0 to 10.0), // https://praxistipps.focus.de/minze-ernten-der-beste-zeitpunkt_107154
    34 to ( 5.0 to 11.0),
    35 to ( 3.0 to  7.0),
    36 to ( 6.0 to  9.0),
    37 to ( 0.0 to  0.0)
)

val treeIdToMarkerIcon = hashMapOf(
     4 to R.drawable.apple,
     5 to R.drawable.pear,
     6 to R.drawable.cherry,
     7 to R.drawable.mirabelle,
     8 to R.drawable.plum,
     9 to R.drawable.quince,
    10 to R.drawable.apricot,
    11 to R.drawable.mulberry,
    12 to R.drawable.otherfruit,

    14 to R.drawable.hazelnut,
    15 to R.drawable.walnut,
    16 to R.drawable.chestnut,
    17 to R.drawable.othernut,

    18 to R.drawable.blackberry,
    19 to R.drawable.otherfruitshrub,  // wild strawberry
    20 to R.drawable.blueberry,
    21 to R.drawable.elderberry,
    22 to R.drawable.raspberry,
    23 to R.drawable.currant,
    24 to R.drawable.cornelcherry,
    25 to R.drawable.shadbush,
    26 to R.drawable.seabuckthorn,
    27 to R.drawable.rosehip,
    28 to R.drawable.blackthorn,
    29 to R.drawable.hawthorn,
    30 to R.drawable.otherfruitshrub,

    31 to R.drawable.ramson,
    32 to R.drawable.juniper,
    33 to R.drawable.mint,
    34 to R.drawable.rosemary,
    35 to R.drawable.woodruff,
    36 to R.drawable.thyme,
    37 to R.drawable.otherherb
)

var markers = HashMap<LatLng, Marker>()

// Helper function as adding text to a bitmap needs more code than one might expect
fun bitmapWithText(resource: Int, activity: Activity, text: String, textSize: Float, outline: Boolean = true, xpos: Float = .5F, color: Int = Color.WHITE) : Bitmap {
    val options = BitmapFactory.Options()
    options.inMutable = true

    val bitmap = BitmapFactory.decodeResource(activity.resources, resource, options)
    val canvas = Canvas(bitmap)
    val textBounds = Rect()

    val paint = Paint()
    paint.color = Color.DKGRAY
    paint.textSize = textSize
    paint.getTextBounds(text, 0, text.length, textBounds)

    if (outline)
        for (delta in listOf(3 to 0, -3 to 0, 0 to 3, 0 to -3, 2 to 2, 2 to -2, -2 to 2, -2 to -2))
            canvas.drawText(text, canvas.width * xpos - textBounds.exactCenterX() + delta.first,
                canvas.height/2F - textBounds.exactCenterY() + delta.second, paint)

    paint.color = color
    canvas.drawText(text, canvas.width * xpos - textBounds.exactCenterX(), canvas.height/2F - textBounds.exactCenterY(), paint)

    return bitmap
}


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, OnCameraIdleListener, ActivityCompat.OnRequestPermissionsResultCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // --- Place a single marker on the GoogleMap, and prepare its info window, using parsed JSON class ---
    private fun addMarkerFromFeature(feature: Feature): Marker {
        val latlng = LatLng(feature.pos[0], feature.pos[1])
        val tid = feature.properties?.tid
        val type = when {
            feature.properties == null -> "cluster"
            treeIdToSeason[tid]?.first == 0.0 -> "other"
            else -> "normal"
        }

        val title = getString(resources.getIdentifier("tid$tid", "string", packageName))
        val fruitColor = BitmapFactory.decodeResource(resources, treeIdToMarkerIcon[tid] ?: R.drawable.otherfruit).getPixel(10, 30)

        val icon =
            if (type == "cluster") // isCluster
                BitmapDescriptorFactory.fromBitmap( bitmapWithText(R.drawable._cluster, this, feature.count.toString(), 45F) )
            else // isTree
                BitmapDescriptorFactory.fromResource(treeIdToMarkerIcon[tid] ?: R.drawable.otherfruit)

        // *** The following code represents January-start as 1, mid-January as 1.5, February-start as 2, and so on
        val curMonth = Calendar.getInstance().get(Calendar.MONTH) + 1 + Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toDouble() / 32
        val isSeasonal = {month : Double -> treeIdToSeason[tid]?.first ?: 0.0 <= month && month <= treeIdToSeason[tid]?.second ?: 0.0}
        val months = (1..12).joinToString("") { when {
             isSeasonal(it + .25) &&  isSeasonal(it + .75) -> "x"
             isSeasonal(it + .25) && !isSeasonal(it + .75) -> "l"
            !isSeasonal(it + .25) &&  isSeasonal(it + .75) -> "r"
            else -> "_"
        }}

        val snippet = type + "\n" + months + "\n" + curMonth + "\n" + isSeasonal(curMonth) + "\n" + fruitColor + "\n" + feature.properties?.nid

        return mMap.addMarker(MarkerOptions().position(latlng).title(title).snippet(snippet).icon(icon)
            .anchor(.5F, if (type == "cluster") .5F else 1F))
    }

    // --- Place a list of markers on the GoogleMap ("var markers"), using raw JSON String ---
    private fun addLocationMarkers(jsonStr: String) {
        Log.e("addLocationMarkers", markers.size.toString() + " " + jsonStr)
        if (jsonStr == "null" || jsonStr == "") return

        // --- parse newly downloaded markers ---
        // API inconsistently either returns String or double/int... -> strip away double quotes
        val jsonStrClean = Regex(""""-?[0-9]+.?[0-9]+"""").replace(jsonStr) { it.value.substring(1, it.value.length - 1) }
        val root = Json.parse(Root.serializer(), jsonStrClean)

        // --- remove old markers not in newly downloaded set (also removes OOB markers) ---
        val featuresSet = HashSet<LatLng>( root.features.map { LatLng(it.pos[0], it.pos[1]) } )
        for (mark in markers.toMap()) {  // copy constructor
            if (!featuresSet.contains(mark.key)) { mark.value.remove(); markers.remove(mark.key) }
        }

        // --- add newly downloaded markers not already in old set ---
        for (feature in root.features) {
            val latlng = LatLng(feature.pos[0], feature.pos[1])
            if (markers.contains(latlng)) continue
            markers[latlng] = addMarkerFromFeature(feature)
        }
    }

    // --- Update markers when user finished moving the map ---
    private fun updateMarkers() {
        val zoom = mMap.cameraPosition.zoom
        val bboxLo = mMap.projection.visibleRegion.nearLeft
        val bboxHi = mMap.projection.visibleRegion.farRight

        // API documented here: https://github.com/niccokunzmann/mundraub-android/blob/master/docs/api.md
        val url = "https://mundraub.org/cluster/plant?bbox=${bboxLo.longitude},${bboxLo.latitude},${bboxHi.longitude},${bboxHi.latitude}" +
             "&zoom=${(zoom + .5F).toInt()}&cat=4,5,6,7,8,9,10,11,12,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37"

        Log.e("updateMarkers", "GET $url")

        GlobalScope.launch {
            val jsonStr = try { URL(url).readText() } catch (ex: Exception) { "null" }
            runOnUiThread { addLocationMarkers(jsonStr) }
        }
    }

    override fun onCameraIdle() = updateMarkers()

    // --- On startup: Prepare map and cause onRequestPermissionsResult to be called ---
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setOnCameraIdleListener(this)

        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        ActivityCompat.requestPermissions(this, permissions,0)

        // --- Build a vertical layout to provide an info window for a marker ---
        // https://stackoverflow.com/a/31629308/2111778
        mMap.setInfoWindowAdapter(object : InfoWindowAdapter {
            override fun getInfoWindow(arg0: Marker): View? = null

            override fun getInfoContents(marker: Marker): View {
                // I'm sending display data encoded in the snippet string as I found no clean way to do this
                val markerData = marker.snippet.split("\n")
                val markerType = markerData[0]
                val monthCodes = markerData[1]; val curMonth = markerData[2].toFloat()
                val isSeasonal = markerData[3].toBoolean(); val fruitColor = markerData[4].toInt()
                val descriptionStr = if (markerData.size > 6) markerData.slice(6 until markerData.size).joinToString("\n") else ""

                val info = LinearLayout(this@MapsActivity)
                info.orientation = LinearLayout.VERTICAL

                val description = TextView(this@MapsActivity)
                // 12 month circles of 13 pixels width -- ugly but WRAP_CONTENT just would not work =(
                val density = resources.displayMetrics.density
                var masterWidth = (12 * 13 * density).toInt()
                description.width = masterWidth
                description.text = descriptionStr
                description.textSize = 12F
                if (descriptionStr.isNotBlank()) info.addView(description)

                val title = TextView(this@MapsActivity)
                title.setTextColor(fruitColor)
                title.gravity = Gravity.CENTER
                title.setTypeface(null, Typeface.BOLD)
                title.text = marker.title
                info.addView(title)

                // no month/season information in this case so return early
                if (markerType == "cluster" || markerType == "other")
                    return info

                val seasonText = TextView(this@MapsActivity)
                seasonText.setTextColor(Color.BLACK)
                seasonText.text = this@MapsActivity.getString(if (isSeasonal) R.string.inSeason else R.string.notInSeason)
                info.addView(seasonText)

                val months = LinearLayout(this@MapsActivity)
                months.orientation = LinearLayout.HORIZONTAL
                for (i in 1..12) {
                    val circle = LinearLayout(this@MapsActivity)
                    circle.orientation = LinearLayout.HORIZONTAL

                    val circleLeft = ImageView(this@MapsActivity)
                    val circleRight = ImageView(this@MapsActivity)

                    val resLeft = if ("xl".contains(monthCodes[i-1])) R.drawable._dot_l1 else R.drawable._dot_l0
                    val resRight = if ("xr".contains(monthCodes[i-1])) R.drawable._dot_r1 else R.drawable._dot_r0
                    circleLeft.setImageResource(resLeft)
                    circleRight.setImageResource(resRight)
                    if ("xl".contains(monthCodes[i-1])) circleLeft.setColorFilter(fruitColor)
                    if ("xr".contains(monthCodes[i-1])) circleRight.setColorFilter(fruitColor)
                    // add vertical line for current time in year
                    if (curMonth.toInt() == i)
                        (if (curMonth % 1 < .5) circleLeft else circleRight).setImageBitmap(
                            bitmapWithText( (if (curMonth % 1 < .5) resLeft else resRight), this@MapsActivity,
                                "|", 50F, false,  2 * (curMonth % .5F), if (isSeasonal) fruitColor else Color.GRAY) )

                    circle.addView(circleLeft)
                    circle.addView(circleRight)

                    val letter = TextView(this@MapsActivity)
                    letter.setTextColor(if (monthCodes[i-1] != '_') fruitColor else Color.GRAY)
                    letter.gravity = Gravity.CENTER
                    if (monthCodes[i-1] != '_') letter.setTypeface(null, Typeface.BOLD)
                    letter.text = "JFMAMJJASOND"[i-1].toString()

                    val month = LinearLayout(this@MapsActivity)
                    month.orientation = LinearLayout.VERTICAL
                    month.addView(circle)
                    month.addView(letter)
                    months.addView(month)
                }
                months.measure(0, 0)
                Log.e("width change", masterWidth.toString() + " -> " + months.measuredWidth)
                masterWidth = months.measuredWidth
                description.width = masterWidth

                val day = RelativeLayout(this@MapsActivity)
                val tv = TextView(this@MapsActivity)
                tv.text = Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString()
                tv.setTextColor(if (isSeasonal) fruitColor else Color.GRAY)
                tv.setTypeface(null, Typeface.BOLD)
                tv.measure(0, 0)
                val params = RelativeLayout.LayoutParams(tv.measuredWidth, tv.measuredHeight)
                params.leftMargin = ( masterWidth / 12F * (curMonth - 1) - tv.measuredWidth / 2F ).toInt().coerceIn(0, masterWidth - tv.measuredWidth)
                day.addView(tv, params)

                info.addView(day)
                info.addView(months)
                return info
            }
        })

        // --- Download detailed node description when user taps ("clicks") on info window ---
        mMap.setOnInfoWindowClickListener { marker ->
            if (marker.snippet.count {it =='\n'} >= 6) return@setOnInfoWindowClickListener

            val nid = marker.snippet.split("\n")[5]
            if (nid == "null") return@setOnInfoWindowClickListener

            GlobalScope.launch {
                val htmlStr = try { URL("https://mundraub.org/node/$nid").readText() } catch (ex : Exception) { "null" }

                runOnUiThread {
                    if (htmlStr == "null") return@runOnUiThread
                    val number = htmlStr.substringAfter("Anzahl: <span class=\"tag\">")
                        .substringBefore("</span>", "?")
                    val description =
                        htmlStr.substringAfter("<p>").substringBefore("</p>", "(no data)")
                    val descriptionUnescaped =
                        HtmlCompat.fromHtml(description, HtmlCompat.FROM_HTML_MODE_LEGACY)
                            .toString()  // unescape "&quot;" etc
                    marker.snippet += "\n[$number] $descriptionUnescaped"
                    marker.showInfoWindow()
                }
            }
        }
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

    // --- On startup: Prepare classes ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        // retains markers if user rotates phone etc. (useful offline)  https://stackoverflow.com/a/22058966/2111778
        mapFragment.retainInstance = true
    }

    override fun onBackPressed() {
        moveTaskToBack(true)  // do not call onCreate after user accidentally hits Back (useful offline)
    }
}


// TODO other
//    * respond to nicco, and/or send screenshots

// TODO UI
//    * depending on font, current day can be too wide (use whole row or fixed width or ignore)

// TODO publishing
//    * fix UI issue
//    * prepare for publishing (key etc.)
//    * upload to Play Store for private beta
//    * write blog post about it
//    * publish to reddit about it


// TODO latlng boundaries
//    * incorrect or no update on non-north-oriented map
//    * bigger boundaries useful

// TODO tests
//    - test scripts for app or at least info extraction

// TODO bugs
//    - when tapping a marker, markers reload, so it sometimes disappears

// TODO pokemon
//    - detect when someone "visits" a marker
//    - list of which species have ever been visited (including link to most recent one)
//    - list of recently visited or starred markers

// TODO individual node data
//    - https://mundraub.org/node/75327
//    - images? impractical, rare, waste data

// TODO filter
//    - allow filtering by fruit type

// TODO stateful app
//    - onInternetConnection: download new markers
//    - startup: load markers from last time

// TODO persistence
//    - keep markers near person better
//    - favorite markers that get permanently stored


// TODO user profiles
//    - allow login
//    - allow adding a node
//    - allow editing a node


// TODO medium-term
//    - replace AsyncTask with coroutines
//        - AsyncTask will be removed in Android 11
//        - https://stackoverflow.com/a/21284021/2111778
//    - pass info in a better way than snippets

// TODO long-term / never
//    - groups, actions, cider makers, saplings, ...


// TODO not really needed
//    - bounding box might benefit from being bigger than the viewport
//    - draw in sorted order and/or with z-score so front markers are in front -> rarely needed
