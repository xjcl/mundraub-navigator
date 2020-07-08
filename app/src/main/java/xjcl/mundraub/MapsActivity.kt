package xjcl.mundraub

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.InfoWindowAdapter
import com.google.android.gms.maps.GoogleMap.OnCameraIdleListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.squareup.picasso.Picasso
import com.squareup.picasso.Picasso.LoadedFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URL
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.max


@Serializable
data class Properties(val nid: Int, val tid: Int)  // node id, tree type id
@Serializable
data class Feature(val pos: List<Double>, val properties: Properties? = null, val count: Int? = null)
@Serializable
data class Root(val features: List<Feature>)

data class MarkerData(val type : String, val title : String, val monthCodes : String, val curMonth : Double,
                      val isSeasonal : Boolean, val fruitColor : Int, val nid : Int?, var description : String?,
                      var image : Bitmap?)

// Key: treeId (type of tree/fruit),  Value: Pair<Int, Int> with first and last month of season
// *** The following code represents January-start as 1, mid-January as 1.5, February-start as 2, and so on
val treeIdToSeason = hashMapOf(
    // https://www.hagebau.de/beratung-obst-ernten/
    // https://www.regional-saisonal.de/saisonkalender-obst
    // https://mundraub.org/sites/default/files/inline-files/Mundraub_Erntekalender.pdf  <-- main source
     4 to ( 8.0 to 11.0),
     5 to ( 8.0 to 11.5),
     6 to ( 6.0 to  9.5),
     7 to ( 7.0 to  9.5),
     8 to ( 7.0 to 10.5),
     9 to ( 8.0 to 11.5),
    10 to ( 7.0 to  9.0),
    11 to ( 6.5 to  8.5),
    12 to ( 0.0 to  0.0),

    14 to ( 8.5 to 11.5),
    15 to ( 9.0 to 11.5),
    16 to ( 9.5 to 11.0),
    17 to ( 0.0 to  0.0),

    18 to ( 7.5 to 11.0), // changed this cos i'm 100% certain they grow around my bday
    19 to ( 6.0 to 10.0), // wild strawberry
    20 to ( 6.5 to 10.0),
    21 to ( 5.5 to 10.0), // note that berries and blossoms are both edible but have different seasons (5.5-10 vs 9-11)
    22 to ( 6.5 to  9.0),
    23 to ( 6.0 to  9.0),
    24 to ( 8.5 to 10.5),
    25 to ( 7.0 to  8.0), // https://www.plantura.garden/gartentipps/zierpflanzen/felsenbirne-pflanzen-und-pflegen
    26 to ( 8.0 to 11.0),
    27 to ( 9.0 to 12.0),
    28 to ( 9.0 to 13.0),
    29 to ( 9.0 to 11.0), // https://www.kneipp.com/de_de/kneipp-magazin/sebastian-kneipp/lexikon-pflanzen-inhaltsstoffe/pflanzenlexikon/weissdorn/
    30 to ( 0.0 to  0.0),

    // https://www.miss.at/pflanzkalender-2018-wann-man-welches-gemuese-pflanzen-kann/?cn-reloaded=1
    31 to ( 3.5 to  6.0),
    32 to ( 8.0 to 11.0), // https://www.pflanzen-vielfalt.net/b%C3%A4ume-str%C3%A4ucher-a-z/wacholder-gemeiner/
    33 to ( 7.0 to 10.0), // https://praxistipps.focus.de/minze-ernten-der-beste-zeitpunkt_107154
    34 to ( 5.0 to 11.0),
    35 to ( 3.0 to  7.0),
    36 to ( 6.0 to  9.0),
    37 to ( 0.0 to  0.0)
)
val getCurMonth = { Calendar.getInstance().get(Calendar.MONTH) + 1 + Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toDouble() / 32 }
val isSeasonal = {tid : Int?, month : Double -> treeIdToSeason[tid]?.first ?: 0.0 <= month && month <= treeIdToSeason[tid]?.second ?: 0.0}

