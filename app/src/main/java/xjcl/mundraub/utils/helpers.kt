package xjcl.mundraub.utils

import com.google.android.gms.maps.model.LatLng

fun vecMul(scalar : Double, vec : LatLng) : LatLng = LatLng(scalar * vec.latitude, scalar * vec.longitude)
fun vecAdd(vec1 : LatLng, vec2 : LatLng) : LatLng = LatLng(vec1.latitude + vec2.latitude, vec1.longitude + vec2.longitude)
fun vecSub(vec1 : LatLng, vec2 : LatLng) : LatLng = LatLng(vec1.latitude - vec2.latitude, vec1.longitude - vec2.longitude)
