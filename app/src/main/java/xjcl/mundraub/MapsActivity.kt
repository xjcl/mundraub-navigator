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
import android.view.*
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URL
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.concurrent.thread
import kotlin.math.max


@Serializable
data class Properties(val nid: Int, val tid: Int)  // node id, tree type id
@Serializable
data class Feature(val pos: List<Double>, val properties: Properties? = null, val count: Int? = null)
@Serializable
data class Root(val features: List<Feature>)

data class MarkerData(val type : String, val title : String, val monthCodes : String, val curMonth : Double,
                      val isSeasonal : Boolean, val fruitColor : Int, val nid : Int?, var description : String?,
                      var uploader : String?, var uploadDate : String?, var image : Bitmap?)

val getCurMonth = { Calendar.getInstance().get(Calendar.MONTH) + 1 + Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toDouble() / 32 }
val isSeasonal = {tid : Int?, month : Double -> treeIdToSeason[tid]?.first ?: 0.0 <= month && month <= treeIdToSeason[tid]?.second ?: 0.0}

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
    override fun onBitmapLoaded(bitmap: Bitmap?, from: LoadedFrom?) {
        md?.image = bitmap
        marker?.showInfoWindow()
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

fun materialDesignBg(padX: Int, padY: Int, c: Float): Drawable {
    val sd = ShapeDrawable(RoundRectShape(floatArrayOf(c, c, c, c, c, c, c, c), null, null))
    sd.paint.color = Color.parseColor("#FFFFFF")
    sd.setPadding(padX, padY, padX, padY)
    return sd
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
        mapView = super.onCreateView(inflater, container, savedInstanceState) ?: return null

        relView = RelativeLayout(context)
        relView.addView(mapView)

        // This needs to happen in post so measuredHeight is available
        mapView.post {
            val scrWidth = mapView.measuredWidth
            val scrHeight = mapView.measuredHeight
            val bmpSample = BitmapFactory.decodeResource(resources, R.drawable.otherfruit)
            Log.e("scrHeight", scrHeight.toString())
            Log.e("bmp wxh", " " + bmpSample.width + " " + bmpSample.height)

            // *** info bar
            // TODO XXX clean this up a lot, remove duplication, var names, ...
            val infoBar = LinearLayout(context)
            infoBar.orientation = LinearLayout.HORIZONTAL
            infoBar.gravity = Gravity.CENTER
            infoBar.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins((.02 * scrHeight).toInt(), (.02 * scrHeight).toInt(), (.02 * scrHeight).toInt(), (.02 * scrHeight).toInt())
                addRule(RelativeLayout.CENTER_IN_PARENT)
                addRule(RelativeLayout.ALIGN_PARENT_TOP)
            }

            val info = TextView(context)
            info.text = getString(R.string.onlyShowing)
            infoBar.addView(info)

            val species = TextView(context)
            species.text = getString(resources.getIdentifier("tid99", "string", "xjcl.mundraub"))
            species.setTypeface(null, Typeface.BOLD)
            species.setTextColor(getFruitColor(resources, 99))
            infoBar.addView(species)

            val density = resources.displayMetrics.density
            infoBar.background = materialDesignBg((7.5 * density).toInt(), (2.5 * density).toInt(), 999F)

            // https://developer.android.com/training/material/shadows-clipping
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) infoBar.elevation = 6F  // Default elevation of a FAB is 6

            relView.addView(infoBar)


            // *** species filter bar (LinearLayout)
            // species <80 (4-12 14-17 18-30 31-37)   groups 80-89  special 90-99
            val linear = LinearLayout(context)
            linear.orientation = LinearLayout.VERTICAL

            val linearLabels = LinearLayout(context)
            linearLabels.orientation = LinearLayout.VERTICAL

            val ivs = HashMap<Int, ImageView>()

            for (entry in treeIdToMarkerIconSorted)
                ivs[entry.key] = ImageView(context)

            ivs[98] = ImageView(context)
            ivs[99] = ImageView(context)

            // *** Height calculation for markers
            // Note that a straight-up division of (totalHeight / numSection) gives poor results
            // E.g. dividing a distance of 42 into 4 sections straight-up would give 10 10 10 10 or 11 11 11 11
            //   but ideally we want 10 11 10 11 so the totalHeight is preserved  -> done by this function
            val totalHeight = .94 * (scrHeight - bmpSample.height)
            val exactHeight = (totalHeight / (ivs.size - 1))
            fun markerHeight(lo : Int, hi : Int) : Int = (hi * exactHeight).toInt() - (lo * exactHeight).toInt()

            fun fillImageView(iv : ImageView, res: Int, i : Int) {
                val bmp = BitmapFactory.decodeResource(resources, res)
                iv.setImageBitmap(bmp)
                Log.e("bmpH", bmp.height.toString())

                val bottom = if (res == R.drawable._marker_reset_filter_a) 0 else -bmp.height + markerHeight(i, i+1)
                iv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, bottom) }
            }

            // sadly .yBy() is not enough for this as the EndAction can get interrupted
            fun animateJump(iv : View) {
                if (!origY.containsKey(iv)) origY[iv] = iv.y
                iv.animate().y((origY[iv]?:0F) - 6 * density).withEndAction { iv.animate().y(origY[iv]?:0F) }  // We commence to make you (jump, jump)! :D
            }
            fun handleClick(iv : View, key : Int, cond : (Int) -> Boolean) {
                Log.e("onClick", key.toString())
                species.text = getString(resources.getIdentifier("tid${key}", "string", "xjcl.mundraub"))  // TODO replace by packageName
                species.setTextColor(getFruitColor(resources, key))
                for (other in ivs)
                    other.value.setColorFilter(Color.parseColor(if (cond(other.key) || other.key >= 90) "#FFFFFF" else "#555555"), PorterDuff.Mode.MULTIPLY)
                selectedSpeciesStr = ivs.filter { cond(it.key) }.map { it.key.toString() }.joinToString(",")
                animateJump(iv)
                mMap.animateCamera( CameraUpdateFactory.zoomBy(0F) )  // trigger updateMarkers()
            }

            // Prepare vertical labels next to filter (linearLabels)  Pair(size, key)
            data class Group(val key : Int, val size : Int, val lo : Int, val hi : Int)
            //val groups = listOf(9 to 80, 4 to 81, 13 to 82, 7 to 83)
            val groups = listOf(Group(80, 9, 4, 12), Group(81, 4, 14, 17), Group(82, 13, 18, 30), Group(83, 7, 31, 37))
            var cum = 0
            for (group in groups) {
                val tv = TextView(context)
                tv.text = getString(resources.getIdentifier("tid${group.key}", "string", "xjcl.mundraub"))

                tv.gravity = Gravity.CENTER
                tv.rotation = 90F

                tv.measure(0, 0)
                Log.e("tvm", "${tv.measuredHeight} ${tv.measuredWidth}")

                tv.height = markerHeight(cum, cum + group.size) - density.toInt()  // make room for ImageView divider
                tv.width = tv.measuredHeight + 400  // 400 IQ

                val tvHolder = RelativeLayout(context)   // NEEDED else the touch regions are all wrong
                tvHolder.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(-200, if (group == groups[0]) (4 * density).toInt() else 0, -200, 0) }
                tvHolder.addView(tv)

                tv.setOnClickListener { handleClick(tv, group.key) { it in group.lo..group.hi } }

                cum += group.size
                linearLabels.addView(tvHolder)

                val iv = ImageView(context)
                iv.setImageResource(R.drawable.divider)  // has height of  = 1 * density
                linearLabels.addView(iv)

                totalLeftPadding = max(totalLeftPadding, (.04 * scrHeight).toInt() + bmpSample.width + tv.measuredHeight)
            }
            mMap.setPadding(totalLeftPadding, 0, 0, 0)

            // filter to 1 species
            var i = 0
            for (entry in treeIdToMarkerIconSorted) {
                val iv = ivs[entry.key] ?: continue
                iv.setOnClickListener { handleClick(iv, entry.key) { it == entry.key } }
                fillImageView(iv, entry.value, i)
                linear.addView(iv)
                i += 1
            }

            // filter to all species currently in season
            ivs[98]?.apply {
                this.setOnClickListener {
                    val set = treeIdToSeason.keys.filter { isSeasonal(it, getCurMonth()) }.toSet()
                    handleClick(this, 98) { set.contains(it) }  // defaults to "," on new Androids and ", " on old ones -- I freaking quit.
                }
                fillImageView(this, R.drawable._marker_season_filter_b, i)
                linear.addView(this)
            }

            // reset filter (show all species)
            ivs[99]?.apply {
                this.setOnClickListener { handleClick(this, 99) { it < 90 } }
                fillImageView(this, R.drawable._marker_reset_filter_a, i + 1)
                linear.addView(this)
            }

            val linearHolder = LinearLayout(context).apply {
                addView(linearLabels)
                addView(linear)
                val pad = (.01 * scrHeight).toInt()
                background = materialDesignBg(pad, pad, bmpSample.width / 2F + pad)
                // https://developer.android.com/training/material/shadows-clipping
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) elevation = 6F  // Default elevation of a FAB is 6

                // setMargins((.02 * scrHeight).toInt(), (.02 * scrHeight).toInt(), 0, 0)
                // ^ setMargins doesn't work on older Androids... I'm going to murder someone at Google over this
                x = .02F * scrHeight
                y = .02F * scrHeight
            }

            relView.addView(linearHolder)


            // *** FAB for Maps navigation ***
            fab = FloatingActionButton(context!!)
            fab.setImageBitmap( BitmapFactory.decodeResource(resources, R.drawable.material_directions) )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                fab.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context!!, R.color.colorPrimary))
            fab.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
            //fab.imageTintList = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
            //fab.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.colorAccent, null))
            fab.compatElevation = 6F
            fab.layoutParams = CoordinatorLayout.LayoutParams(CoordinatorLayout.LayoutParams.WRAP_CONTENT, CoordinatorLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, (.04 * scrHeight).toInt(), (.04 * scrHeight).toInt()) }

            fab.measure(0, 0)
            fabAnimationFromTo = scrWidth.toFloat() to scrWidth - (.04 * scrHeight).toFloat() - fab.measuredWidth
            fab.x = fabAnimationFromTo.first

            // TODO try to get rid of this intermediate class
            val fabHolder = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
                gravity = Gravity.END or Gravity.BOTTOM
                addView(fab)
            }

            relView.addView(fabHolder)
        }

        return relView
    }
}

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, OnCameraIdleListener, ActivityCompat.OnRequestPermissionsResultCallback {

    // --- Place a single marker on the GoogleMap, and prepare its info window, using parsed JSON class ---
    private fun addMarkerFromFeature(feature: Feature) {
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

        markersData[latlng] = MarkerData(type, title, monthCodes, getCurMonth(), isSeasonal(tid, getCurMonth()), fruitColor,
            feature.properties?.nid, null, null, null, null)

        runOnUiThread {
            markers[latlng] = mMap.addMarker(MarkerOptions().position(latlng).title(title).icon(icon).anchor(.5F, if (type == "cluster") .5F else 1F))
        }
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
            if (!featuresSet.contains(mark.key)) { runOnUiThread { mark.value.remove(); markers.remove(mark.key); markersData.remove(mark.key) } }
        }

        // --- add newly downloaded markers not already in old set ---
        for (feature in root.features) {
            val latlng = LatLng(feature.pos[0], feature.pos[1])
            if (markers.contains(latlng)) continue
            addMarkerFromFeature(feature)
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

        thread {
            val jsonStr = try { URL(url).readText() } catch (ex: Exception) {
                runOnUiThread { Toast.makeText(this,  getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show() }
                return@thread
            }
            addLocationMarkers(jsonStr)
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
                description.textSize = 12F
                if (md.type != "cluster") info.addView(description)
                if (md.description != null) {
                    description.text = md.description

                    val uploader = TextView(this@MapsActivity)
                    uploader.textSize = 12F
                    uploader.text = md.uploader
                    uploader.gravity = Gravity.RIGHT
                    uploader.maxWidth = masterWidth
                    info.addView(uploader)

                    val uploadDate = TextView(this@MapsActivity)
                    uploadDate.textSize = 12F
                    uploadDate.text = md.uploadDate
                    uploadDate.gravity = Gravity.RIGHT
                    uploadDate.maxWidth = masterWidth
                    info.addView(uploadDate)
                } else {
                    description.text = this@MapsActivity.getString(R.string.tapForInfo)
                }

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

            thread {
                // --- Download number of finds and description ---
                val htmlStr = try { URL("https://mundraub.org/node/${md.nid}").readText() } catch (ex : Exception) {
                    runOnUiThread { Toast.makeText(this,  getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show() }
                    return@thread
                }

                fun extractUnescaped(htmlStr : String, after : String, before : String) : String {
                    val extractEscaped = htmlStr.substringAfter(after).substringBefore(before, "(no data)")
                    return HtmlCompat.fromHtml(extractEscaped, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()  // unescape "&quot;" etc
                }

                val number = extractUnescaped(htmlStr, "Anzahl: <span class=\"tag\">", "</span>")
                val description = extractUnescaped(htmlStr, "<p>", "</p>")
                md.uploader = extractUnescaped(htmlStr.substringAfter("typeof=\"schema:Person\""), ">", "</span>")
                md.uploadDate = extractUnescaped(htmlStr.substringAfter("am <span>"), ", ", " - ")
                md.description = "[$number] $description"

                runOnUiThread { marker.showInfoWindow() }

                // --- Download image in lowest quality ---
                val imageURL = htmlStr.substringAfter("srcset=\"", "").substringBefore(" ")
                if (imageURL.isBlank() || md.image != null) return@thread

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

    // if we add a marker, resume at its location
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 33 && resultCode == Activity.RESULT_OK && data != null) {
            val lat = data.getDoubleExtra("lat", 0.0)
            val lng = data.getDoubleExtra("lng", 0.0)
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 18F))
            // TODO: possible to also open its window?
        }
    }

    // Handle ActionBar option selection
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.title) {
            "Add" -> {
                val intent = Intent(this, AddPlantActivity::class.java)
                startActivityForResult(intent, 33)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Create the ActionBar options menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val icon = ContextCompat.getDrawable(this, R.drawable.material_add_location) ?: return true
        icon.setColorFilter(resources.getColor(R.color.colorPrimary), PorterDuff.Mode.SRC_IN)

        menu.add(0, 0, 0, "Add").setIcon(icon).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    // --- On startup: Prepare classes ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        supportActionBar?.apply {
            title = HtmlCompat.fromHtml("<font color=\"#94b422\">" + "Nav. v${BuildConfig.VERSION_NAME}!" + "</font>", HtmlCompat.FROM_HTML_MODE_LEGACY)
            setBackgroundDrawable(ColorDrawable(Color.WHITE))
            setHomeAsUpIndicator(R.drawable.mundraub_logo_bar_48dp)  // export with 15px border
            displayOptions = ActionBar.DISPLAY_SHOW_HOME or ActionBar.DISPLAY_SHOW_TITLE or ActionBar.DISPLAY_HOME_AS_UP or ActionBar.DISPLAY_USE_LOGO
        }

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
//            * um we need a cardview of added markers first
//        * store offline
//    - detect when someone "visits" a marker
//    - list of recently visited or starred markers
//    - list of which marker types have ever been visited (including link to most recent one)
//    - list of how common each marker type is

// TODO UI
//    - all markers jump when pressing filter?
//    * immediately download info when tapping marker (1 instead of 2 taps)
//    - "force reload" button

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

// TODO show seasonality in infobar

// TODO make img/ and docs/ dir
// TODO v11

/*
Kleine Sachen über die ich noch nachgedacht habe:
- Eventuell direkt auf der Karte die Marker dunkler machen die nicht saisonal sind
- Direkter Download von Marker-Infos beim ersten Berühren. Da ist die Frage wie sehr es das Backend belastet

Größere Features:
- Marker als "Favoriten" makieren. Die werden dann am besten permanent auf dem Gerät gespeichert. Ich dachte zuerst an eine Liste aber ein Filter mit grauem Icon wie die anderen beiden wäre sogar intuitiver denke ich.
- Marker-History. Die am besten automatisch bemerkt an welchen Markern man vorbei gelaufen ist. Daraus kann man dann Gamification machen und schauen ob man alle Sorten besuchen kann. Da braucht man auch ein Feature um fälschlich besuchte Marker wieder zu entfernen.

Error message
*/
