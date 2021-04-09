package xjcl.mundraub.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.squareup.picasso.Picasso
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import xjcl.mundraub.R
import xjcl.mundraub.data.*
import java.net.URL
import kotlin.concurrent.thread

class MarkerDataManager {
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

    // --- Place a single marker on the GoogleMap, and prepare its info window, using parsed JSON class ---
    private fun addMarkerFromFeatures(activity: Activity, features: List<Feature>) {
        for (featureChunk in features.chunked(12)) {
            val new_markers = mutableListOf<Triple<LatLng, Marker, MarkerData>>()
            activity.runOnUiThread {
                for (feature in featureChunk) {
                    val latlng = LatLng(feature.pos[0], feature.pos[1])
                    if (markers.contains(latlng)) continue
                    val md = featureToMarkerData(activity, feature)
                    val mo = MarkerOptions().position(latlng).title(md.title).icon(md.icon).anchor(.5F, if (md.type == "cluster") .5F else 1F)
                    val mark = mMap?.addMarker(mo)
                    if (mark != null)
                        new_markers.add(Triple(latlng, mark, md))
                }

                markerContext.execute {
                    new_markers.forEach {
                        val (latlng, mark, md) = it
                        markers[latlng] = mark
                        markersData[latlng] = md
                    }
                }
            }
        }
    }

    // --- Place a list of markers on the GoogleMap ("var markers"), using raw JSON String ---
    // Note that the HashMap 'markers' is only modified in the markerContext to avoid concurrency issues
    private fun addLocationMarkers(activity: Activity, jsonStrPre: String) {
        markerContext.execute {
            Log.e("addLocationMarkers", "ENTER " + markers.size.toString() + " " + jsonStrPre)
            val jsonStr = if (jsonStrPre == "null") "{\"features\":[]}" else jsonStrPre

            // --- parse newly downloaded markers ---
            // API inconsistently either returns String or double/int... -> strip away double quotes
            val jsonStrClean = Regex(""""-?[0-9]+.?[0-9]+"""").replace(jsonStr) { it.value.substring(1, it.value.length - 1) }
            val root = Json.decodeFromString<Root>(jsonStrClean)

            // --- remove old markers not in newly downloaded set (also removes OOB markers) ---
            val featuresSet = root.features.map { LatLng(it.pos[0], it.pos[1]) }.toSet()
            for (markChunk in markers.toMap().entries.chunked(12)) {  // copy constructor
                for (mark in markChunk) {
                    if (featuresSet.contains(mark.key)) continue
                    markers.remove(mark.key)
                    markersData.remove(mark.key)
                }

                activity.runOnUiThread {
                    for (mark in markChunk) {
                        if (featuresSet.contains(mark.key)) continue
                        mark.value.remove()
                    }
                }
            }

            // --- add newly downloaded markers not already in old set ---
            addMarkerFromFeatures(activity, root.features)
            Log.e("addLocationMarkers", "EXIT " + markers.size.toString())
        }
    }

    // --- Update markers when user finished moving the map ---
    fun updateMarkers(activity: Activity, callback : () -> Unit = {}) {
        val mmMap = mMap ?: return
        val zoom = mmMap.cameraPosition.zoom
        Log.e("updateMarkers", "zoom $zoom")
        if (zoom == 2F || zoom == 3F) return  // Bugfix, do not remove, see commit message
        val bboxLo = mmMap.projection.visibleRegion.latLngBounds.southwest
        val bboxHi = mmMap.projection.visibleRegion.latLngBounds.northeast

        // API documented here: https://github.com/niccokunzmann/mundraub-android/blob/master/docs/api.md
        val url = "https://mundraub.org/cluster/plant?bbox=${bboxLo.longitude},${bboxLo.latitude},${bboxHi.longitude},${bboxHi.latitude}" +
                "&zoom=${(zoom + .25F).toInt()}&cat=$selectedSpeciesStr"

        Log.e("updateMarkers", "GET $url")

        thread {
            val jsonStr = try {
                URL(url).readText()
            } catch (ex: Exception) {
                activity.runOnUiThread { Toast.makeText(activity, activity.getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show() }
                return@thread
            }
            addLocationMarkers(activity, jsonStr)
            callback()
        }
    }

    // --- Download detailed node description and stick it into marker info window ---
    fun downloadMarkerData(activity : Activity, marker : Marker) {
        val md = markersData[marker.position] ?: return
        if (md.description != null || md.nid == null) return

        thread {
            // --- Download number of finds and description ---
            val htmlStr = try { URL("https://mundraub.org/node/${md.nid}").readText() } catch (ex : Exception) {
                activity.runOnUiThread { Toast.makeText(activity, activity.getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show() }
                return@thread
            }

            fun extractUnescaped(htmlStr : String, after : String, before : String) : String {
                val extractEscaped = htmlStr.substringAfter(after).substringBefore(before, "(no data)")
                return unescapeHtml(extractEscaped)
            }

            val number = extractUnescaped(htmlStr, "Anzahl: <span class=\"tag\">", "</span>")
            val description = extractUnescaped(htmlStr, "<p>", "</p>")
            md.uploader = extractUnescaped(htmlStr.substringAfter("typeof=\"schema:Person\""), ">", "</span>")
            md.uploadDate = extractUnescaped(htmlStr.substringAfter("am <span>"), ", ", " - ")
            md.description = "[$number] $description"

            // --- Download image in lowest quality ---
            val imageURL = htmlStr.substringAfter("srcset=\"", "").substringBefore(" ")
            if (imageURL.isBlank() || md.image != null)
                return@thread activity.runOnUiThread { marker.showInfoWindow() }

            activity.runOnUiThread {
                Log.e("onMarkerClickListener", "Started Picasso on UI thread now ($imageURL)")
                picassoBitmapTarget.md = md
                picassoBitmapTarget.marker = marker
                Picasso.with(activity).load("https://mundraub.org/$imageURL").placeholder(R.drawable.progress_animation).into(
                    picassoBitmapTarget
                )
            }
        }
    }

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
}
