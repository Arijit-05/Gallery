package com.arijit.gallery.benchmark

import android.graphics.Point
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class GridScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollTimelineGridFling() = benchmarkRule.measureRepeated(
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
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight

        // Fling down
        device.swipe(
            displayWidth / 2,
            (displayHeight * 0.8f).toInt(),
            displayWidth / 2,
            (displayHeight * 0.2f).toInt(),
            6 // Fast fling speed
        )
        device.waitForIdle()

        // Fling back up
        device.swipe(
            displayWidth / 2,
            (displayHeight * 0.2f).toInt(),
            displayWidth / 2,
            (displayHeight * 0.8f).toInt(),
            6
        )
        device.waitForIdle()
    }

    companion object {
        const val TARGET_PACKAGE = "com.arijit.gallery"
    }
}

