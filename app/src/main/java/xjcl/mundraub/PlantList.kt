package xjcl.mundraub

import android.app.Activity
import android.app.AlertDialog
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
import java.util.*
import kotlin.collections.ArrayList


class CardInfo(val nid: Int, val tid: Int, val addr: String, val type: String)

class RVAdapter (val cardInfos: List<CardInfo>) : RecyclerView.Adapter<RVAdapter.PersonViewHolder>() {
    class PersonViewHolder (val v: View) : RecyclerView.ViewHolder(v)

    // card created
    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): PersonViewHolder {
        val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.activity_plant_list_item, viewGroup, false)
        return PersonViewHolder(v)
    }

    // card scrolls into view or data changed
    override fun onBindViewHolder(personViewHolder: PersonViewHolder, i: Int) {
        personViewHolder.v.apply {
            card_title.text = cardInfos[i].type
            card_subtitle.text = cardInfos[i].addr
            card_marker.setImageResource( treeIdToMarkerIcon[cardInfos[i].tid] ?: R.drawable.otherfruit )
            setOnClickListener {
                (context as Activity).startActivityForResult(Intent(context, PlantForm::class.java).putExtra("nid", cardInfos[i].nid), 33)
            }
        }
    }

    override fun getItemCount(): Int = cardInfos.size
}


class PlantList : AppCompatActivity() {
    // I hate this code but Android
    private fun setGermanStringsToTreeId() {
        val conf = resources.configuration
        val localeBackup: Locale = conf.locale
        conf.locale = Locale.GERMAN
        resources.updateConfiguration(conf, null) // second arg null means don't change

        val values = treeIdToMarkerIcon.keys.toList()
        val keys = values.map { key -> resources.getString(resources.getIdentifier("tid${key}", "string", packageName)) }

        conf.locale = localeBackup
        resources.updateConfiguration(conf, null)

        germanStringsToTreeId = keys.zip(values).toMap()
    }

    private lateinit var germanStringsToTreeId : Map<String, Int>
    private lateinit var cookie : String
    private lateinit var uid : String
    private var pagesLoaded = 0
    private var allPagesLoaded = false
    private val cardInfos = ArrayList<CardInfo>()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.e("onActivityResult", "onActivityResult")
        // TODO change just one item, do not recreate whole activity
        if (requestCode == 33 && resultCode == Activity.RESULT_OK) recreate()
        if (requestCode == 55) {
            if (hasLoginCookie(this)) doCreate() else finish()
        }
    }

    private fun logout() {
        val sharedPref = this.getSharedPreferences("global", Context.MODE_PRIVATE)
        with (sharedPref.edit()) {
            remove("cookie")
            remove("name")
            remove("pass")
            apply()
        }
        recreate()
    }

    private fun logoutDialog() {
        AlertDialog.Builder(this).setMessage(R.string.reallyLogout)
            .setPositiveButton(R.string.yes) { _, _ -> logout() }
            .setNegativeButton(R.string.no) { _, _ -> }
            .create().show()
    }

    // Handle ActionBar option selection
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            8 -> { logoutDialog(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Create the ActionBar options menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(8, 8, 8, "Logout").setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plant_list)
        setGermanStringsToTreeId()

        if (hasLoginCookie(this, loginIfMissing = true))
            doCreate()
    }

    private fun loadNextPage() {
        if (allPagesLoaded || !::uid.isInitialized) return
        val url = "https://mundraub.org/user/$uid/plants?page=$pagesLoaded"  // Pear
        Log.e("loadNextPage", url)
        Fuel.get(url).header(Headers.COOKIE to cookie).responseString { request, response, result ->

            when (response.statusCode ) {
                -1 -> return@responseString runOnUiThread { Toast.makeText(this, getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show() }
                200 -> {}
                else -> return@responseString runOnUiThread { Toast.makeText(this, getString(R.string.errMsgAccess), Toast.LENGTH_SHORT).show() }
            }
            pagesLoaded += 1

            // TODO icon for finds
            var unprocessed = result.get()
            var i = -1
            while (i++ >= -1) {
                unprocessed = unprocessed.substringAfter("nid=", "")
                if (unprocessed.isBlank()) break

                val nid = unprocessed.substringBefore("\"").toInt()
                val type = unprocessed.substringAfter("boxlink\">").substringBefore("<")
                val addrRaw = unprocessed.substringAfter("</a>").substringBefore("</div></div>")

                val matches = ">.*?<".toRegex().findAll(addrRaw)
                val addr = matches.map { it.value.substring(1, it.value.length - 1) }.joinToString(" ").let { unescapeHtml(it) }
                Log.e("pl***", " $type **** $addr")

                val tid = germanStringsToTreeId[type] ?: 12

                val card = CardInfo(nid, tid, addr, resources.getString(resources.getIdentifier("tid${tid}", "string", packageName)))
                cardInfos.add(card)
            }
            allPagesLoaded = i == 0
            if (i > 0)
                runOnUiThread { recycler_view.adapter?.notifyItemRangeInserted(cardInfos.size - i, i)
                    recycler_info.visibility = View.GONE; recycler_info_divider.visibility = View.GONE }
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

        Fuel.get("https://mundraub.org/user").header(Headers.COOKIE to cookie).allowRedirects(false).responseString { request, response, result ->
            // TODO store this in sharedPrefs object (after login, and here for compat reasons)
            uid = result.get().substringAfter("/user/").substringBefore("\"")
            loadNextPage()
        }
    }
}

// TODO really should use svg or at least xxxhdpi instead of xhdpi for this