val treeIdToMarkerIcon = hashMapOf(
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
    19 to R.drawable.wildstrawberry,
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
val treeIdToMarkerIconSorted = treeIdToMarkerIcon.toSortedMap()

lateinit var mMap: GoogleMap
lateinit var fusedLocationClient: FusedLocationProviderClient

lateinit var relView : RelativeLayout
lateinit var mapView : View
lateinit var fab : FloatingActionButton

var markers = HashMap<LatLng, Marker>()
var markersData = HashMap<LatLng, MarkerData>()
const val selectedSpeciesStrDefault : String = "4,5,6,7,8,9,10,11,12,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37"
var selectedSpeciesStr : String = selectedSpeciesStrDefault
var fabAnimationFromTo : Pair<Float, Float> = 0F to 0F
val origY = HashMap<ImageView, Float>()
var totalLeftPadding = 0

// This stupidly needs to be stored with a strong reference because it otherwise gets garbage-collected
// https://stackoverflow.com/a/24602348/2111778
class JanTarget : com.squareup.picasso.Target {
    var marker: Marker? = null
    var md: MarkerData? = null
    override fun onPrepareLoad(placeHolderDrawable: Drawable?) { }
    override fun onBitmapFailed(errorDrawable: Drawable?) { }
    override fun onBitmapLoaded(bitmap: Bitmap?, from: LoadedFrom?) {
        md!!.image = bitmap
        marker!!.showInfoWindow()
    }
}
val picassoBitmapTarget = JanTarget()

// Helper function as adding text to a bitmap needs more code than one might expect
fun bitmapWithText(resource: Int, activity: Activity, text: String, textSize: Float, outline: Boolean = true, xpos: Float = .5F, color: Int = Color.WHITE) : Bitmap {
    val options = BitmapFactory.Options()
    options.inMutable = true

    val bitmap = BitmapFactory.decodeResource(activity.resources, resource, options)
    val canvas = Canvas(bitmap)
    val textBounds = Rect()

    val paint = Paint()
    paint.textSize = textSize * activity.resources.displayMetrics.density / 3F
    paint.getTextBounds(text, 0, text.length, textBounds)
    paint.color = color

    // https://stackoverflow.com/a/9133305/2111778
    if (outline) {
        val stkPaint = Paint()
        stkPaint.style = Paint.Style.STROKE
        stkPaint.strokeWidth = 2F * activity.resources.displayMetrics.density
        stkPaint.textSize = paint.textSize
        stkPaint.color = Color.DKGRAY
        canvas.drawText(text, canvas.width * xpos - textBounds.exactCenterX(), canvas.height/2F - textBounds.exactCenterY(), stkPaint)
    }

    canvas.drawText(text, canvas.width * xpos - textBounds.exactCenterX(), canvas.height/2F - textBounds.exactCenterY(), paint)

    return bitmap
}

fun scaleToWidth(bitmapMaybeNull : Bitmap?, width : Int) : Bitmap {
    val bitmap = bitmapMaybeNull ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8)
    return Bitmap.createScaledBitmap(bitmap, width, (width.toDouble() / bitmap.width * bitmap.height).toInt(), true)
}

fun getFruitColor(resources : Resources, tid: Int?) : Int =
    BitmapFactory.decodeResource(resources, treeIdToMarkerIcon[tid] ?: R.drawable.otherfruit)
    .getPixel(resources.displayMetrics.density.toInt() * 3, resources.displayMetrics.density.toInt() * 10)

fun vecMul(scalar : Double, vec : LatLng) : LatLng = LatLng(scalar * vec.latitude, scalar * vec.longitude)
fun vecAdd(vec1 : LatLng, vec2 : LatLng) : LatLng = LatLng(vec1.latitude + vec2.latitude, vec1.longitude + vec2.longitude)
fun vecSub(vec1 : LatLng, vec2 : LatLng) : LatLng = LatLng(vec1.latitude - vec2.latitude, vec1.longitude - vec2.longitude)


class JanMapFragment : SupportMapFragment() {

    // --- Create drawer and info bar for species filtering ---
    // -> This moves Google controls over using screen and marker dimensions
    // -> 2% top-margin 1% top-padding 1% bottom-padding 2% bottom-margin => 94% height
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mapView = super.onCreateView(inflater, container, savedInstanceState)!!

        relView = RelativeLayout(this.context)
        relView.addView(mapView)

