package xjcl.mundraub.data

import android.graphics.Bitmap
import com.google.android.gms.maps.model.BitmapDescriptor
import kotlinx.serialization.Serializable

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
