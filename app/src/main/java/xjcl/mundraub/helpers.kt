package xjcl.mundraub

import android.app.Activity
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import com.google.android.gms.maps.model.LatLng

// Helper function as adding text to a bitmap needs more code than one might expect
fun bitmapWithText(resource: Int, activity: Activity, text: String, textSize_: Float, outline: Boolean = true, xpos: Float = .5F, color_: Int = Color.WHITE) : Bitmap {
    val bitmap = BitmapFactory.decodeResource(activity.resources, resource, BitmapFactory.Options().apply { inMutable = true })
    val canvas = Canvas(bitmap)
    val textBounds = Rect()

    val paint = Paint().apply {
        textSize = textSize_ * activity.resources.displayMetrics.density / 3F; getTextBounds(text, 0, text.length, textBounds); color = color_ }

    // https://stackoverflow.com/a/9133305/2111778
    if (outline) {
        val stkPaint = Paint().apply {
            style = Paint.Style.STROKE; strokeWidth = 2F * activity.resources.displayMetrics.density; textSize = paint.textSize; color = Color.DKGRAY }
        canvas.drawText(text, canvas.width * xpos - textBounds.exactCenterX(), canvas.height/2F - textBounds.exactCenterY(), stkPaint)
    }

    canvas.drawText(text, canvas.width * xpos - textBounds.exactCenterX(), canvas.height/2F - textBounds.exactCenterY(), paint)

    return bitmap
}

fun scaleToWidth(bitmapMaybeNull : Bitmap?, width : Int) : Bitmap {
    val bitmap = bitmapMaybeNull ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8)
    return Bitmap.createScaledBitmap(bitmap, width, (width.toDouble() / bitmap.width * bitmap.height).toInt(), true)
}

fun materialDesignBg(padX: Int, padY: Int, c: Float): Drawable {
    return ShapeDrawable(RoundRectShape(floatArrayOf(c, c, c, c, c, c, c, c), null, null)).apply {
        paint.color = Color.parseColor("#FFFFFF")
        setPadding(padX, padY, padX, padY)
    }
}

fun getFruitColor(resources : Resources, tid: Int?) : Int =
    BitmapFactory.decodeResource(resources, treeIdToMarkerIcon[tid] ?: R.drawable.otherfruit)
        .getPixel(resources.displayMetrics.density.toInt() * 3, resources.displayMetrics.density.toInt() * 10)

fun vecMul(scalar : Double, vec : LatLng) : LatLng = LatLng(scalar * vec.latitude, scalar * vec.longitude)
fun vecAdd(vec1 : LatLng, vec2 : LatLng) : LatLng = LatLng(vec1.latitude + vec2.latitude, vec1.longitude + vec2.longitude)
fun vecSub(vec1 : LatLng, vec2 : LatLng) : LatLng = LatLng(vec1.latitude - vec2.latitude, vec1.longitude - vec2.longitude)