        // This needs to happen in post so measuredHeight is available
        mapView.post {
            val scrHeight = mapView.measuredHeight
            val scrWidth = mapView.measuredWidth
            val bmpSample = BitmapFactory.decodeResource(resources, R.drawable.otherfruit)
            Log.e("scrHeight", scrHeight.toString())
            Log.e("bmp wxh", " " + bmpSample.width + " " + bmpSample.height)

            // *** info bar
            // TODO XXX clean this up a lot, remove duplication, var names, ...
            val infoBar = LinearLayout(this.context)
            infoBar.orientation = LinearLayout.HORIZONTAL
            infoBar.gravity = Gravity.CENTER

            infoBar.layoutParams = {
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins((.02 * scrHeight).toInt(), (.02 * scrHeight).toInt(), (.02 * scrHeight).toInt(), (.02 * scrHeight).toInt())
                lp
            }()

            val info = TextView(this.context)
            info.text = getString(R.string.onlyShowing)
            infoBar.addView(info)

            val species = TextView(this.context)
            species.text = getString(resources.getIdentifier("tid99", "string", "xjcl.mundraub"))
            species.setTypeface(null, Typeface.BOLD)
            species.setTextColor(getFruitColor(resources, 99))
            infoBar.addView(species)

                val density = resources.displayMetrics.density
                val pad_min = (2.5 * density).toInt()
                val pad_max = (7.5 * density).toInt()
                val c_ = 999F
                val sd_ = ShapeDrawable(RoundRectShape(floatArrayOf(c_, c_, c_, c_, c_, c_, c_, c_), null, null))
                sd_.paint.color = Color.parseColor("#FFFFFF")  // I think the crosshairs are C0 or less, but I like D0 better
                sd_.setPadding(pad_max, pad_min, pad_max, pad_min)
                infoBar.setPadding(pad_max, pad_min, pad_max, pad_min)
                infoBar.background = sd_

            // https://developer.android.com/training/material/shadows-clipping
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) infoBar.elevation = 6F  // Default elevation of a FAB is 6

            // linearHolder needed so LinearLayout does not extend all the way to the edge
            val infoBarHolder = LinearLayout(this.context)
            infoBarHolder.orientation = LinearLayout.VERTICAL
            infoBarHolder.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            infoBarHolder.gravity = Gravity.CENTER
            infoBarHolder.addView(infoBar)

            relView.addView(infoBarHolder)


            // *** species drawer (LinearLayout)
            val linear = LinearLayout(this.context)
            linear.orientation = LinearLayout.VERTICAL

            val linearLabels = LinearLayout(this.context)
            linearLabels.orientation = LinearLayout.VERTICAL

            val ivs = HashMap<Int, ImageView>()

            for (entry in treeIdToMarkerIconSorted)
                ivs[entry.key] = ImageView(this.context)

            ivs[98] = ImageView(this.context)
            ivs[99] = ImageView(this.context)

            // *** Height calculation for markers
            // Note that a straight-up division of (totalHeight / numSection) gives poor results
            // E.g. dividing a distance of 42 into 4 sections straight-up would give 10 10 10 10 or 11 11 11 11
            //   but ideally we want 10 11 10 11 so the totalHeight is preserved  -> done by this function
            val totalHeight = .94 * (scrHeight - bmpSample.height)
            val exactHeight = (totalHeight / (ivs.size - 1))
            fun markerHeight(lo : Int, hi : Int) : Int = (hi * exactHeight).toInt() - (lo * exactHeight).toInt()

            // Prepare vertical labels next to filter (linearLabels)
            val groupNames = listOf(9 to getString(R.string.catFruitTrees), 4 to getString(R.string.catNutTrees),
                13 to getString(R.string.catFruitShrubs), 7 to getString(R.string.catHerbs))
            var cum = 0
            for (el in groupNames) {
                val tv = TextView(this.context)
                tv.text = el.second
                tv.gravity = Gravity.CENTER
                tv.rotation = 90F

                tv.measure(0, 0)
                Log.e("tvm", "${tv.measuredHeight} ${tv.measuredWidth}")

                tv.height = markerHeight(cum, cum + el.first) - density.toInt()  // make room for ImageView divider
                tv.width = tv.measuredHeight + 400  // 400 IQ

                val tvHolder = RelativeLayout(this.context)
                tvHolder.layoutParams = {
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.setMargins(-200, if (el == groupNames[0]) (4 * density).toInt() else 0, -200, 0)
                    lp
                }()
                tvHolder.addView(tv)

                cum += el.first
                linearLabels.addView(tvHolder)

                val iv = ImageView(this.context)
                iv.setImageResource(R.drawable.divider)  // has height of  = 1 * density
                linearLabels.addView(iv)

                totalLeftPadding = max(totalLeftPadding, (.04 * scrHeight).toInt() + bmpSample.width + tv.measuredHeight)
            }
            if (::mMap.isInitialized) mMap.setPadding(totalLeftPadding, 0, 0, 0)

