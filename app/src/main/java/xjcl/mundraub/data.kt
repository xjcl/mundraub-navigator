package xjcl.mundraub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.RelativeLayout
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.squareup.picasso.Picasso
import kotlinx.serialization.Serializable
import java.util.*
import java.util.concurrent.Executors

@Serializable
data class Properties(val nid: Int, val tid: Int)  // node id, tree type id
@Serializable
data class Feature(val pos: List<Double>, val properties: Properties? = null, val count: Int? = null)
@Serializable
data class Root(val features: List<Feature>)

data class MarkerData(val type : String, val title : String, val monthCodes : String, val curMonth : Double,
                      val isSeasonal : Boolean, val fruitColor : Int, val nid : Int?, var description : String?,
                      var uploader : String?, var uploadDate : String?, var image : Bitmap?, val icon : BitmapDescriptor)

fun getCurMonth(): Double = Calendar.getInstance().get(Calendar.MONTH) + 1 + Calendar.getInstance().get(
    Calendar.DAY_OF_MONTH).toDouble() / 32
fun isSeasonal(tid : Int?, month : Double) = treeIdToSeason[tid]?.first ?: 0.0 <= month && month <= treeIdToSeason[tid]?.second ?: 0.0
fun Any?.discard() = Unit

var mMap: GoogleMap? = null
lateinit var fusedLocationClient: FusedLocationProviderClient
var mapTypeChanged = false

lateinit var relView : RelativeLayout
lateinit var mapView : View
lateinit var fab : FloatingActionButton

var markers = mutableMapOf<LatLng, Marker>()
var markersData = mutableMapOf<LatLng, MarkerData>()
val markerContext = Executors.newSingleThreadExecutor()

val polylinesOnScreen = mutableListOf<Polyline>()
var polylinesLatLng : LatLng? = null

var selectedSpeciesStr : String = "1,4,5,6,7,8,9,10,11,12,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37"
var fabAnimationFromTo : Pair<Float, Float> = 0F to 0F
val origY = mutableMapOf<View, Float>()
var totalLeftPadding = 0

// This stupidly needs to be stored with a strong reference because it otherwise gets garbage-collected
// https://stackoverflow.com/a/24602348/2111778
class PicassoBitmapTarget : com.squareup.picasso.Target {
    var marker: Marker? = null
    var md: MarkerData? = null
    override fun onPrepareLoad(placeHolderDrawable: Drawable?) { }
    override fun onBitmapFailed(errorDrawable: Drawable?) { }
    override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
        md?.image = bitmap
        marker?.showInfoWindow()
    }
}
val picassoBitmapTarget = PicassoBitmapTarget()

// ----

fun featureToMarkerData(context : Context, feature : Feature) : MarkerData {
    val tid = feature.properties?.tid
    val type = if (feature.properties == null) "cluster" else "normal"

    val title = context.getString(context.resources.getIdentifier("tid$tid", "string", context.packageName))
    val fruitColor = getFruitColor(context.resources, tid)

    val icon =
        if (type == "cluster") // isCluster
            BitmapDescriptorFactory.fromBitmap(bitmapWithText(R.drawable._cluster_c, context, feature.count.toString(), 45F))
        else // isTree
            BitmapDescriptorFactory.fromResource(treeIdToMarkerIcon[tid] ?: R.drawable.otherfruit)

    // *** The following code represents January-start as 1, mid-January as 1.5, February-start as 2, and so on
    val monthCodes = (1..12).joinToString("") {
        when {
             isSeasonal(tid, it + .25) &&  isSeasonal(tid, it + .75) -> "x"
             isSeasonal(tid, it + .25) && !isSeasonal(tid, it + .75) -> "l"
            !isSeasonal(tid, it + .25) &&  isSeasonal(tid, it + .75) -> "r"
            else -> "_"
        }
    }

    return MarkerData(type, title, monthCodes, getCurMonth(), isSeasonal(tid, getCurMonth()), fruitColor,
        feature.properties?.nid, null, null, null, null, icon)
}
