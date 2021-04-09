package xjcl.mundraub.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import xjcl.mundraub.R

fun Any?.discard() = Unit

fun <K, V> Map<K, V>.getInverse(value: V) = entries.firstOrNull { it.value == value }?.key

fun tryStartActivity(context: Context, uri: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.errMsgOpenIntent, Toast.LENGTH_SHORT).show()
    }
}
