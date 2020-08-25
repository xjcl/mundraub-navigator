package xjcl.mundraub

import android.app.Activity
import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
        val mapView = super.onCreateView(inflater, container, savedInstanceState)!!

        val root = RelativeLayout(context).apply {
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
            addView(mapView)
        }

        mapView.post {
            val markerView = ImageView(context).apply {
                setImageResource(treeIdToMarkerIcon[tid] ?: R.drawable.otherfruit)
                measure(0, 0)
                x = mapView.measuredWidth / 2F - measuredWidth / 2F
                y = mapView.measuredHeight / 2F - measuredHeight  // anchor at bottom
            }

            root.addView(markerView)
        }

        return root
    }
}

class LocationPicker : AppCompatActivity(), OnMapReadyCallback {

    // Handle ActionBar option selection
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.title) {
            "OK" -> { returnLocation(); return true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Create the ActionBar options menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 0, 0, "OK").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_picker)
        supportActionBar?.title = getString(R.string.pickLoc)
        tid = this.intent.getIntExtra("tid", 12)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as MapFragmentPicker
        mapFragment.getMapAsync(this)
    }

    // exit activity
    private fun returnLocation() {
        val output = Intent().putExtra("lat", nMap.cameraPosition.target.latitude)
            .putExtra("lng", nMap.cameraPosition.target.longitude)
        setResult(Activity.RESULT_OK, output)
        finish()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        nMap = googleMap

        val lat = this.intent.getDoubleExtra("lat", 0.0)
        val lng = this.intent.getDoubleExtra("lng", 0.0)
        if (lat == 0.0 && lng == 0.0)
            nMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(51.17, 10.45), 6F))
        else
            nMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 14F))
    }
}
