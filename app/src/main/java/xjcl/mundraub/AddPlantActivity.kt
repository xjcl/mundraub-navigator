package xjcl.mundraub

import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.core.view.setPadding
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import khttp.async.Companion.get
import khttp.async.Companion.post

class AddPlantActivity : AppCompatActivity() {

    // TODO: MapFragment with preview of window :D
    //      this will let users know not to write too much text
    //      MF should NOT respond to touches
    // TODO: use filter bar for species selection?
    // TODO: handle errors
    // TODO: handle status codes

    val loginData = mutableMapOf(
        "form_id" to "user_login_form",
        "op" to "Anmelden"
    )

    val plantData = mutableMapOf(
        "changed" to "0",
        "form_build_id" to "",
        "form_id" to "node_plant_form",
        "field_plant_category" to "4",  // USER
        "field_position[0][value]" to "POINT(0 0)",  // USER
        "body[0][value]" to "TEST FOR 'MUNDRAUB NAVIGATOR'. WILL DELETE",  // USER
        "body[0][format]" to "simple_text",
        "field_plant_image[0][_weight]" to "0",
        "field_plant_image[0][fids]" to "",
        "field_plant_image[0][display]" to "1",
        "address-search" to "",
        "field_plant_address[0][value]" to "Null Island!",  // USER
        "accept_rules" to "1",  // (USER)
        "advanced__active_tab" to "",
        "op" to "Speichern"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val lin = LinearLayout(this)
        lin.orientation = LinearLayout.VERTICAL
        lin.setPadding(24)

        // TODO save user and password
        TextInputLayout.inflate(this, R.layout.text_input_layout, lin)
        val userTIL = lin.children.last() as TextInputLayout
        userTIL.hint = "Benutzername"

        // TODO hide
        TextInputLayout.inflate(this, R.layout.text_input_layout, lin)
        val passTIL = lin.children.last() as TextInputLayout
        passTIL.hint = "Passwort"

        TextInputLayout.inflate(this, R.layout.text_input_autocomplete, lin)
        val typeTIL = lin.children.last() as TextInputLayout
        typeTIL.hint = "Fruchtfund"
        val items = listOf("4", "5", "6", "7") // TODO
        val adapter = ArrayAdapter(this, R.layout.list_item, items)
        val textField = typeTIL.findViewById<AutoCompleteTextView>(R.id.auto_text)
        textField.setAdapter(adapter)

        LayoutInflater.from(this).inflate(R.layout.chip_group_number, lin)
        val chipGroup = (lin.children.last() as LinearLayout).children.last() as ChipGroup

        TextInputLayout.inflate(this, R.layout.text_input_layout, lin)
        val descriptionTIL = lin.children.last() as TextInputLayout
        descriptionTIL.hint = "Beschreibung"
        val edit = descriptionTIL.findViewById<TextInputEditText>(R.id.edit_text)
        edit.inputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_CLASS_TEXT
        edit.minLines = 3
        edit.maxLines = 3
        lin.children.forEach { Log.e("ch", it.toString()) }

        val btn = Button(this)
        btn.text = "Add!"
        btn.setOnClickListener {

            Log.e("pos", "2")
//            fun String.utf8(): String = java.net.URLEncoder.encode(this, "UTF-8")
//            parameters.map {(k, v) -> "${k.utf8()}=${v.utf8()}"}.joinToString("&")
            loginData["name"] = userTIL.editText!!.text.toString()
            loginData["pass"] = passTIL.editText!!.text.toString()

            post("https://mundraub.org/user/login", data=loginData, allowRedirects=false) {
                val r0 = this
                Log.e("ResponseLogin0", r0.text)

                get("https://mundraub.org/node/add/plant", cookies=r0.cookies) {
                    plantData["form_token"] = this.text.substringAfter("""form_token" value="""", "(missing)").substringBefore("\"")
                    plantData["body[0][value]"] = descriptionTIL.editText!!.text.toString()
                    plantData["field_plant_category"] = typeTIL.editText!!.text.toString()
                    plantData["field_plant_count_trees"] = mapOf(
                        R.id.chip_0 to "0",
                        R.id.chip_1 to "1",
                        R.id.chip_2 to "2",
                        R.id.chip_3 to "3"
                    )[chipGroup.checkedChipId] ?: "0"

                    Log.e("plantData", plantData.toString())

                    post("https://mundraub.org/node/add/plant", data=plantData, cookies=r0.cookies, allowRedirects=false) {
                        Log.e("ResponseLogin2", this.text)
                    }
                }
            }
        }
        lin.addView(btn)

        val scroll = ScrollView(this)
        scroll.addView(lin)

        setContentView(scroll)
    }
}