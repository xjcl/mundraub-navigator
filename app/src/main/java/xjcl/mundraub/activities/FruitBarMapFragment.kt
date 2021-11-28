package xjcl.mundraub.activities

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import xjcl.mundraub.R
import xjcl.mundraub.data.*
import xjcl.mundraub.layouts.createMonthsBarLayout
import xjcl.mundraub.utils.*
import kotlin.math.max

/**
 * Draw the map and the UI elements on it
 * - "Fruit bar" at the left for filtering by fruit type
 * - "Info bar" at the top for informing about the currently selected type
 * - "FAB" at the bottom right for Google Maps navigation (when marker selected)
 *
 * Note this is by far the worst quality code in this app because
 * - it's just not possible to do a layout this complex in XML
 * - there is a forced separation of the MapsActivity and the SupportMapFragment
 *      - meaning i can only draw UI elements on the SupportMapFragment, not the MapsActivity!
 *          - but those UI elements manipulate the map, so global+member variables are used to communicate
 */
class FruitBarMapFragment : SupportMapFragment() {

    private val ivs = mutableMapOf<Int, ImageView>()
    private var density : Float = 0f
    private var scrHeight : Int = 0
    private val origY = mutableMapOf<View, Float>()

    private lateinit var bmpSample : Bitmap
    private lateinit var infoBar : LinearLayout
    private lateinit var species : TextView
    private lateinit var relView : RelativeLayout

    // sadly .yBy() is not enough for this as the EndAction can get interrupted
    private fun animateJump(iv : View) {
        if (!origY.containsKey(iv)) origY[iv] = iv.y
        iv.animate().y((origY[iv]?:0F) - 6 * density).withEndAction { iv.animate().y(origY[iv]?:0F) }  // We commence to make you (jump, jump)! :D
    }

    fun handleFilterClick(iv : View?, key : Int, cond : (Int) -> Boolean) {
        Log.e("onClick", key.toString())
        species.text = context!!.getString(context!!.resources.getIdentifier("tid${key}", "string", context!!.packageName))
        species.setTextColor(getFruitColor(context!!.resources, key))
        for (other in ivs)
            other.value.setColorFilter(Color.parseColor(if (cond(other.key) || other.key >= 90) "#FFFFFF" else "#555555"), PorterDuff.Mode.MULTIPLY)
        // dummy category "1" added to handle two issues that occur when passing an empty filter string (no filtering done, no tid returned(!))
        selectedSpeciesStr = ivs.filter { cond(it.key) }.map { it.key.toString() }.let { listOf("1") + it }.joinToString(",")
        iv?.let { animateJump(it) }
        mMap?.animateCamera(CameraUpdateFactory.zoomBy(0F))  // trigger updateMarkers()
        infoBar.removeViewAt(1) // removes monthsBar
        infoBar.addView(createMonthsBarLayout(context!!, key))
        infoBar.setOnClickListener {
            if (treeIdToProfileUrl[key] != null)
                (context as Activity).startActivity(Intent(context, PlantProfile::class.java).putExtra("tid", key))
        }
    }

