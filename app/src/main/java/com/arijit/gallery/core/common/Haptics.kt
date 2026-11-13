package com.arijit.gallery.core.common

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

@Stable
class HapticController(private val view: View? = null) {
    fun selectionStart() {
        view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun selectionTick() {
        view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun snap() {
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun click() {
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun buttonTap() {
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun favoritePulse() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    fun tabSwitch() {
        view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun heavy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view?.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    fun toggle() {
        view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}

@Composable
fun rememberHapticController(): HapticController {
    val view = LocalView.current
    return remember(view) { HapticController(view) }
}

