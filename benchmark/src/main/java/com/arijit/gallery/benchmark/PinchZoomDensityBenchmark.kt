package com.arijit.gallery.benchmark

import android.os.SystemClock
import android.view.MotionEvent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class PinchZoomDensityBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun pinchZoomGridDensity() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), 5000)
        }
    ) {
        val cx = device.displayWidth / 2f
        val cy = device.displayHeight / 2f

        // Pinch out (zoom in)
        performPinch(
            startX1 = cx - 50f, startY1 = cy - 50f,
            endX1 = cx - 250f, endY1 = cy - 250f,
            startX2 = cx + 50f, startY2 = cy + 50f,
            endX2 = cx + 250f, endY2 = cy + 250f,
            steps = 25
        )
        device.waitForIdle()

        // Pinch in (zoom out)
        performPinch(
            startX1 = cx - 250f, startY1 = cy - 250f,
            endX1 = cx - 50f, endY1 = cy - 50f,
            startX2 = cx + 250f, startY2 = cy + 250f,
            endX2 = cx + 50f, endY2 = cy + 50f,
            steps = 25
        )
        device.waitForIdle()
    }

    private fun performPinch(
        startX1: Float, startY1: Float, endX1: Float, endY1: Float,
        startX2: Float, startY2: Float, endX2: Float, endY2: Float,
        steps: Int
    ) {
        val inst = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()

        val properties = arrayOf(
            MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER },
            MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER }
        )

        val coords = arrayOf(
            MotionEvent.PointerCoords().apply { x = startX1; y = startY1; pressure = 1f; size = 1f },
            MotionEvent.PointerCoords().apply { x = startX2; y = startY2; pressure = 1f; size = 1f }
        )

        val eventDown1 = MotionEvent.obtain(
            downTime, downTime, MotionEvent.ACTION_DOWN, 1,
            properties, coords, 0, 0, 1f, 1f, 0, 0, 0, 0
        )
        inst.sendPointerSync(eventDown1)

        val eventDown2 = MotionEvent.obtain(
            downTime, downTime,
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, properties, coords, 0, 0, 1f, 1f, 0, 0, 0, 0
        )
        inst.sendPointerSync(eventDown2)

        for (i in 1..steps) {
            val progress = i.toFloat() / steps
            coords[0].x = startX1 + (endX1 - startX1) * progress
            coords[0].y = startY1 + (endY1 - startY1) * progress
            coords[1].x = startX2 + (endX2 - startX2) * progress
            coords[1].y = startY2 + (endY2 - startY2) * progress

            val eventMove = MotionEvent.obtain(
                downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, 2,
                properties, coords, 0, 0, 1f, 1f, 0, 0, 0, 0
            )
            inst.sendPointerSync(eventMove)
            SystemClock.sleep(10)
        }

        val eventUp2 = MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(),
            MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2, properties, coords, 0, 0, 1f, 1f, 0, 0, 0, 0
        )
        inst.sendPointerSync(eventUp2)

        val eventUp1 = MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 1,
            properties, coords, 0, 0, 1f, 1f, 0, 0, 0, 0
        )
        inst.sendPointerSync(eventUp1)
    }

    companion object {
        const val TARGET_PACKAGE = "com.arijit.gallery"
    }
}

