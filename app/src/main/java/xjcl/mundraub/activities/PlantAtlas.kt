package xjcl.mundraub.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Headers
import kotlinx.android.synthetic.main.activity_plant_atlas.*
import kotlinx.android.synthetic.main.activity_plant_list_item.view.*
import xjcl.mundraub.R
import xjcl.mundraub.data.ActivityRequest
import xjcl.mundraub.data.treeIdToFrequency
import xjcl.mundraub.data.treeIdToMarkerIcon


class CPCardInfo(val tid: Int, var submitted: Boolean)

class CPRVAdapter(val cardInfos: List<CPCardInfo>) : RecyclerView.Adapter<CPRVAdapter.ViewHolder>() {
    class ViewHolder (val v: View) : RecyclerView.ViewHolder(v)

    // card created
    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): ViewHolder {
        val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.activity_plant_list_item, viewGroup, false)
        return ViewHolder(v)
    }

    // card scrolls into view or data changed
    override fun onBindViewHolder(viewHolder: ViewHolder, i: Int) {
        val tid = cardInfos[i].tid
        val submitted = cardInfos[i].submitted
        Log.e("submitted", " i=$i: tid=$tid submitted=$submitted")
        viewHolder.v.apply {
            val title = context.getString(context.resources.getIdentifier("tid$tid", "string", context.packageName))
            card_title.text = title
            card_subtitle.text = "${treeIdToFrequency[tid]} ${context.getString(R.string.markers)}"
            card_subtitle2.text = context.getString(if (submitted) R.string.submitted else R.string.not_submitted)
            card_marker.setImageResource( treeIdToMarkerIcon[tid] ?: R.drawable.icon_otherfruit)
            cv.setCardBackgroundColor(if (submitted) resources.getColor(R.color.colorPrimary) else Color.WHITE)
            setOnClickListener {
                if (treeIdToProfileUrl[tid] != null)  // "other" fruit/shrub/nut/herb
                    (context as Activity).startActivity(Intent(context, PlantProfile::class.java).putExtra("tid", tid))
            }
        }
    }

    override fun getItemCount(): Int = cardInfos.size
}


class PlantAtlas : AppCompatActivity() {
    private val cardInfos = ArrayList<CPCardInfo>()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ActivityRequest.Login.value && resultCode == Activity.RESULT_OK) recreate()
        if (requestCode == ActivityRequest.Login.value && resultCode != Activity.RESULT_OK) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plant_atlas)

        doWithLoginCookie(this, loginIfMissing = true, callback = { doCreate() })
    }

    private fun doCreate() {
        val sharedPref = this.getSharedPreferences("global", Context.MODE_PRIVATE)
        val cookie = sharedPref.getString("cookie", null) ?: return finish()

        recycler_view.layoutManager = LinearLayoutManager(this)
        recycler_view.adapter = CPRVAdapter(cardInfos)

        for (el in treeIdToFrequency.toList().sortedBy { -it.second })  // Pair<tid, frequency>
            cardInfos.add(CPCardInfo(el.first, false))

        Fuel.get("https://mundraub.org/user").header(Headers.COOKIE to cookie).allowRedirects(false).responseString { request, response, result ->

            when (response.statusCode) {
                -1 -> return@responseString runOnUiThread { Toast.makeText(this, getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show(); finish() }
                302 -> {}
                else -> return@responseString runOnUiThread { Toast.makeText(this, getString(R.string.errMsgAccess), Toast.LENGTH_SHORT).show(); finish() }
            }

            Log.e("GET user to obtain uid", response.statusCode.toString())
            val uid = result.get().substringAfter("/user/").substringBefore("\"")
            val tidPresentMap = mutableSetOf<Int>()

            fun nextPage(i: Int = 0) {
                val url = "https://mundraub.org/user/$uid/plants?page=$i"
                Fuel.get(url).header(Headers.COOKIE to cookie).responseString { request, response, result ->
                    when (response.statusCode) {
                        -1 -> return@responseString runOnUiThread { Toast.makeText(this, getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show(); finish() }
                        200 -> {}
                        else -> return@responseString runOnUiThread { Toast.makeText(this, getString(R.string.errMsgAccess), Toast.LENGTH_SHORT).show(); finish() }
                    }

                    val newCardInfos = processHTMLToCardInfos(result.get(), this)
                    newCardInfos.forEach { tidPresentMap.add(it.tid) }

                    cardInfos.filter { tidPresentMap.contains(it.tid) }.forEach { it.submitted = true }
                    runOnUiThread { recycler_view.adapter?.notifyDataSetChanged() }

                    if (newCardInfos.isNotEmpty()) nextPage(i + 1)
                }
            }
            nextPage()
        }
    }
}
