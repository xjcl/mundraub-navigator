package xjcl.mundraub.utils

import android.app.Activity
import com.google.android.gms.maps.model.LatLng
import xjcl.mundraub.data.markerContext
import xjcl.mundraub.data.markers
import xjcl.mundraub.data.markersData

fun invalidateMarker(activity: Activity, nid: String) {
    markerContext.execute {
        for (mark in markers.toMap()) {  // copy constructor
            if (markersData[mark.key]?.nid.toString() == nid) {
                activity.runOnUiThread { mark.value.remove() }
                markers.remove(mark.key)
                markersData.remove(mark.key)
            }
        }
    }
}

fun vecMul(scalar : Double, vec : LatLng) : LatLng = LatLng(scalar * vec.latitude, scalar * vec.longitude)
fun vecAdd(vec1 : LatLng, vec2 : LatLng) : LatLng = LatLng(vec1.latitude + vec2.latitude, vec1.longitude + vec2.longitude)
fun vecSub(vec1 : LatLng, vec2 : LatLng) : LatLng = LatLng(vec1.latitude - vec2.latitude, vec1.longitude - vec2.longitude)