            fun fillImageView(iv : ImageView, res: Int, i : Int) {
                val bmp = BitmapFactory.decodeResource(resources, res)
                iv.setImageBitmap(bmp)
                Log.e("bmpH", bmp.height.toString())

                val bottom = if (res == R.drawable._marker_reset_filter_a) 0 else -bmp.height + markerHeight(i, i+1)
                iv.layoutParams = {
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.setMargins(0, 0, 0, bottom)
                    lp
                }()
            }

            // sadly .yBy() is not enough for this as the EndAction can get interrupted
            fun animateJump(iv : ImageView) {
                if (!origY.containsKey(iv)) origY[iv] = iv.y
                iv.animate().y((origY[iv]?:0F) - 6 * density).withEndAction { iv.animate().y(origY[iv]?:0F) }  // We commence to make you (jump, jump)! :D
            }
            fun handleClick(key : Int, cond : (Int) -> Boolean, str : String) {
                val iv = ivs[key] ?: return
                Log.e("onClick", key.toString())
                species.text = getString(resources.getIdentifier("tid${key}", "string", "xjcl.mundraub"))  // TODO replace by packageName
                species.setTextColor(getFruitColor(resources, key))
                for (other in ivs)
                    other.value.setColorFilter(Color.parseColor(if (cond(other.key)) "#FFFFFF" else "#555555"), PorterDuff.Mode.MULTIPLY)
                selectedSpeciesStr = str
                animateJump(iv)
                mMap.animateCamera( CameraUpdateFactory.zoomBy(0F) )  // trigger updateMarkers()
            }

            // filter to 1 species
            var i = 0
            for (entry in treeIdToMarkerIconSorted) {
                val iv = ivs[entry.key] ?: continue
                iv.setOnClickListener { handleClick(entry.key, {it == entry.key || it > 90}, entry.key.toString()) }
                fillImageView(iv, entry.value, i)
                linear.addView(iv)
                i += 1
            }

            // filter to all species currently in season
            ivs[98]!!.setOnClickListener {
                val set = treeIdToSeason.keys.filter { isSeasonal(it, getCurMonth()) }.toSet()
                handleClick(98, {set.contains(it) || it > 90}, set.joinToString(","))  // defaults to "," on new Androids and ", " on old ones -- I freaking quit.
            }
            fillImageView(ivs[98]!!, R.drawable._marker_season_filter_b, i)
            linear.addView(ivs[98]!!)

            // reset filter (show all species)
            ivs[99]!!.setOnClickListener { handleClick(99, {true}, selectedSpeciesStrDefault) }
            fillImageView(ivs[99]!!, R.drawable._marker_reset_filter_a, i + 1)
            linear.addView(ivs[99]!!)


            val linearHolder = LinearLayout(this.context)
            linearHolder.addView(linearLabels)
            linearHolder.addView(linear)

            val pad = (.01 * scrHeight).toInt()
            val c = bmpSample.width / 2F + pad  // corner size
            val sd = ShapeDrawable(RoundRectShape(floatArrayOf(c, c, c, c, c, c, c, c), null, null))
            sd.paint.color = Color.parseColor("#FFFFFF")
            sd.setPadding(pad, pad, pad, pad)

