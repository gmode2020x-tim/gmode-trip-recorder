package ca.gmode.triprecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Circle
import org.mapsforge.map.layer.overlay.FixedPixelCircle
import org.mapsforge.map.layer.renderer.TileRendererLayer
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import java.io.FileInputStream
import java.util.Locale

/** Offline map display. All map data comes from the chosen local .map file. */
class OfflineMapActivity : AppCompatActivity(), LocationListener {
    private lateinit var mapView: MapView
    private lateinit var status: TextView
    private lateinit var locationManager: LocationManager
    private var mapStream: FileInputStream? = null
    private var positionMarker: FixedPixelCircle? = null
    private var radiusRing: Circle? = null
    private var latestFix: Location? = null
    private var followPosition = true

    private val chooseMap = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            getPreferences(MODE_PRIVATE).edit().putString("map_uri", uri.toString()).apply()
            openMap(uri)
        }
    }
    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startLocation() else status.text = "Precise location permission is needed for the live marker."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        AndroidGraphicFactory.createInstance(application)
        locationManager = getSystemService(LocationManager::class.java)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(12, 22, 26)) }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        controls.addView(Button(this).apply { text = "BACK"; setOnClickListener { finish() } })
        controls.addView(Button(this).apply { text = "CHOOSE OFFLINE MAP"; setOnClickListener { chooseMap.launch(arrayOf("*/*")) } })
        controls.addView(Button(this).apply { text = "MY POSITION"; setOnClickListener {
            followPosition = true
            latestFix?.let { mapView.setCenter(LatLong(it.latitude, it.longitude)) }
        } })
        root.addView(controls)
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(18, 4, 18, 8)
            text = "Choose an offline .map file. GPS works without internet."
        }
        root.addView(status)
        mapView = MapView(this).apply {
            setBuiltInZoomControls(true)
            getMapScaleBar().setVisible(true)
            setZoomLevel(7)
        }
        root.addView(mapView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        getPreferences(MODE_PRIVATE).getString("map_uri", null)?.let { saved ->
            runCatching { openMap(Uri.parse(saved)) }.onFailure { status.text = "Select the downloaded .map file again." }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocation()
        } else askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun openMap(uri: Uri) {
        try {
            mapView.layerManager.layers.clear()
            positionMarker = null
            radiusRing = null
            mapStream?.close()
            val stream = contentResolver.openInputStream(uri) as? FileInputStream
                ?: error("This map provider cannot open a seekable file. Save the .map in Downloads.")
            val map = MapFile(stream)
            val cache = AndroidUtil.createTileCache(
                this, "offline-map-cache", mapView.model.displayModel.tileSize, 1f,
                mapView.model.frameBufferModel.overdrawFactor,
            )
            val renderer = TileRendererLayer(cache, map, mapView.model.mapViewPosition, AndroidGraphicFactory.INSTANCE)
            renderer.setXmlRenderTheme(MapsforgeThemes.MOTORIDER)
            mapView.layerManager.layers.add(renderer)
            mapStream = stream
            status.text = "Offline map ready • GPS locating… • © OpenStreetMap contributors"
            map.startPosition()?.let { mapView.setCenter(it) }
            val currentFix = latestFix
            latestFix = null
            currentFix?.let { updatePosition(it) }
        } catch (error: Exception) {
            status.text = "Could not open map: ${error.message ?: "invalid file"}"
        }
    }

    @Suppress("MissingPermission")
    private fun startLocation() {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        providers.filter { locationManager.isProviderEnabled(it) }.forEach { provider ->
            locationManager.getLastKnownLocation(provider)?.let(::updatePosition)
            locationManager.requestLocationUpdates(provider, 2_000L, 3f, this)
        }
        if (providers.none { locationManager.isProviderEnabled(it) }) status.text = "Turn on phone Location to show your position."
    }

    override fun onLocationChanged(location: Location) = updatePosition(location)

    private fun updatePosition(location: Location) {
        if (location.time < System.currentTimeMillis() - 5 * 60_000L) return
        if (latestFix != null && location.time < latestFix!!.time) return
        latestFix = location
        val point = LatLong(location.latitude, location.longitude)
        if (radiusRing == null) {
            val ringPaint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
                color = Color.rgb(255, 130, 50)
                strokeWidth = 4f
                setStyle(Style.STROKE)
            }
            radiusRing = Circle(point, 100_000f, null, ringPaint).also { mapView.layerManager.layers.add(it) }
        }
        if (positionMarker == null) {
            val dotPaint = AndroidGraphicFactory.INSTANCE.createPaint().apply {
                color = Color.rgb(16, 130, 255)
                setStyle(Style.FILL)
            }
            val outline = AndroidGraphicFactory.INSTANCE.createPaint().apply {
                color = Color.WHITE
                strokeWidth = 3f
                setStyle(Style.STROKE)
            }
            positionMarker = FixedPixelCircle(point, 12f, dotPaint, outline).also { mapView.layerManager.layers.add(it) }
            mapView.setZoomLevel(7)
        } else positionMarker?.setLatLong(point)
        if (followPosition) mapView.setCenter(point)
        val accuracy = if (location.hasAccuracy()) " ±${location.accuracy.toInt()} m" else ""
        status.text = String.format(Locale.US, "GPS %.5f, %.5f%s • orange ring: 100 km • © OpenStreetMap contributors", location.latitude, location.longitude, accuracy)
    }

    override fun onDestroy() {
        locationManager.removeUpdates(this)
        mapView.destroyAll()
        mapStream?.close()
        AndroidGraphicFactory.clearResourceMemoryCache()
        super.onDestroy()
    }
}
