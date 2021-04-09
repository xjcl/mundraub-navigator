package xjcl.mundraub.data

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import kotlinx.serialization.Serializable
import xjcl.mundraub.R
import xjcl.mundraub.utils.bitmapWithText
import xjcl.mundraub.utils.getCurMonth
import xjcl.mundraub.utils.getFruitColor
import xjcl.mundraub.utils.isSeasonal


@Serializable
data class Properties(val nid: Int, val tid: Int)  // node id, tree type id
@Serializable
data class Feature(val pos: List<Double>, val properties: Properties? = null, val count: Int? = null)
@Serializable
data class Root(val features: List<Feature>)

data class MarkerData(val tid : Int, val type : String, val title : String, val monthCodes : String, val curMonth : Double,
                      val isSeasonal : Boolean, val fruitColor : Int, val nid : Int?, var description : String?,
                      var uploader : String?, var uploadDate : String?, var image : Bitmap?, val icon : BitmapDescriptor,
                      var truncate : Boolean)


fun featureToMarkerData(context : Context, feature : Feature) : MarkerData {
    val tid = feature.properties?.tid ?: 12
    val type = if (feature.properties == null) "cluster" else "normal"

    val title = context.getString(context.resources.getIdentifier("tid$tid", "string", context.packageName))
    val fruitColor = getFruitColor(context.resources, tid)

    val icon =
        if (type == "cluster") // isCluster
            BitmapDescriptorFactory.fromBitmap(bitmapWithText(R.drawable._cluster_c, context, feature.count.toString(), 45F))
        else // isTree
            BitmapDescriptorFactory.fromResource(treeIdToMarkerIcon[tid] ?: R.drawable.icon_otherfruit)

    // *** The following code represents January-start as 1, mid-January as 1.5, February-start as 2, and so on
    val monthCodes = (1..12).joinToString("") {
        when {
             isSeasonal(tid, it + .25) &&  isSeasonal(tid, it + .75) -> "x"
             isSeasonal(tid, it + .25) && !isSeasonal(tid, it + .75) -> "l"
            !isSeasonal(tid, it + .25) &&  isSeasonal(tid, it + .75) -> "r"
            else -> "_"
        }
    }

    return MarkerData(tid, type, title, monthCodes, getCurMonth(), isSeasonal(tid, getCurMonth()), fruitColor,
        feature.properties?.nid, null, null, null, null, icon, true)
}