            // https://developer.android.com/training/material/shadows-clipping
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) linearHolder.elevation = 6F  // Default elevation of a FAB is 6

            linearHolder.background = sd

            /*linear.layoutParams = {
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins((.02 * scrHeight).toInt(), (.02 * scrHeight).toInt(), 0, 0)
                lp
            }()*/  // setMargins doesn't work on older Androids... I'm going to murder someone at Google over this
            linearHolder.x = (.02 * scrHeight).toFloat()
            linearHolder.y = (.02 * scrHeight).toFloat()

            relView.addView(linearHolder)


            // *** FAB for Maps navigation ***
            fab = FloatingActionButton(this.context!!)
            fab.setImageBitmap( BitmapFactory.decodeResource(resources, R.drawable.material_directions) )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                fab.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context!!, R.color.colorPrimary))
            fab.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
            //fab.imageTintList = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
            //fab.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.colorAccent, null))
            fab.compatElevation = 6F
            fab.layoutParams = {
                val lp = CoordinatorLayout.LayoutParams(CoordinatorLayout.LayoutParams.WRAP_CONTENT, CoordinatorLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, (.04 * scrHeight).toInt(), (.04 * scrHeight).toInt())
                lp
            }()

            fab.measure(0, 0)
            fabAnimationFromTo = scrWidth.toFloat() to scrWidth - (.04 * scrHeight).toFloat() - fab.measuredWidth
            fab.x = fabAnimationFromTo.first

            // Why is Android so broken -_- Can't set gravity on FAB directly
            val fabHolder = LinearLayout(this.context)
            fabHolder.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            fabHolder.gravity = Gravity.END or Gravity.BOTTOM
            fabHolder.addView(fab)

            relView.addView(fabHolder)
        }

        return relView
    }
}

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, OnCameraIdleListener, ActivityCompat.OnRequestPermissionsResultCallback {

    // --- Place a single marker on the GoogleMap, and prepare its info window, using parsed JSON class ---
    private fun addMarkerFromFeature(feature: Feature): Marker {
        val latlng = LatLng(feature.pos[0], feature.pos[1])
        val tid = feature.properties?.tid
        val type = when {
            feature.properties == null -> "cluster"       // Cluster of 2+ markers
            treeIdToSeason[tid]?.first == 0.0 -> "other"  // Marker of unknown species with no season info
            else -> "normal"                              // Marker of known species with season info
        }

        val title = getString(resources.getIdentifier("tid$tid", "string", packageName))
        val fruitColor = getFruitColor(resources, tid)

        val icon =
            if (type == "cluster") // isCluster
                BitmapDescriptorFactory.fromBitmap( bitmapWithText(R.drawable._cluster_c, this, feature.count.toString(), 45F) )
            else // isTree
                BitmapDescriptorFactory.fromResource(treeIdToMarkerIcon[tid] ?: R.drawable.otherfruit)

        // *** The following code represents January-start as 1, mid-January as 1.5, February-start as 2, and so on
        val monthCodes = (1..12).joinToString("") { when {
             isSeasonal(tid, it + .25) &&  isSeasonal(tid, it + .75) -> "x"
             isSeasonal(tid, it + .25) && !isSeasonal(tid, it + .75) -> "l"
            !isSeasonal(tid, it + .25) &&  isSeasonal(tid, it + .75) -> "r"
            else -> "_"
        }}

        markersData[latlng] = MarkerData(type, title, monthCodes, getCurMonth(), isSeasonal(tid, getCurMonth()), fruitColor, feature.properties?.nid, null, null)

        return mMap.addMarker(MarkerOptions().position(latlng).title(title).icon(icon).anchor(.5F, if (type == "cluster") .5F else 1F))
    }

    // --- Place a list of markers on the GoogleMap ("var markers"), using raw JSON String ---
    private fun addLocationMarkers(jsonStrPre: String) {
        Log.e("addLocationMarkers", markers.size.toString() + " " + jsonStrPre)
        val jsonStr = if (jsonStrPre == "null") "{\"features\":[]}" else jsonStrPre

        // --- parse newly downloaded markers ---
        // API inconsistently either returns String or double/int... -> strip away double quotes
        val jsonStrClean = Regex(""""-?[0-9]+.?[0-9]+"""").replace(jsonStr) { it.value.substring(1, it.value.length - 1) }
        val root = Json.parse(Root.serializer(), jsonStrClean)

        // --- remove old markers not in newly downloaded set (also removes OOB markers) ---
        val featuresSet = HashSet<LatLng>( root.features.map { LatLng(it.pos[0], it.pos[1]) } )
        for (mark in markers.toMap()) {  // copy constructor
            if (!featuresSet.contains(mark.key)) { mark.value.remove(); markers.remove(mark.key); markersData.remove(mark.key) }
        }

        // --- add newly downloaded markers not already in old set ---
        for (feature in root.features) {
            val latlng = LatLng(feature.pos[0], feature.pos[1])
            if (markers.contains(latlng)) continue
            markers[latlng] = addMarkerFromFeature(feature)
        }
    }

    // --- Update markers when user finished moving the map ---
    private fun updateMarkers() {
        val zoom = mMap.cameraPosition.zoom
        Log.e("updateMarkers", "zoom $zoom")
        if (zoom == 2F || zoom == 3F) return  // Bugfix, do not remove, see commit message
        val bboxLo = mMap.projection.visibleRegion.latLngBounds.southwest
        val bboxHi = mMap.projection.visibleRegion.latLngBounds.northeast

        // API documented here: https://github.com/niccokunzmann/mundraub-android/blob/master/docs/api.md
        val url = "https://mundraub.org/cluster/plant?bbox=${bboxLo.longitude},${bboxLo.latitude},${bboxHi.longitude},${bboxHi.latitude}" +
             "&zoom=${(zoom + .25F).toInt()}&cat=${selectedSpeciesStr}"

        Log.e("updateMarkers", "GET $url")

        GlobalScope.launch(Dispatchers.IO) {
            val jsonStr = try { URL(url).readText() } catch (ex: Exception) { return@launch }
            runOnUiThread { addLocationMarkers(jsonStr) }
        }
    }

    override fun onCameraIdle() = updateMarkers()

    // --- OnInternetConnected: Automatically update (download) markers ---
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun setUpNetworking() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = runOnUiThread { this@MapsActivity.updateMarkers() }
        })
    }

    // --- On startup: Prepare map and cause onRequestPermissionsResult to be called ---
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setOnCameraIdleListener(this)
        mMap.setPadding(totalLeftPadding, 0, 0, 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) setUpNetworking()

        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        ActivityCompat.requestPermissions(this, permissions, 0)

        // --- Build a vertical layout to provide an info window for a marker ---
        // https://stackoverflow.com/a/31629308/2111778
        mMap.setInfoWindowAdapter(object : InfoWindowAdapter {
            override fun getInfoWindow(arg0: Marker): View? = null

            override fun getInfoContents(marker: Marker): View {
                val md = markersData[marker.position] ?: return TextView(this@MapsActivity)

                // 12 month circles of 13 pixels width -- ugly but WRAP_CONTENT just would not work =(
                val density = resources.displayMetrics.density
                var masterWidth = (12 * 13 * density).toInt()

                val info = LinearLayout(this@MapsActivity)
                info.orientation = LinearLayout.VERTICAL

                val photo = ImageView(this@MapsActivity)
                photo.setImageBitmap(scaleToWidth(md.image, masterWidth))
                if (md.image != null) info.addView(photo)

                val description = TextView(this@MapsActivity)
                description.width = masterWidth
                description.text = md.description ?: this@MapsActivity.getString(R.string.tapForInfo)
                description.textSize = 12F
                if (md.type != "cluster") info.addView(description)

                val title = TextView(this@MapsActivity)
                title.setTextColor(md.fruitColor)
                title.gravity = Gravity.CENTER
                title.setTypeface(null, Typeface.BOLD)
                title.text = marker.title
                info.addView(title)

                // no month/season information in this case so return early
                if (md.type == "cluster" || md.type == "other")
                    return info

                val seasonText = TextView(this@MapsActivity)
                seasonText.setTextColor(Color.BLACK)
                seasonText.text = this@MapsActivity.getString(if (md.isSeasonal) R.string.inSeason else R.string.notInSeason)
                info.addView(seasonText)

                val months = LinearLayout(this@MapsActivity)
                months.orientation = LinearLayout.HORIZONTAL
                for (i in 1..12) {
                    val circle = LinearLayout(this@MapsActivity)
                    circle.orientation = LinearLayout.HORIZONTAL

                    val circleLeft = ImageView(this@MapsActivity)
                    val circleRight = ImageView(this@MapsActivity)

                    val resLeft = if ("xl".contains(md.monthCodes[i-1])) R.drawable._dot_l1 else R.drawable._dot_l0
                    val resRight = if ("xr".contains(md.monthCodes[i-1])) R.drawable._dot_r1 else R.drawable._dot_r0
                    circleLeft.setImageResource(resLeft)
                    circleRight.setImageResource(resRight)
                    if ("xl".contains(md.monthCodes[i-1])) circleLeft.setColorFilter(md.fruitColor)
                    if ("xr".contains(md.monthCodes[i-1])) circleRight.setColorFilter(md.fruitColor)
                    // add vertical line for current time in year
                    if (md.curMonth.toInt() == i)
                        (if (md.curMonth % 1 < .5) circleLeft else circleRight).setImageBitmap(
                            bitmapWithText( (if (md.curMonth % 1 < .5) resLeft else resRight), this@MapsActivity,
                                "|", 50F, false,  2 * (md.curMonth % .5).toFloat(), if (md.isSeasonal) md.fruitColor else Color.GRAY) )

                    circle.addView(circleLeft)
                    circle.addView(circleRight)

                    val letter = TextView(this@MapsActivity)
                    letter.setTextColor(if (md.monthCodes[i-1] != '_') md.fruitColor else Color.GRAY)
                    letter.gravity = Gravity.CENTER
                    if (md.monthCodes[i-1] != '_') letter.setTypeface(null, Typeface.BOLD)
                    letter.text = "JFMAMJJASOND"[i-1].toString()

                    val month = LinearLayout(this@MapsActivity)
                    month.orientation = LinearLayout.VERTICAL
                    month.addView(circle)
                    month.addView(letter)
                    months.addView(month)
                }
                months.measure(0, 0)
                Log.e("width change", masterWidth.toString() + " -> " + months.measuredWidth)
                masterWidth = months.measuredWidth
                description.width = masterWidth
                photo.setImageBitmap(scaleToWidth(md.image, masterWidth))

                val day = RelativeLayout(this@MapsActivity)
                val tv = TextView(this@MapsActivity)
                tv.text = Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString()
                tv.setTextColor(if (md.isSeasonal) md.fruitColor else Color.GRAY)
                tv.setTypeface(null, Typeface.BOLD)
                tv.measure(0, 0)
                val params = RelativeLayout.LayoutParams(tv.measuredWidth, tv.measuredHeight)
                params.leftMargin = ( masterWidth / 12F * (md.curMonth - 1) - tv.measuredWidth / 2F ).toInt().coerceIn(0, masterWidth - tv.measuredWidth)
                day.addView(tv, params)

                info.addView(day)
                info.addView(months)
                return info
            }
        })

        // --- Disappear the navigation button once window closes ---
        mMap.setOnInfoWindowCloseListener {
            fab.animate().x(fabAnimationFromTo.first)
        }

        // --- Download detailed node description when user taps ("clicks") on info window ---
        mMap.setOnInfoWindowClickListener { marker ->
            val md = markersData[marker.position] ?: return@setOnInfoWindowClickListener
            if (md.description != null || md.nid == null) return@setOnInfoWindowClickListener

            GlobalScope.launch(Dispatchers.IO) {
                // --- Download number of finds and description ---
                val htmlStr = try { URL("https://mundraub.org/node/${md.nid}").readText() } catch (ex : Exception) { return@launch }

                val number = htmlStr.substringAfter("Anzahl: <span class=\"tag\">", "?").substringBefore("</span>")
                val description = htmlStr.substringAfter("<p>").substringBefore("</p>", "(no data)")
                val descriptionUnescaped = HtmlCompat.fromHtml(description, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()  // unescape "&quot;" etc
                md.description = "[$number] $descriptionUnescaped"

                runOnUiThread { marker.showInfoWindow() }

                // --- Download image in lowest quality ---
                val imageURL = htmlStr.substringAfter("srcset=\"", "").substringBefore(" ")
                if (imageURL.isBlank() || md.image != null) return@launch

                runOnUiThread {
                    Log.e("onMarkerClickListener", "Started Picasso on UI thread now ($imageURL)")
                    picassoBitmapTarget.md = md
                    picassoBitmapTarget.marker = marker
                    Picasso.with(this@MapsActivity).load("https://mundraub.org/$imageURL").into(picassoBitmapTarget)
                }
            }
        }

        // --- Custom zoom to marker at a *below-center* position to leave more space for its large info window ---
        mMap.setOnMarkerClickListener { marker ->
            if (markersData[marker.position]?.type ?: "" == "cluster") return@setOnMarkerClickListener true
            marker.showInfoWindow()
            // --- Click on FAB will give directions to Marker in Google Maps app ---
            fab.setOnClickListener {
                fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
                    if (location == null) return@addOnSuccessListener
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                        "http://maps.google.com/maps?saddr=${location.latitude}, ${location.longitude}&daddr=${marker.position.latitude}, ${marker.position.longitude}"
                    )))
                }
            }
            fab.animate().x(fabAnimationFromTo.second)
            val targetPosition = vecAdd(marker.position, vecMul(.25, vecSub(mMap.projection.visibleRegion.farLeft, mMap.projection.visibleRegion.nearLeft)))
            mMap.animateCamera(CameraUpdateFactory.newLatLng(targetPosition), 300, null)
            true
        }
    }

    // --- When user rotates phone, re-download markers for the new screen size ---
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // dummy zoom to trigger onCameraIdle with *correct* orientation  https://stackoverflow.com/a/61993030/2111778
        mMap.animateCamera( CameraUpdateFactory.zoomBy(0F) )
    }

    // --- On startup: If GPS enabled, then zoom into user, else zoom into Germany ---
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        fusedLocationClient.lastLocation.addOnFailureListener(this) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(51.17, 10.45), 6F))  // Germany
        }
        fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
            if (location == null) return@addOnSuccessListener
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 13F))
        }

        if (grantResults.isEmpty() || grantResults[0] == PackageManager.PERMISSION_DENIED) return

        mMap.isMyLocationEnabled = true  // show blue circle on map
    }

    // --- On startup: Prepare classes ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        supportActionBar!!.title = HtmlCompat.fromHtml("<font color=\"#94b422\">" + "Navigator v${BuildConfig.VERSION_NAME}!" + "</font>", HtmlCompat.FROM_HTML_MODE_LEGACY)
        supportActionBar!!.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        supportActionBar!!.setHomeAsUpIndicator(R.drawable.mundraub_logo_bar_48dp)  // export with 15px border
        supportActionBar!!.displayOptions = ActionBar.DISPLAY_SHOW_HOME or
                ActionBar.DISPLAY_SHOW_TITLE or ActionBar.DISPLAY_HOME_AS_UP or ActionBar.DISPLAY_USE_LOGO

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as JanMapFragment
        mapFragment.getMapAsync(this)
        // retains markers if user rotates phone etc. (useful offline)  https://stackoverflow.com/a/22058966/2111778
        mapFragment.retainInstance = true
    }

    override fun onBackPressed() {
        moveTaskToBack(true)  // do not call onCreate after user accidentally hits Back (useful offline)
    }
}


