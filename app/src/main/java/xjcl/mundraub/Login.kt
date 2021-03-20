package xjcl.mundraub

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.kittinunf.fuel.Fuel
import kotlinx.android.synthetic.main.activity_login.*


class Login : AppCompatActivity() {

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.e("Login", "onActivityResult")
        loadCredsIntoFields()
    }

    // migrate the user's old settings (new in v13) -- can delete this a few weeks out
    private fun migrateSettings() {
        val sharedPref = this.getSharedPreferences("global", Context.MODE_PRIVATE)
        val sharedPrefOld = this.getSharedPreferences("AddPlantActivity", Context.MODE_PRIVATE)
        val newName = sharedPref.getString("name", "") ?: ""
        val newPass = sharedPref.getString("pass", "") ?: ""
        val oldName = sharedPrefOld.getString("name", newName) ?: newName
        val oldPass = sharedPrefOld.getString("pass", newPass) ?: newPass
        with (sharedPref.edit()) {
            putString("name", oldName)
            putString("pass", oldPass)
            apply()
        }
        with (sharedPrefOld.edit()) {
            remove("name")
            remove("pass")
            apply()
        }
    }

    private fun loadCredsIntoFields() {
        migrateSettings()

        val sharedPref = this.getSharedPreferences("global", Context.MODE_PRIVATE)
        this.name_inner.setText( sharedPref.getString("name", "") ?: "" )
        this.pass_inner.setText( sharedPref.getString("pass", "") ?: "" )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        loadCredsIntoFields()
    }

    fun onLoginClick(view : View) {
        if (this.name_inner.text.toString().isBlank() || this.pass_inner.text.toString().isBlank())
            return Toast.makeText(this, this.getString(R.string.errMsgLoginInfo), Toast.LENGTH_SHORT).show()

        doLogin(this, {}, this.name_inner.text.toString(), this.pass_inner.text.toString(), inLoginActivity = true)
    }

    fun onRegisterClick(view : View) {
        startActivityForResult(Intent(this, Register::class.java), ActivityRequest.Register.value)
    }
}

// --------------------------------

/**
 * Idea is to
 *  (a) (inLoginActivity=true) Provide functionality for Login form
 *          -> As stated below, put callback for when the cookie arrives in onActivityResult
 *  (b) (inLoginActivity=false) Secretly re-log in the background when the log-in cookie expires
 *          I.e. this function is called in ANOTHER activity's UI thread
 */
fun doLogin(activity: Activity, callback : () -> Unit, name : String? = null, pass : String? = null, inLoginActivity : Boolean = false) {
    Log.e("cook", "doLogin")
    val loginData = mutableMapOf(
        "form_id" to "user_login_form",
        "op" to "Anmelden"
    )

    val sharedPref = activity.getSharedPreferences("global", Context.MODE_PRIVATE)
    loginData["name"] = name ?: sharedPref.getString("name", "") ?: ""
    loginData["pass"] = pass ?: sharedPref.getString("pass", "") ?: ""
    Log.e("doLogin", loginData["name"] + "_" + loginData["pass"])
    if (loginData["name"]!!.isBlank() || loginData["pass"]!!.isBlank())
        return activity.startActivityForResult(Intent(activity, Login::class.java), ActivityRequest.Login.value)

    Fuel.post("https://mundraub.org/user/login", loginData.toList()).allowRedirects(false).responseString { request, response, result ->

        when (response.statusCode) {
            -1 -> return@responseString activity.runOnUiThread {
                Toast.makeText(activity, activity.getString(R.string.errMsgNoInternet), Toast.LENGTH_SHORT).show()
                if (!inLoginActivity) activity.finish()
            }
            303 -> {}
            else -> return@responseString activity.runOnUiThread {
                Toast.makeText(activity, activity.getString(R.string.errMsgLogin), Toast.LENGTH_SHORT).show()
                if (!inLoginActivity) activity.startActivityForResult(Intent(activity, Login::class.java), ActivityRequest.Login.value)
            }
        }

        val cook = response.headers["Set-Cookie"].first()
        Log.e("cook", "new cook $cook")

        with (sharedPref.edit()) {
            putString("name", loginData["name"])
            putString("pass", loginData["pass"])
            putString("cookie", cook)
            putLong("cookieTime", System.currentTimeMillis() / 1000L)
            apply()
        }

        Log.e("cook", "success with cookie $cook")
        activity.runOnUiThread {
            if (inLoginActivity) {
                Toast.makeText(activity, activity.getString(R.string.errMsgLoginSuccess), Toast.LENGTH_SHORT).show()
                activity.setResult(Activity.RESULT_OK, Intent())
                activity.finish()
            }
            callback()
        }
    }
}

/**
 * *** hasLoginCookie(true) ***
 * Only start activity if login cookie is present
 * If no cookie is present then start Login Activity instead
 *      1. First attempt re-login with the previous credentials
 *      2. If credentials do not work, wait for user to submit form
 *      -> Both will call onActivityResult so use hasLoginCookie(false) there to test for success
 */
fun doWithLoginCookie(activity : Activity, loginIfMissing : Boolean = false, callback : () -> Unit = {}) : Boolean {
    Log.e("cook", "hasLoginCookie ENTER")

    val sharedPref = activity.getSharedPreferences("global", Context.MODE_PRIVATE)
    val cook = sharedPref.getString("cookie", null)
    if (cook == null) {
        Log.e("cook", "no cook")
        if (loginIfMissing) doLogin(activity, callback)
        return false
    }

    val nowTime = System.currentTimeMillis() / 1000L
    val cookTime = sharedPref.getLong("cookieTime", 0)
    Log.e("cook", "trying cookie $cook from time $nowTime - $cookTime = ${nowTime - cookTime}")

    if (nowTime - cookTime > 140000) {  // Drupal cookies expire after 200k secs server-side
        Log.e("cook", "this cook is expired")
        if (loginIfMissing) doLogin(activity, callback)
        return false
    }

    Log.e("cook", "use cook")
    callback()
    return true
}
