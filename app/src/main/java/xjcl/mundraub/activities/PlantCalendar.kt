package xjcl.mundraub.activities

import android.graphics.Typeface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import xjcl.mundraub.data.treeIdToSeason

import xjcl.mundraub.layouts.createMonthsBarLayout
import xjcl.mundraub.utils.getFruitColor

class PlantCalendar : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density

        val scrollpage = ScrollView(this)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(0, (20 * density).toInt(), 0, (20 * density).toInt())

        //for ((i, tid) in treeIdToSeason.keys.withIndex())
        for ((i, tid) in treeIdToSeason.keys.sortedBy { treeIdToSeason[it]!!.first * 20 + treeIdToSeason[it]!!.second }.withIndex())
        {
            val tv = TextView(this)
            tv.text = getString(resources.getIdentifier("tid$tid", "string", packageName))
            tv.setTextColor(getFruitColor(resources, tid))
            tv.setTypeface(null, Typeface.BOLD)
            tv.ellipsize = TextUtils.TruncateAt.END
            tv.setSingleLine()
            tv.minWidth = (110 * density).toInt()
            tv.maxWidth = (110 * density).toInt()

            val mb = createMonthsBarLayout(this, tid, withLetters = i % 5 == 4)

            val line = LinearLayout(this)
            line.addView(tv)
            line.addView(mb)
            line.gravity = Gravity.CENTER_HORIZONTAL
            page.addView(line)
        }

        scrollpage.addView(page)
        setContentView(scrollpage)
    }
}
