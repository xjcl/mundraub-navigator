package xjcl.mundraub

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import com.github.kittinunf.fuel.Fuel
import kotlinx.android.synthetic.main.activity_register.*

class Register : AppCompatActivity() {
    val registerData = mutableMapOf(
        "form_id" to "user_register_form",
        "accept_rules" to "1",
        "op" to "Neues Benutzerkonto erstellen",
        "country" to ""
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        Fuel.get("https://mundraub.org/user/register").allowRedirects(false).responseString { request, response, result ->

            when (response.statusCode) {
                -1 -> {runOnUiThread { Toast.makeText(this@Register, getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show() }; finish()}
                200 -> {}
                else -> {runOnUiThread { Toast.makeText(this@Register, getString(R.string.errMsgAccess), Toast.LENGTH_SHORT).show() }; finish()}
            }

            registerData["honeypot_time"] = result.get().substringAfter("name=\"honeypot_time\" value=\"").substringBefore("\"")
        }
    }

    fun onRegisterClick(view : View) {
        registerData["mail"] = this.reg_email_inner.text.toString()
        registerData["name"] = this.reg_user_inner.text.toString()
        if (registerData["mail"].isNullOrBlank() || registerData["name"].isNullOrBlank())
            {runOnUiThread { Toast.makeText(this, getString(R.string.errMsgRegisterInfo), Toast.LENGTH_SHORT).show() }; return}

        Fuel.post("https://mundraub.org/user/register", registerData.toList()).allowRedirects(false).responseString { request, response, result ->
            Log.e("register", result.get())
            when (response.statusCode) {
                -1 -> {runOnUiThread { Toast.makeText(this, getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show() }; return@responseString}
                303 -> {}
                else -> {runOnUiThread { Toast.makeText(this, getString(R.string.errMsgRegister), Toast.LENGTH_SHORT).show() }; return@responseString}
            }

            runOnUiThread {
                Toast.makeText(this, getString(R.string.errMsgRegisterSuccess), Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}
