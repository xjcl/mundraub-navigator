package xjcl.mundraub.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import xjcl.mundraub.R
import androidx.core.net.toUri

fun <K, V> Map<K, V>.getInverse(value: V) = entries.firstOrNull { it.value == value }?.key

fun Context.tryStartActivity(uri: String) {
    try {
        this.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.errMsgOpenIntent, Toast.LENGTH_SHORT).show()
    }
}
