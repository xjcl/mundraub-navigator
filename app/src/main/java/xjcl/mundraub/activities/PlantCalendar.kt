package xjcl.mundraub.activities

import android.graphics.Typeface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.TextView
import xjcl.mundraub.R
import xjcl.mundraub.data.treeIdToSeason
import xjcl.mundraub.databinding.ActivityPlantCalendarBinding
import xjcl.mundraub.layouts.createMonthsBarLayout
import xjcl.mundraub.utils.getFruitColor

class PlantCalendar : AppCompatActivity() {
    private lateinit var binding: ActivityPlantCalendarBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlantCalendarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.plant_calendar)

        val density = resources.displayMetrics.density

        // Sort by season and populate the container
        treeIdToSeason.keys.sortedBy { treeIdToSeason[it]!!.first * 20 + treeIdToSeason[it]!!.second }
            .forEachIndexed { i, tid ->
                val tv = TextView(this).apply {
                    text = getString(resources.getIdentifier("tid$tid", "string", packageName))
                    setTextColor(getFruitColor(resources, tid))
                    setTypeface(null, Typeface.BOLD)
                    ellipsize = TextUtils.TruncateAt.END
                    isSingleLine = true
                    minWidth = (110 * density).toInt()
                    maxWidth = (110 * density).toInt()
                }

                val mb = createMonthsBarLayout(this, tid, withLetters = i % 5 == 4)

                val line = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    addView(tv)
                    addView(mb)
                }
                
                binding.calendarContainer.addView(line)
            }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
