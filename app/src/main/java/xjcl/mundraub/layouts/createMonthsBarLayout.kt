package xjcl.mundraub.layouts

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import xjcl.mundraub.R
import xjcl.mundraub.data.*
import xjcl.mundraub.utils.bitmapWithText

/**
 *  create a Layout of colored circles representing the months when a fruit is ripe:
 *                       v----------- current time of year
 *      - - - o o o - - -|- - -
 *      J F M A M J J A S O N D  <--- only if withLetters is true
 */
fun createMonthsBarLayout(context: Context, md : MarkerData, withLetters : Boolean = true): LinearLayout {
    val months = LinearLayout(context)
    months.orientation = LinearLayout.HORIZONTAL
    months.gravity = Gravity.CENTER
    for (i in 1..12) {
        val circle = LinearLayout(context)
        circle.orientation = LinearLayout.HORIZONTAL

        val circleLeft = ImageView(context)
        val circleRight = ImageView(context)

        val resLeft = if ("xl".contains(md.monthCodes[i - 1])) R.drawable._dot_l1 else R.drawable._dot_l0
        val resRight = if ("xr".contains(md.monthCodes[i - 1])) R.drawable._dot_r1 else R.drawable._dot_r0
        circleLeft.setImageResource(resLeft)
        circleRight.setImageResource(resRight)
        if ("xl".contains(md.monthCodes[i - 1])) circleLeft.setColorFilter(md.fruitColor)
        if ("xr".contains(md.monthCodes[i - 1])) circleRight.setColorFilter(md.fruitColor)
        // add vertical line for current time in year
        if (md.curMonth.toInt() == i)
            (if (md.curMonth % 1 < .5) circleLeft else circleRight).setImageBitmap(
                bitmapWithText((if (md.curMonth % 1 < .5) resLeft else resRight), context, "|", 50F, false,
                    2 * (md.curMonth % .5).toFloat(), if (md.isSeasonal) md.fruitColor else Color.GRAY)
            )

        circle.addView(circleLeft)
        circle.addView(circleRight)

        val letter = TextView(context)
        letter.setTextColor(if (md.monthCodes[i - 1] != '_') md.fruitColor else Color.GRAY)
        letter.gravity = Gravity.CENTER
        if (md.monthCodes[i - 1] != '_') letter.setTypeface(null, Typeface.BOLD)
        letter.text = "JFMAMJJASOND"[i - 1].toString()

        val month = LinearLayout(context)
        month.orientation = LinearLayout.VERTICAL
        month.addView(circle)
        if (withLetters) month.addView(letter)
        months.addView(month)
    }
    return months
}

fun createMonthsBarLayout(context: Context, tid : Int): LinearLayout {
    if (!treeIdToSeason.contains(tid)) return LinearLayout(context)
    val fakeFeature = Feature(listOf(), Properties(0, tid), null)
    return createMonthsBarLayout(context, markerDataManager.featureToMarkerData(context, fakeFeature), false)
}
