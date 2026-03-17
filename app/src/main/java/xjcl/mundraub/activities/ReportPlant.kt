package xjcl.mundraub.activities

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Headers
import xjcl.mundraub.R
import xjcl.mundraub.databinding.ActivityReportPlantBinding
import xjcl.mundraub.utils.scrapeFormToken

class ReportPlant : AppCompatActivity() {
    private lateinit var binding: ActivityReportPlantBinding

    private var intentNid : Int = -1
    private lateinit var cookie : String
    private lateinit var username : String

    val reportData = mutableMapOf(
        "op" to "Absenden",
        "form_id" to "report_node_form"
    )

    val radioMap = mapOf(R.id.report_r0 to "0", R.id.report_r1 to "1", R.id.report_r2 to "2", R.id.report_r3 to "3", R.id.report_r4 to "4")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportPlantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_activity_report_plant)

        intentNid = intent.getIntExtra("nid", -1)

        doWithLoginCookie(this, loginIfMissing = true, callback = { doCreate() })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun doCreate() {
        val sharedPref = this.getSharedPreferences("global", Context.MODE_PRIVATE)
        cookie = sharedPref.getString("cookie", null) ?: return finish()
        username = sharedPref.getString("name", null) ?: return finish()
    }

    fun reportPlantActual(view : View) {
        if (binding.reportTextInner.text.toString().isBlank()) return

        val url = "https://mundraub.org/node/$intentNid?destination=/map?nid=$intentNid"
        Fuel.get(url).header(Headers.COOKIE to cookie).responseString { request, response, result ->

            when (response.statusCode) {
                -1 -> return@responseString runOnUiThread { Toast.makeText(this, getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show() }
                200 -> {}
                else -> return@responseString runOnUiThread { Toast.makeText(this, getString(R.string.errMsgAccess), Toast.LENGTH_SHORT).show() }
            }

            reportData["form_token"] = scrapeFormToken(result.get().substringAfter("edit-report-node-form-form-token"))
            reportData["report_type"] = radioMap[binding.reportRadioGroup.checkedRadioButtonId] ?: "4"
            reportData["description"] = binding.reportTextInner.text.toString()

            Log.e("reportPlant", intentNid.toString())
            Log.e("reportPlant", result.get())
            Log.e("reportPlant", reportData.toString())
            Fuel.post(url, reportData.toList()).header(Headers.COOKIE to cookie).allowRedirects(false).responseString { request, response, result ->

                when (response.statusCode) {
                    -1 -> return@responseString runOnUiThread { Toast.makeText(this@ReportPlant, getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show() }
                    303 -> {}
                    else -> return@responseString runOnUiThread { Toast.makeText(this@ReportPlant, getString(R.string.errMsgOpFail), Toast.LENGTH_SHORT).show() }
                }

                runOnUiThread {
                    Toast.makeText(this@ReportPlant, getString(R.string.errMsgReportSuccess), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    fun reportPlant(view: View) {
        val url = "https://mundraub.org/node/$intentNid"
        val reason = getString(resources.getIdentifier("report${radioMap[binding.reportRadioGroup.checkedRadioButtonId] ?: "4"}", "string", packageName))

        val emailIntent = Intent(Intent.ACTION_SEND)
        emailIntent.type = "message/rfc822"
        emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf("info@mundraub.org"))
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, """"Fundort melden" via Mundraub Navigator""")
        emailIntent.putExtra(Intent.EXTRA_TEXT, """
            Liebes mundraub-Team,
            
            ich würde gerne den folgenden Marker melden:
            - URL: $url
            - Grund: $reason
            - Freitext: ${binding.reportTextInner.text}
            
            Fruchtige Grüße,
            Nutzer $username
        """.trimIndent())

        startActivity(Intent.createChooser(emailIntent, "Send mail..."))
    }
}
