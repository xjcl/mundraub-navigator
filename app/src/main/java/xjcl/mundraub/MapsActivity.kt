package xjcl.mundraub

import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnCameraIdleListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker
import com.google.android.gms.maps.model.BitmapDescriptorFactory.fromResource
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
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

    31 to R.drawable.ramsons,
    32 to R.drawable.juniper,
    33 to R.drawable.mint,
    34 to R.drawable.rosemary,
    35 to R.drawable.woodruff,
    36 to R.drawable.thyme,
    37 to R.drawable.otherherb,

    null to R.drawable.otherfruit
)

var markers = ArrayList<Marker>()


class AsyncFruitGetRequest(map : GoogleMap, url : String) : AsyncTask<Void, Void, String>() {
    val mMap = map;
    val mURL = url;

    override fun doInBackground(vararg params: Void?): String {
        return try {
            URL(mURL).readText()
        } catch (ex : Exception) {
            "null"
        }
    }

    fun addLocationMarkers(jsonStr: String) {
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
        var markersSet = HashSet<LatLng>( markers.map { it.position } )
        outer@
        for (feature in root.features) {
            val fruit = LatLng(feature.pos[0], feature.pos[1])

            if (markersSet.contains(fruit))
                continue

            val icon =
                if (feature.properties == null) // cluster
                    defaultMarker(70.0F)
                else // tree
                    fromResource(treeIdToName[feature.properties?.tid] ?: R.drawable.otherfruit)

            val mark = mMap.addMarker(
                MarkerOptions().position(fruit).title("Zoom To Expand").icon(icon)
            )
            markers.add(mark)
        }

        println(markers.size)
    }

    override fun onPostExecute(jsonStr: String) {
        addLocationMarkers(jsonStr)
    }
}


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, OnCameraIdleListener {

    private lateinit var mMap: GoogleMap

    override fun onCameraIdle() {
        // https://github.com/niccokunzmann/mundraub-android/blob/master/docs/api.md

        val zoom = mMap.cameraPosition.zoom
        val bbox_lo = mMap.projection.visibleRegion.nearLeft
        val bbox_hi = mMap.projection.visibleRegion.farRight

        val url = "https://mundraub.org/cluster/plant?bbox=${bbox_lo.longitude},${bbox_lo.latitude},${bbox_hi.longitude},${bbox_hi.latitude}" +
             "&zoom=${(zoom + .5).toInt()}&cat=4,5,6,7,8,9,10,11,12,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37"

        Log.e("testo", "GET $url")

        AsyncFruitGetRequest(mMap, url).execute()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setOnCameraIdleListener(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

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

// TODO reopen app
//    * start at most recent viewport
//    * save markers

// TODO UX
//    * draw user per GPS like Gmaps
//    - click on marker: display fruit name (how? second map? pair-in-pair type? pair-in-dataclass?)


// TODO medium-term
//    - click on marker: Marker callback with rich fruit info
//        - season, API (descr, picture) ...

// TODO long-term / never
//    - groups, actions, cider makers, saplings, ...
