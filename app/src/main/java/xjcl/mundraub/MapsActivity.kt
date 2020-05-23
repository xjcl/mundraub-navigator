package xjcl.mundraub

import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnCameraIdleListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URL


@Serializable
data class Properties(val nid: Int, val tid: Int)

@Serializable
data class Feature(val pos: List<Double>, val properties: Properties? = null, val count: Int? = null)

@Serializable
data class Root(val features: List<Feature>)


class AsyncFruitGetRequest(map : GoogleMap, url : String) : AsyncTask<Void, Void, String>() {
    val mMap = map;
    val mURL = url;

    override fun doInBackground(vararg params: Void?): String {
        return URL(mURL).readText()
    }

    fun addLocationMarkers(testStr: String) {
        if (testStr == "null") return
        // API sometimes returns String instead of double/int for no reason... -> convert
        val testStr2 = Regex(""""-?[0-9]+.?[0-9]+"""").replace(testStr, { it.value.substring(1, it.value.length - 1) })
        val root = Json.parse(Root.serializer(), testStr2)
        Log.e("testo", root.toString())

        mMap.clear()
        for (feature in root.features) {
            val fruit = LatLng(feature.pos[0], feature.pos[1])
            mMap.addMarker(MarkerOptions().position(fruit).title("Marker in Fruit"))
        }
    }

    override fun onPostExecute(testStr: String) {
        addLocationMarkers(testStr)
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
             "&zoom=${zoom.toInt() + 1}&cat=4,5,6,7,8,9,10,11,12,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37"

        Log.e("testo", "GET $url")
        Log.e("testo", "zoom $zoom")
        Log.e("testo", "bbox_lo $bbox_lo")
        Log.e("testo", "bbox_hi $bbox_hi")

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

// TODO:
//    - bounding box around
//    - fruit types
//    - move camera to most recent location
//    - cache markers
