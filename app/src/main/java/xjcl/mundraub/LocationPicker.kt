package xjcl.mundraub

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng


lateinit var nMap: GoogleMap
var tid = 12

// same strategy as in the main maps activity -- have to intercept the SupportMapFragment to draw a UI
class MapFragmentPicker : SupportMapFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mapView = super.onCreateView(inflater, container, savedInstanceState)!!

        relView = RelativeLayout(this.context)
        relView.addView(mapView)

        val btn = Button(this.context)
        btn.text = "OK"
        btn.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        btn.layoutParams = {
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 40)
            lp
        }()
        relView.addView(btn)
        btn.setOnClickListener {
            nMap.animateCamera( CameraUpdateFactory.zoomBy(0F) )  // sneakily trigger onCameraMoveStarted
        }

        mapView.post {
            val scrWidth = mapView.measuredWidth
            val scrHeight = mapView.measuredHeight

            val markerView = ImageView(this.context)
            Log.e("tid", "$tid")
            markerView.setImageResource( treeIdToMarkerIcon[tid] ?: R.drawable.otherfruit )

            markerView.measure(0, 0)

            markerView.x = scrWidth/2F - markerView.measuredWidth/2F
            markerView.y = scrHeight/2F - markerView.measuredHeight  // anchor at bottom
            relView.addView(markerView)
        }

        return relView
    }
}

class LocationPicker : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnCameraMoveStartedListener {

    var listen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_picker)
        supportActionBar!!.title = getString(R.string.pickLoc)
        tid = this.intent.getIntExtra("tid", 12)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as MapFragmentPicker
        mapFragment.getMapAsync(this)
    }

    // hacky way to exit activity
    override fun onCameraMoveStarted(reason : Int) {
        if (reason != GoogleMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION || !listen) return
        val output = Intent()
        output.putExtra("lat", nMap.cameraPosition.target.latitude)
        output.putExtra("lng", nMap.cameraPosition.target.longitude)
        setResult(Activity.RESULT_OK, output)
        finish()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        nMap = googleMap
        nMap.setOnCameraMoveStartedListener(this)

        // TODO should start at currently selected location
        val lat = this.intent.getDoubleExtra("lat", 0.0)
        val lng = this.intent.getDoubleExtra("lng", 0.0)
        if (lat == 0.0 && lng == 0.0)
            nMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(51.17, 10.45), 6F))
        else
            nMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 14F))

        listen = true
    }
}