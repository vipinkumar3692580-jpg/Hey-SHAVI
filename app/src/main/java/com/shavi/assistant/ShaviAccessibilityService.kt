package com.shavi.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * Lets SHAVi perform on-screen gestures like scrolling, which normal apps
 * cannot do to OTHER apps without Accessibility permission.
 * User must enable this manually in Settings > Accessibility > Hey SHAVi.
 */
class ShaviAccessibilityService : AccessibilityService() {

    companion object {
        var instance: ShaviAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* not used */ }

    override fun onInterrupt() { /* not used */ }

    fun performScroll(direction: String) {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        val path = Path()
        if (direction.contains("up", ignoreCase = true)) {
            path.moveTo(width / 2f, height * 0.3f)
            path.lineTo(width / 2f, height * 0.7f)
        } else {
            path.moveTo(width / 2f, height * 0.7f)
            path.lineTo(width / 2f, height * 0.3f)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        dispatchGesture(gesture, null, null)
    }
}