    private fun createFilterBarLayout() : LinearLayout {
        // *** species filter bar (LinearLayout)
        // species <80 (4-12 14-17 18-30 31-37)   groups 80-89  special 90-99

        val linear = LinearLayout(context)
        linear.orientation = LinearLayout.VERTICAL

        val linearLabels = LinearLayout(context)
        linearLabels.orientation = LinearLayout.VERTICAL

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
            val bmp = BitmapFactory.decodeResource(context!!.resources, res)
            iv.setImageBitmap(bmp)
            Log.e("bmpH", bmp.height.toString())

            val bottom = if (res == R.drawable._marker_reset_filter_a) 0 else -bmp.height + markerHeight(i, i+1)
            iv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, bottom) }
        }

        // Prepare vertical labels next to filter (linearLabels)  Pair(size, key)
        data class Group(val key : Int, val size : Int, val lo : Int, val hi : Int)
        //val groups = listOf(9 to 80, 4 to 81, 13 to 82, 7 to 83)
        val groups = listOf(Group(80, 9, 4, 12), Group(81, 4, 14, 17), Group(82, 13, 18, 30), Group(83, 7, 31, 37))
        var cum = 0
        for (group in groups) {
            val tv = TextView(context)
            tv.text = context!!.getString(context!!.resources.getIdentifier("tid${group.key}", "string", context!!.packageName))

            tv.gravity = Gravity.CENTER
            tv.rotation = 90F

            tv.measure(0, 0)
            Log.e("tvm", "${tv.measuredHeight} ${tv.measuredWidth}")

            tv.height = markerHeight(cum, cum + group.size) - (density + .5).toInt()  // make room for ImageView divider
            Log.e("tvWidth", "${tv.measuredHeight} ${bmpSample.width}")
            val tvWidth = max(tv.measuredHeight, (bmpSample.width * .9).toInt())
            tv.width = tvWidth + 400  // 400 IQ

            val tvHolder = RelativeLayout(context)   // NEEDED else the touch regions are all wrong
            tvHolder.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(-200, if (group == groups[0]) (4 * density).toInt() else 0, -200, 0) }
            tvHolder.addView(tv)

            tv.setOnClickListener { handleFilterClick(tv, group.key) { it in group.lo..group.hi } }

            cum += group.size
            linearLabels.addView(tvHolder)

            val iv = ImageView(context)
            iv.minimumWidth = (tvWidth * .85).toInt()
            iv.minimumHeight = (density + .5).toInt()
            iv.setBackgroundColor(Color.GRAY)
            iv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            linearLabels.addView(iv)

            totalLeftPadding = max(totalLeftPadding, (.04 * scrHeight).toInt() + bmpSample.width + tvWidth)
        }
        mMap?.setPadding(totalLeftPadding, 0, 0, 0)

        // filter to 1 species
        var i = 0
        for (entry in treeIdToMarkerIconSorted) {
            val iv = ivs[entry.key] ?: continue
            iv.setOnClickListener { handleFilterClick(iv, entry.key) { it == entry.key } }
            fillImageView(iv, entry.value, i)
            linear.addView(iv)
            i += 1
        }

        // filter to all species currently in season
        ivs[98]?.apply {
            this.setOnClickListener {
                val set = treeIdToSeason.keys.filter { isSeasonal(it, getCurMonth()) }.toSet()
                handleFilterClick(this, 98) { set.contains(it) }  // defaults to "," on new Androids and ", " on old ones -- I freaking quit.
            }
            fillImageView(this, R.drawable._marker_season_filter_b, i)
            linear.addView(this)
        }

        // reset filter (show all species)
        ivs[99]?.apply {
            this.setOnClickListener { handleFilterClick(this, 99) { it < 90 } }
            fillImageView(this, R.drawable._marker_reset_filter_a, i + 1)
            linear.addView(this)
        }

        return LinearLayout(context).apply {
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
    }

    private fun createInfoBarLayout(): LinearLayout {
        // *** central info bar at the top, showing the currently active filter ***
        infoBar = LinearLayout(context)
        infoBar.orientation = LinearLayout.VERTICAL
        infoBar.gravity = Gravity.CENTER
        infoBar.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins((.02 * scrHeight).toInt(), (.02 * scrHeight).toInt(), (.02 * scrHeight).toInt(), (.02 * scrHeight).toInt())
            addRule(RelativeLayout.CENTER_IN_PARENT)
            addRule(RelativeLayout.ALIGN_PARENT_TOP)
        }

        val infoSpecies = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER

            val info = TextView(context)
            info.text = getString(R.string.onlyShowing)
            this.addView(info)

            species = TextView(context)
            species.text = getString(R.string.tid99)
            species.setTypeface(null, Typeface.BOLD)
            species.setTextColor(getFruitColor(resources, 99))
            this.addView(species)
        }
        infoBar.addView(infoSpecies)

        val months = createMonthsBarLayout(context!!, 99)
        infoBar.addView(months)

        infoBar.background = materialDesignBg((7.5 * density).toInt(), (2.5 * density).toInt(), 999F)

        // https://developer.android.com/training/material/shadows-clipping
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) infoBar.elevation = 6F  // Default elevation of a FAB is 6

        return infoBar
    }

    private fun createFABLayout(): LinearLayout {
        // *** FAB for Maps navigation ***
        fab = FloatingActionButton(context!!)
        fab.setImageBitmap(BitmapFactory.decodeResource(resources, R.drawable.material_directions))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            fab.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context!!, R.color.colorPrimary))
        fab.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
        //fab.imageTintList = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
        //fab.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.colorAccent, null))
        fab.compatElevation = 6F
        fab.layoutParams = CoordinatorLayout.LayoutParams(CoordinatorLayout.LayoutParams.WRAP_CONTENT, CoordinatorLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, (.04 * scrHeight).toInt(), (.04 * scrHeight).toInt()) }

        val scrWidth = mapView.measuredWidth
        fab.measure(0, 0)
        fabAnimationFromTo = scrWidth.toFloat() to scrWidth - (.04 * scrHeight).toFloat() - fab.measuredWidth
        fab.x = fabAnimationFromTo.first

        // TODO properly use Android RelativeLayout to avoid this intermediate class
        return LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            gravity = Gravity.END or Gravity.BOTTOM
            addView(fab)
        }
    }

    fun mapViewPostInner() {
        relView.removeAllViews()
        relView.addView(mapView)

        scrHeight = mapView.measuredHeight
        bmpSample = BitmapFactory.decodeResource(resources, R.drawable.icon_otherfruit)
        Log.e("scrHeight", scrHeight.toString())
        Log.e("bmp wxh", " " + bmpSample.width + " " + bmpSample.height)

        relView.addView(createInfoBarLayout())
        relView.addView(createFilterBarLayout())
        relView.addView(createFABLayout())
    }

    fun mapViewPost() {
        // This needs to happen in post so measuredHeight is available
        mapView.post {
            mapViewPostInner()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    // --- Create drawer and info bar for species filtering ---
    // -> This moves Google controls over using screen and marker dimensions
    // -> 2% top-margin 1% top-padding 1% bottom-padding 2% bottom-margin => 94% height
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        mapView = super.onCreateView(inflater, container, savedInstanceState)

        relView = RelativeLayout(context)
        relView.addView(mapView)

        density = resources.displayMetrics.density
        mapViewPost()

        return relView
    }
}
