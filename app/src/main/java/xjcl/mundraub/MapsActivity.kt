package xjcl.mundraub

import android.Manifest
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnCameraIdleListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker
import com.google.android.gms.maps.model.BitmapDescriptorFactory.fromResource
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.Task
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URL


@Serializable
data class Properties(val nid: Int, val tid: Int)  // node id, tree type id

@Serializable
data class Feature(val pos: List<Double>, val properties: Properties? = null, val count: Int? = null)

@Serializable
data class Root(val features: List<Feature>)

val treeIdToName = hashMapOf(
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

var markers = ArrayList<Marker>()


class AsyncFruitGetRequest(activity: MapsActivity, map : GoogleMap, url : String) : AsyncTask<Void, Void, String>() {
    val mActivity = activity
    val mMap = map
    val mURL = url

    override fun doInBackground(vararg params: Void?): String =
        try { URL(mURL).readText() } catch (ex : Exception) { "null" }

    private fun addLocationMarkers(jsonStr: String) {
        if (jsonStr == "null" || jsonStr == "") return

        // --- parse newly downloaded markers ---
        Log.e("testo", jsonStr)
        // API sometimes returns String instead of double/int for no reason... -> convert
        val jsonStrClean = Regex(""""-?[0-9]+.?[0-9]+"""").replace(jsonStr, { it.value.substring(1, it.value.length - 1) })
        val root = Json.parse(Root.serializer(), jsonStrClean)
        Log.e("testo", root.toString())

        // --- remove old markers not in newly downloaded set (also removes OOB markers) ---
        val markersNew = ArrayList<Marker>()
        val featuresSet = HashSet<LatLng>( root.features.map { LatLng(it.pos[0], it.pos[1]) } )
        for (mark in markers) {
            if (featuresSet.contains(mark.position)) markersNew.add(mark) else mark.remove()
        }
        markers = markersNew

        // --- add newly downloaded markers not already in old set ---
        val markersSet = HashSet<LatLng>( markers.map { it.position } )

        for (feature in root.features) {
            val latlng = LatLng(feature.pos[0], feature.pos[1])
            val tid = feature.properties?.tid

            if (markersSet.contains(latlng))
                continue

            val icon =
                if (feature.properties == null) // cluster
                    defaultMarker(70.0F)
                else // tree
                    fromResource(treeIdToName[tid] ?: R.drawable.otherfruit)

            val resId = mActivity.resources.getIdentifier("tid$tid", "string", mActivity.packageName)
            val title = mActivity.getString(resId)

            val mark = mMap.addMarker(
                MarkerOptions().position(latlng).title(title).icon(icon)
            )
            markers.add(mark)
        }

        println(markers.size)
    }

    override fun onPostExecute(jsonStr: String) = addLocationMarkers(jsonStr)
}


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, OnCameraIdleListener, ActivityCompat.OnRequestPermissionsResultCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // --- update markers when user finished moving map ---
    override fun onCameraIdle() {
        val zoom = mMap.cameraPosition.zoom
        val bbox_lo = mMap.projection.visibleRegion.nearLeft
        val bbox_hi = mMap.projection.visibleRegion.farRight

        // https://github.com/niccokunzmann/mundraub-android/blob/master/docs/api.md
        val url = "https://mundraub.org/cluster/plant?bbox=${bbox_lo.longitude},${bbox_lo.latitude},${bbox_hi.longitude},${bbox_hi.latitude}" +
             "&zoom=${(zoom + .5).toInt()}&cat=4,5,6,7,8,9,10,11,12,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37"

        Log.e("testo", "GET $url")

        AsyncFruitGetRequest(this, mMap, url).execute()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>, grantResults: IntArray
    ) {
        if (grantResults.isEmpty() || grantResults[0] == PackageManager.PERMISSION_DENIED) return

        mMap.isMyLocationEnabled = true  // show blue circle on map

        fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
            if (location == null) return@addOnSuccessListener
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 13F))
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setOnCameraIdleListener(this)
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(51.17, 10.45), 6F))  // Germany

        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        ActivityCompat.requestPermissions(this, permissions,0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }
}


// TODO marker assets
//    * make marker assets for clusters (1..8, 9+)?
//    - use hi-dpi / hi-res markers

// TODO algorithm
//    - bounding box could be bigger than viewport
//    - draw from top to bottom so correct marker selected? i.e. sort by y

// TODO stateful app
//    * startup: save markers from last time
//    * onRotate: loses markers if no internet
//          https://developers.google.com/maps/documentation/android-sdk/current-place-tutorial

// TODO publishing
//    - remove google_maps_key from git history and add note
//    - ask for permission for using the name Mundraub and the assets


// TODO medium-term
//    - click on marker: info window with rich fruit info
//        - season, API (descr, picture) ...
//        - Marker callback ? should not be needed

// TODO long-term / never
//    - groups, actions, cider makers, saplings, ...
