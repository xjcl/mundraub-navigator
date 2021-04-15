package xjcl.mundraub.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Headers
import kotlinx.android.synthetic.main.activity_plant_list.*
import kotlinx.android.synthetic.main.activity_plant_list_item.view.*
import xjcl.mundraub.R
import xjcl.mundraub.data.ActivityRequest
import xjcl.mundraub.data.germanStringsToTreeId
import xjcl.mundraub.data.treeIdToMarkerIcon
import xjcl.mundraub.utils.unescapeHtml
import java.util.*
import kotlin.collections.ArrayList



fun processHTMLToCardInfos(html: String, activity: Activity) : List<CardInfo> {
    var unprocessed = html
    var i = -1
    val newCardInfos = mutableListOf<CardInfo>()
    while (i++ >= -1) {
        unprocessed = unprocessed.substringAfter("nid=", "")
        if (unprocessed.isBlank()) break

        val nid = unprocessed.substringBefore("\"").toInt()
        val type = unprocessed.substringAfter("boxlink\">").substringBefore("<")
        val addrRaw = unprocessed.substringAfter("</a>").substringBefore("</div></div>")

        val matches = ">.*?<".toRegex().findAll(addrRaw)
        val addr = matches.map { it.value.substring(1, it.value.length - 1) }.joinToString(" ").let { unescapeHtml(it) }
        Log.e("loadNextPage", " $type **** $addr")

        val tid = germanStringsToTreeId[type] ?: 12

        val card = CardInfo(nid, tid, addr, activity.resources.getString(activity.resources.getIdentifier("tid${tid}", "string", activity.packageName)))
        newCardInfos.add(card)
    }
    return newCardInfos
}



class CardInfo(val nid: Int, val tid: Int, val addr: String, val type: String)

private class RVAdapter(val cardInfos: List<CardInfo>) : RecyclerView.Adapter<RVAdapter.ViewHolder>() {
    class ViewHolder (val v: View) : RecyclerView.ViewHolder(v)

    // card created
    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): ViewHolder {
        val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.activity_plant_list_item, viewGroup, false)
        return ViewHolder(v)
    }

    // card scrolls into view or data changed
    override fun onBindViewHolder(viewHolder: ViewHolder, i: Int) {
        viewHolder.v.apply {
            card_title.text = cardInfos[i].type
            card_subtitle.text = cardInfos[i].addr
            card_marker.setImageResource( treeIdToMarkerIcon[cardInfos[i].tid] ?: R.drawable.icon_otherfruit)
            setOnClickListener {
                (context as Activity).startActivityForResult(Intent(context, PlantForm::class.java).putExtra("nid", cardInfos[i].nid), ActivityRequest.PlantForm.value)
            }
        }
    }

    override fun getItemCount(): Int = cardInfos.size
}


class PlantList : AppCompatActivity() {
    private lateinit var cookie : String
    private lateinit var uid : String
    private var pagesLoaded = 0
    private var allPagesLoaded = false
    private val cardInfos = ArrayList<CardInfo>()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.e("onActivityResult", "onActivityResult ${requestCode} ${resultCode}")
        // TODO change just one item, do not recreate whole activity
        if (requestCode == ActivityRequest.Login.value && resultCode == Activity.RESULT_OK) recreate()
        if (requestCode == ActivityRequest.Login.value && resultCode != Activity.RESULT_OK) finish()
        if (requestCode == ActivityRequest.PlantForm.value && resultCode == Activity.RESULT_OK) {
            setResult(Activity.RESULT_OK, data)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plant_list)

        doWithLoginCookie(this, loginIfMissing = true, callback = { doCreate() })
    }

    private fun loadNextPage() {
        if (allPagesLoaded || !::uid.isInitialized) return
        val url = "https://mundraub.org/user/$uid/plants?page=$pagesLoaded"
        Log.e("loadNextPage", url)
        Fuel.get(url).header(Headers.COOKIE to cookie).responseString { request, response, result ->

            when (response.statusCode) {
                -1 -> return@responseString runOnUiThread { Toast.makeText(this, getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show() }
                200 -> {}
                else -> return@responseString runOnUiThread { Toast.makeText(this, getString(R.string.errMsgAccess), Toast.LENGTH_SHORT).show() }
            }
            pagesLoaded += 1

            val newCardInfos = processHTMLToCardInfos(result.get(), this)
            cardInfos.addAll(newCardInfos)

            allPagesLoaded = newCardInfos.isEmpty()
            if (!allPagesLoaded)
                runOnUiThread {
                    recycler_view.adapter?.notifyItemRangeInserted(cardInfos.size - newCardInfos.size, newCardInfos.size)
                    recycler_info.visibility = View.GONE
                    recycler_info_divider.visibility = View.GONE
                }
            if (allPagesLoaded && pagesLoaded == 1)
                runOnUiThread { recycler_info.text = getString(R.string.empty_info) }
        }
    }

    private fun doCreate() {
        val sharedPref = this.getSharedPreferences("global", Context.MODE_PRIVATE)
        cookie = sharedPref.getString("cookie", null) ?: return finish()

        recycler_view.layoutManager = LinearLayoutManager(this)
        recycler_view.adapter = RVAdapter(cardInfos)
        recycler_view.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE)
                    loadNextPage()
            }
        })

        Log.e("doCreate", "req")
        Fuel.get("https://mundraub.org/user").header(Headers.COOKIE to cookie).allowRedirects(false).responseString { request, response, result ->
            // TODO store this in sharedPrefs object (after login, and here for compat reasons)

            when (response.statusCode) {
                -1 -> return@responseString runOnUiThread { Toast.makeText(this, getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show(); finish() }
                302 -> {}
                else -> return@responseString runOnUiThread { Toast.makeText(this, getString(R.string.errMsgAccess), Toast.LENGTH_SHORT).show(); finish() }
            }

            Log.e("doCreate", response.statusCode.toString())
            uid = result.get().substringAfter("/user/").substringBefore("\"")
            loadNextPage()
        }
    }
}

// TODO really should use svg or at least xxxhdpi instead of xhdpi for this
