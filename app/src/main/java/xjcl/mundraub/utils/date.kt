package xjcl.mundraub.utils

import xjcl.mundraub.data.treeIdToSeason
import java.util.*

fun getCurMonth(): Double =
    Calendar.getInstance().get(Calendar.MONTH) + 1 + Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toDouble() / 32

fun isSeasonal(tid : Int?, month : Double) =
    treeIdToSeason[tid]?.first ?: 0.0 <= month && month <= treeIdToSeason[tid]?.second ?: 0.0
