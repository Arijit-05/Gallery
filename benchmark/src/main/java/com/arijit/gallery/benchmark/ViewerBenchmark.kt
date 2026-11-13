package com.arijit.gallery.benchmark

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
class ViewerBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun openViewerFromGrid() = benchmarkRule.measureRepeated(
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
        // Tap first media item thumbnail near top of grid
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        device.click(displayWidth / 4, (displayHeight * 0.35f).toInt())
        device.waitForIdle()

        // Press back to return
        device.pressBack()
        device.waitForIdle()
    }

    @Test
    fun swipeThroughPager() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), 5000)
            // Tap into viewer
            val displayWidth = device.displayWidth
            val displayHeight = device.displayHeight
            device.click(displayWidth / 4, (displayHeight * 0.35f).toInt())
            device.waitForIdle()
        }
    ) {
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight

        // Swipe left to next item
        device.swipe(
            (displayWidth * 0.85f).toInt(),
            displayHeight / 2,
            (displayWidth * 0.15f).toInt(),
            displayHeight / 2,
            10
        )
        device.waitForIdle()

        // Swipe right back to previous item
        device.swipe(
            (displayWidth * 0.15f).toInt(),
            displayHeight / 2,
            (displayWidth * 0.85f).toInt(),
            displayHeight / 2,
            10
        )
        device.waitForIdle()
    }

    @Test
    fun swipeDownDismiss() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), 5000)
            // Tap into viewer
            val displayWidth = device.displayWidth
            val displayHeight = device.displayHeight
            device.click(displayWidth / 4, (displayHeight * 0.35f).toInt())
            device.waitForIdle()
        }
    ) {
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight

        // Swipe down from center to dismiss
        device.swipe(
            displayWidth / 2,
            (displayHeight * 0.4f).toInt(),
            displayWidth / 2,
            (displayHeight * 0.9f).toInt(),
            12
        )
        device.waitForIdle()
    }

    @Test
    fun lightSwipeDownDismiss() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), 5000)
            // Tap into viewer
            val displayWidth = device.displayWidth
            val displayHeight = device.displayHeight
            device.click(displayWidth / 4, (displayHeight * 0.35f).toInt())
            device.waitForIdle()
        }
    ) {
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight

        // Light flick down (approx 12% of screen height)
        device.swipe(
            displayWidth / 2,
            (displayHeight * 0.45f).toInt(),
            displayWidth / 2,
            (displayHeight * 0.58f).toInt(),
            6
        )
        device.waitForIdle()
    }

    companion object {
        const val TARGET_PACKAGE = "com.arijit.gallery"
    }
}

