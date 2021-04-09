package xjcl.mundraub.utils

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import com.google.android.gms.maps.model.Marker
import com.squareup.picasso.Picasso
import xjcl.mundraub.data.MarkerData

class PicassoBitmapTarget : com.squareup.picasso.Target {
    var marker: Marker? = null
    var md: MarkerData? = null
    override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
        md?.image = placeHolderDrawable?.toBitmap()
        marker?.showInfoWindow()
    }
    override fun onBitmapFailed(errorDrawable: Drawable?) { }
    override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
        md?.image = bitmap
        marker?.showInfoWindow()
    }
}