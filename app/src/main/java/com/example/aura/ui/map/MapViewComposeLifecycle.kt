package com.example.aura.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mapbox.mapboxsdk.maps.MapView

/**
 * [MapView] для Compose с привязкой к lifecycle.
 * Синхронизирует текущее состояние (не только будущие события), иначе при уже RESUMED
 * [LifecycleEventObserver] не получит ON_RESUME повторно.
 * Не вызывает onStart/onResume, пока владелец не дошёл хотя бы до [Lifecycle.State.STARTED] —
 * на части устройств (в т.ч. Samsung) ранний onResume до готовности Activity даёт нестабильность GL.
 */
@Composable
internal fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context).also { it.onCreate(null) } }
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        when (lifecycle.currentState) {
            Lifecycle.State.RESUMED -> {
                mapView.onStart()
                mapView.onResume()
            }
            Lifecycle.State.STARTED -> mapView.onStart()
            else -> Unit
        }
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}
