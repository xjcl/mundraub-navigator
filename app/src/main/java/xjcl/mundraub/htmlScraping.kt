package xjcl.mundraub

import android.util.Log
import androidx.core.text.HtmlCompat

fun scrapeFormToken(s: String) : String =
    s.substringAfter("""form_token" value="""", "(missing)").substringBefore("\"")

fun scrapeErrorMessage(s: String) : String {
    val tmp = s.substringAfter("Fehlermeldung</h2>").substringBefore("</div>")
        .substringAfter("<ul>").substringBefore("</ul>")
    Log.e("scrapeErrorMessage", tmp)
    return tmp.split("</li>").filter { it.isNotBlank() }.map { it.trim() }.map { ">$it<" }
        .map { Regex(">.*?<").findAll(it).map { it.value.substring(1 until it.value.length - 1) }.joinToString(" ") }
        .joinToString("\n\n")
}

fun unescapeHtml(s : String) = HtmlCompat.fromHtml(s, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()  // unescape "&quot;" etc
