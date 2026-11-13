package com.arijit.gallery.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() = rule.collect(
        packageName = TARGET_PACKAGE
    ) {
        pressHome()
        startActivityAndWait()

        // Grid scroll fling
        device.waitForIdle()
        device.swipe(
            device.displayWidth / 2,
            (device.displayHeight * 0.8f).toInt(),
            device.displayWidth / 2,
            (device.displayHeight * 0.2f).toInt(),
            8
        )
        device.waitForIdle()

        // Open viewer
        device.click(device.displayWidth / 4, (device.displayHeight * 0.35f).toInt())
        device.waitForIdle()

        // Swipe pager
        device.swipe(
            (device.displayWidth * 0.85f).toInt(),
            device.displayHeight / 2,
            (device.displayWidth * 0.15f).toInt(),
            device.displayHeight / 2,
            10
        )
        device.waitForIdle()

        // Swipe-down dismiss
        device.swipe(
            device.displayWidth / 2,
            (device.displayHeight * 0.4f).toInt(),
            device.displayWidth / 2,
            (device.displayHeight * 0.9f).toInt(),
            12
        )
        device.waitForIdle()
    }

    companion object {
        const val TARGET_PACKAGE = "com.arijit.gallery"
    }
}

