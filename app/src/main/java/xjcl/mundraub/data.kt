package xjcl.mundraub

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.RelativeLayout
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.squareup.picasso.Picasso
import kotlinx.serialization.Serializable
import java.util.*
import kotlin.collections.HashMap

@Serializable
data class Properties(val nid: Int, val tid: Int)  // node id, tree type id
@Serializable
data class Feature(val pos: List<Double>, val properties: Properties? = null, val count: Int? = null)
@Serializable
data class Root(val features: List<Feature>)

data class MarkerData(val type : String, val title : String, val monthCodes : String, val curMonth : Double,
                      val isSeasonal : Boolean, val fruitColor : Int, val nid : Int?, var description : String?,
                      var uploader : String?, var uploadDate : String?, var image : Bitmap?)

fun getCurMonth(): Double = Calendar.getInstance().get(Calendar.MONTH) + 1 + Calendar.getInstance().get(
    Calendar.DAY_OF_MONTH).toDouble() / 32
fun isSeasonal(tid : Int?, month : Double) = treeIdToSeason[tid]?.first ?: 0.0 <= month && month <= treeIdToSeason[tid]?.second ?: 0.0
fun Any?.discard() = Unit

lateinit var mMap: GoogleMap
lateinit var fusedLocationClient: FusedLocationProviderClient

lateinit var relView : RelativeLayout
lateinit var mapView : View
lateinit var fab : FloatingActionButton

var markers = HashMap<LatLng, Marker>()
var markersData = HashMap<LatLng, MarkerData>()
var selectedSpeciesStr : String = "4,5,6,7,8,9,10,11,12,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37"
var fabAnimationFromTo : Pair<Float, Float> = 0F to 0F
val origY = HashMap<View, Float>()
var totalLeftPadding = 0

// This stupidly needs to be stored with a strong reference because it otherwise gets garbage-collected
// https://stackoverflow.com/a/24602348/2111778
class JanTarget : com.squareup.picasso.Target {
    var marker: Marker? = null
    var md: MarkerData? = null
    override fun onPrepareLoad(placeHolderDrawable: Drawable?) { }
    override fun onBitmapFailed(errorDrawable: Drawable?) { }
    override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
        md?.image = bitmap
        marker?.showInfoWindow()
    }
}
val picassoBitmapTarget = JanTarget()
