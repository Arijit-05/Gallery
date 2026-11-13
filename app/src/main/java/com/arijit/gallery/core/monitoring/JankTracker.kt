package com.arijit.gallery.core.monitoring

import android.app.Activity
import android.util.Log
import android.view.Window
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors UI frame performance using AndroidX JankStats.
 * Designed for 120Hz (8.33ms frame budget) on Motorola Edge 70 and modern displays.
 */
@Singleton
class JankTracker @Inject constructor() {

    private var jankStats: JankStats? = null
    private var metricsStateHolder: PerformanceMetricsState.Holder? = null

    data class JankRecord(
        val screen: String,
        val durationMs: Double,
        val isJank: Boolean,
        val states: List<String>,
        val timestampNanos: Long
    )

    private val _recentJanks = CopyOnWriteArrayList<JankRecord>()
    val recentJanks: List<JankRecord> get() = _recentJanks

    fun startTracking(activity: Activity) {
        val window: Window = activity.window
        metricsStateHolder = PerformanceMetricsState.getHolderForHierarchy(window.decorView)

        val listener = JankStats.OnFrameListener { frameData: FrameData ->
            val durationMs = frameData.frameDurationUiNanos / 1_000_000.0
            val stateList = frameData.states.map { "${it.key}=${it.value}" }
            val screenState = frameData.states.firstOrNull { it.key == "Screen" }?.value ?: "Unknown"

            // 120Hz budget is 8.33ms. Even if system flags jank at 60Hz default threshold (16ms),
            // flag anything > 8.33ms when in 120Hz mode.
            val is120HzJank = frameData.isJank || durationMs > 8.33

            if (is120HzJank) {
                val record = JankRecord(
                    screen = screenState,
                    durationMs = durationMs,
                    isJank = true,
                    states = stateList,
                    timestampNanos = frameData.frameStartNanos
                )
                if (_recentJanks.size > 200) {
                    _recentJanks.removeAt(0)
                }
                _recentJanks.add(record)

                Log.w(
                    TAG,
                    "⚠️ [JANK @ 120Hz] Screen: $screenState | Duration: ${String.format("%.2f", durationMs)}ms | States: $stateList"
                )
            }
        }

        try {
            jankStats = JankStats.createAndTrack(window, listener).apply {
                isTrackingEnabled = true
            }
            Log.i(TAG, "JankStats tracking initialized for ${activity.componentName.className}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize JankStats", e)
        }
    }

    fun putState(key: String, value: String) {
        metricsStateHolder?.state?.putState(key, value)
    }

    fun removeState(key: String) {
        metricsStateHolder?.state?.removeState(key)
    }

    fun stopTracking() {
        jankStats?.isTrackingEnabled = false
        jankStats = null
    }

    companion object {
        private const val TAG = "GalleryJankStats"
    }
}

val LocalJankTracker = androidx.compose.runtime.staticCompositionLocalOf<JankTracker?> { null }

