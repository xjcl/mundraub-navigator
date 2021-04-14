package xjcl.mundraub.data

import android.view.View
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import xjcl.mundraub.utils.MarkerDataManager
import xjcl.mundraub.utils.PicassoMarkerDataTarget
import java.util.concurrent.Executors


// ugly, but these NEED to be shared across (mainly) Main.kt and FruitBarMapFragment.kt
var mMap: GoogleMap? = null
lateinit var fusedLocationClient: FusedLocationProviderClient
var mapTypeChanged = false

lateinit var mapView : View
lateinit var fab : FloatingActionButton

var markers = mutableMapOf<LatLng, Marker>()
var markersData = mutableMapOf<LatLng, MarkerData>()
val markerContext = Executors.newSingleThreadExecutor()
val markerDataManager = MarkerDataManager()

var selectedSpeciesStr : String = "1,4,5,6,7,8,9,10,11,12,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37"
var fabAnimationFromTo : Pair<Float, Float> = 0F to 0F
var totalLeftPadding = 0

// This stupidly needs to be stored with a strong reference because it otherwise gets garbage-collected
// https://stackoverflow.com/a/24602348/2111778
val picassoMarkerDataTarget = PicassoMarkerDataTarget()
