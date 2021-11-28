package xjcl.mundraub.utils

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.text.Spanned
import androidx.core.text.HtmlCompat
import xjcl.mundraub.R
import xjcl.mundraub.data.treeIdToMarkerIcon


// Helper function as adding text to a bitmap needs more code than one might expect
fun bitmapWithText(resource: Int, context: Context, text: String, textSize_: Float, outline: Boolean = true, xpos: Float = .5F, color_: Int = Color.WHITE) : Bitmap {
    val bitmap = BitmapFactory.decodeResource(context.resources, resource, BitmapFactory.Options().apply { inMutable = true })
    val canvas = Canvas(bitmap)
    val textBounds = Rect()

    val paint = Paint().apply {
        textSize = textSize_ * context.resources.displayMetrics.density / 3F; getTextBounds(text, 0, text.length, textBounds); color = color_ }

    // https://stackoverflow.com/a/9133305/2111778
    if (outline) {
        val stkPaint = Paint().apply {
            style = Paint.Style.STROKE; strokeWidth = 2F * context.resources.displayMetrics.density; textSize = paint.textSize; color = Color.DKGRAY }
        canvas.drawText(text, canvas.width * xpos - textBounds.exactCenterX(), canvas.height/2F - textBounds.exactCenterY(), stkPaint)
    }

    canvas.drawText(text, canvas.width * xpos - textBounds.exactCenterX(), canvas.height/2F - textBounds.exactCenterY(), paint)

    return bitmap
}

fun scaleToHeight(bitmapMaybeNull : Bitmap?, height : Int) : Bitmap {
    val bitmap = bitmapMaybeNull ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8)
    return Bitmap.createScaledBitmap(bitmap, (height.toDouble() / bitmap.height * bitmap.width).toInt(), height, true)
}

fun materialDesignBg(padX: Int, padY: Int, c: Float): Drawable {
    return ShapeDrawable(RoundRectShape(floatArrayOf(c, c, c, c, c, c, c, c), null, null)).apply {
        paint.color = Color.parseColor("#FFFFFF")
        setPadding(padX, padY, padX, padY)
    }
}

fun primaryColorTitle(text : String) : Spanned =
    HtmlCompat.fromHtml("<font color=\"#94b422\">${text}</font>", HtmlCompat.FROM_HTML_MODE_LEGACY)

// pixel at (6, 20) for an xhdpi density=2 input image of size (50, 80)
fun getFruitColor(resources : Resources, tid: Int?) : Int =
    BitmapFactory.decodeResource(resources, treeIdToMarkerIcon[tid] ?: R.drawable.icon_otherfruit)
        .getPixel((resources.displayMetrics.density * 3).toInt(), (resources.displayMetrics.density * 10).toInt())