// TODO publishing
//    - write blog post about it
//    - publish to reddit about it
//    - official Facebook group
//    - official email newsletter

// TODO QA
//    - Full automated UI scripts using Espresso
//    - Node test with image: https://mundraub.org/node/75327
//    - Ensure correct season information
//    - Test different pixel densities by setting screen rezo on my phone
//    - Test both supported languages (EN/DE)
//    - Test rotation
//    - Test offline use



// * TODO clusters
//    - use Material design cluster icon with shadow
//        - should fix font centering issue too
//    - request max zoom level earlier (clusters are a useless anti-affordance)

// TODO marker filter
//    * apply darkened markers to map too
//    - animation / FloatingActionButton (slides out when tapped, also can be used to reset filtering)
//    - fix phone rotation  -> put 'val linear' into own singleton class that has an updateHeight function
//         -> either remove drawer, shrink it (???) or put it on the x axis (bottom) (?)
//         -> minor priority as the app is not really usable in landscape mode

// TODO latlng boundaries
//    - extend boundaries to go slightly offscreen so less re-loading needed?

// TODO bugs
//    - when tapping a marker, markers reload, so it sometimes deletes the marker in focus
//    - better workaround for initial Maps download issue

// * TODO pokemon
//    * favorite markers
//        * in a cardview list  -> wait no on the main map!!
//        * store offline
//    - detect when someone "visits" a marker
//    - list of recently visited or starred markers
//    - list of which marker types have ever been visited (including link to most recent one)
//    - list of how common each marker type is

// TODO UI
//    * immediately download info when tapping marker (1 instead of 2 taps)
//    - "force reload" button

// TODO AppBar
//    - one-tap menu in the AppBar (https://developer.android.com/training/appbar)

// TODO marker availability
//    - startup: load markers from last time
//    - keep markers near user location always
//    - favorite markers that get permanently stored


// TODO user profiles
//    - allow login
//    - allow adding a node
//    - allow editing a node
//    - allow reporting a problem with a node


// TODO wontfix
//    - rarely used marker types: groups, actions, cider, saplings
//    - draw in sorted order and/or with z-score so front markers are in front -> rarely needed
