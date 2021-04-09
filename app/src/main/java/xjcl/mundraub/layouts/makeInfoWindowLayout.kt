package xjcl.mundraub.layouts

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.google.android.gms.maps.model.Marker
import xjcl.mundraub.R
import xjcl.mundraub.activities.FruitBarMapFragment
import xjcl.mundraub.data.markersData
import xjcl.mundraub.data.treeIdToMarkerFrame
import xjcl.mundraub.utils.getCurMonth
import xjcl.mundraub.utils.scaleToHeight
import java.util.Calendar


fun makeInfoWindowLayout(context: Context, mapFragment: FruitBarMapFragment, marker: Marker): View {
    val md = markersData[marker.position] ?: return TextView(context)

    val months = mapFragment.createMonthsBarLayout(md)
    months.measure(0, 0)
    val masterWidth = months.measuredWidth

    val info = LinearLayout(context)
    info.orientation = LinearLayout.VERTICAL

    val title = TextView(context)
    title.setTextColor(md.fruitColor)
    title.gravity = Gravity.CENTER
    title.setTypeface(null, Typeface.BOLD)
    title.text = marker.title
    title.textSize = 18F
    info.addView(title)

    val photo = ImageView(context)
    photo.setImageBitmap(scaleToHeight(
        if (md.image != null)
            md.image
        else
            BitmapFactory.decodeResource(context.resources, treeIdToMarkerFrame[md.tid] ?: R.drawable.frame_otherfruit)
        , (9 * masterWidth) / 16
    ))
    info.addView(photo)

    val description = TextView(context)
    description.width = masterWidth
    description.textSize = 12F
    description.minLines = 5
    if (md.truncate) {
        description.maxLines = 5
        description.ellipsize = TextUtils.TruncateAt.END
    }
    if (md.type != "cluster") info.addView(description)

    description.text = md.description ?: context.getString(R.string.loading)
    val byLine = LinearLayout(context)

    val uploader = TextView(context)
    uploader.textSize = 10.5F
    uploader.text = md.uploader ?: context.getString(R.string.loading)
    uploader.gravity = Gravity.LEFT
    uploader.maxWidth = masterWidth
    uploader.measure(0, 0)

    val uploadDate = TextView(context)
    uploadDate.textSize = 10.5F
    uploadDate.text = md.uploadDate ?: context.getString(R.string.loading)
    uploadDate.gravity = Gravity.RIGHT
    uploadDate.maxWidth = masterWidth
    uploadDate.measure(0, 0)

    val whitespace = TextView(context)
    whitespace.width = masterWidth - uploader.measuredWidth - uploadDate.measuredWidth

    byLine.addView(uploader)
    byLine.addView(whitespace)
    byLine.addView(uploadDate)

    info.addView(byLine)

    // no month/season information in this case so return early
    if (md.monthCodes.all { it == '_' })
        return info

    val day = RelativeLayout(context)
    val tv = TextView(context)
    tv.text = Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString()
    tv.setTextColor(if (md.isSeasonal) md.fruitColor else Color.GRAY)
    tv.setTypeface(null, Typeface.BOLD)
    tv.measure(0, 0)
    tv.layoutParams = RelativeLayout.LayoutParams(tv.measuredWidth, tv.measuredHeight).apply {
        leftMargin = ( masterWidth / 12F * (md.curMonth - 1) - tv.measuredWidth / 2F ).toInt().coerceIn(0, masterWidth - tv.measuredWidth)
    }
    day.addView(tv)

    if (md.isSeasonal) {
        // using RelativeLayout.ALIGN_PARENT_RIGHT just causes a funk, so ugly workaround here
        val ripe2 = ImageView(context)
        ripe2.setImageResource(R.drawable._ripe)
        ripe2.measure(0, 0)
        ripe2.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
            if (getCurMonth() < 8.5)
                leftMargin = masterWidth - ripe2.measuredWidth
            addRule(RelativeLayout.CENTER_VERTICAL)
            RelativeLayout.ALIGN_PARENT_RIGHT
        }
        if (md.isSeasonal) ripe2.setColorFilter(md.fruitColor)
        day.addView(ripe2)
    }

    info.addView(day)
    info.addView(months)
    return info
}
