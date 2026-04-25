package com.example.aura.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.layers.CircleLayer
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleColor
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleRadius
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleStrokeColor
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleStrokeWidth
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource

private const val PROFILE_NODE_SOURCE_ID = "profile-node-source"
private const val PROFILE_NODE_LAYER_ID = "profile-node-layer"

/** Бесплатный векторный стиль OpenFreeMap (без ключа). */
private const val PROFILE_ONLINE_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

/**
 * Мини-карта MapLibre для профиля ноды: показывает позицию в mesh-сети.
 * Если координаты неизвестны — отображает затемнённый оверлей с подсказкой.
 */
@Composable
fun ProfileNodeLocationMap(
    latitude: Double?,
    longitude: Double?,
    modifier: Modifier = Modifier,
) {
    var mapRef by remember { mutableStateOf<MapboxMap?>(null) }
    val mapView = rememberMapViewWithLifecycle()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF121C28)),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { _ ->
                mapView.addOnDidFailLoadingMapListener {
                    Log.e("MapLibre", "Profile map/style failed to load")
                }
                mapView.getMapAsync { map ->
                    mapRef = map
                    map.uiSettings.apply {
                        isCompassEnabled = false
                        isRotateGesturesEnabled = false
                        isScrollGesturesEnabled = false
                        isZoomGesturesEnabled = false
                        isTiltGesturesEnabled = false
                    }
                    map.setStyle(PROFILE_ONLINE_STYLE_URL) { style ->
                        applyProfileMapState(map, style, latitude, longitude)
                    }
                }
                mapView
            },
            update = { _ ->
                val map = mapRef
                val style = map?.style
                if (map != null && style != null) {
                    runCatching { applyProfileMapState(map, style, latitude, longitude) }
                        .onFailure { Log.e("MapLibre", "Profile map update failed", it) }
                }
            },
        )

        if (latitude == null || longitude == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA070D14)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Позиция в mesh для этой ноды пока неизвестна",
                    color = Color(0xFFE8F0F8),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private fun applyProfileMapState(
    map: MapboxMap,
    style: Style,
    latitude: Double?,
    longitude: Double?,
) {
    val existingSource = style.getSourceAs<GeoJsonSource>(PROFILE_NODE_SOURCE_ID)
    val lat = latitude
    val lon = longitude

    if (lat != null && lon != null) {
        val feature = Feature.fromGeometry(Point.fromLngLat(lon, lat))
        if (existingSource != null) {
            existingSource.setGeoJson(feature)
        } else {
            style.addSource(GeoJsonSource(PROFILE_NODE_SOURCE_ID, feature))
            if (style.getLayer(PROFILE_NODE_LAYER_ID) == null) {
                style.addLayer(
                    CircleLayer(PROFILE_NODE_LAYER_ID, PROFILE_NODE_SOURCE_ID).withProperties(
                        circleRadius(8f),
                        circleColor("#4AF263"),
                        circleStrokeWidth(1.5f),
                        circleStrokeColor("#FFFFFF"),
                    ),
                )
            }
        }
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 14.0))
    } else {
        val empty = FeatureCollection.fromFeatures(emptyArray())
        if (existingSource != null) {
            existingSource.setGeoJson(empty)
        } else {
            style.addSource(GeoJsonSource(PROFILE_NODE_SOURCE_ID, empty))
        }
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(55.7522, 37.6156), 3.0))
    }
}





