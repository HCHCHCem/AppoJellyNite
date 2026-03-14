package com.appojellyapp.feature.streaming.moonlight.input

import android.view.MotionEvent

/**
 * Maps touch screen input to mouse events for the Moonlight streaming protocol.
 *
 * Supports:
 * - Single finger drag = mouse move
 * - Single tap = left click
 * - Two-finger tap = right click
 * - Two-finger drag = scroll
 * - Long press = left click and hold (for drag)
 */
class TouchInputMapper(
    private val controllerHandler: ControllerHandler,
    private val streamWidth: Int,
    private val streamHeight: Int,
) {
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchStartTime = 0L
    private var isDragging = false
    private var pointerCount = 0
    private var lastScrollY = 0f

    companion object {
        private const val TAP_THRESHOLD_PX = 20f
        private const val TAP_THRESHOLD_MS = 250L
        private const val LONG_PRESS_MS = 500L
        private const val MOUSE_SENSITIVITY = 1.5f
    }

    /**
     * Handle a touch event and translate to mouse input.
     * @return true if the event was consumed
     */
    fun handleTouchEvent(event: MotionEvent, viewWidth: Int, viewHeight: Int): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                touchStartX = event.x
                touchStartY = event.y
                touchStartTime = System.currentTimeMillis()
                isDragging = false
                pointerCount = 1
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                pointerCount = event.pointerCount
                if (pointerCount == 2) {
                    lastScrollY = event.getY(1)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (pointerCount == 1) {
                    // Single finger drag = mouse move
                    val deltaX = ((event.x - lastTouchX) * MOUSE_SENSITIVITY).toInt()
                    val deltaY = ((event.y - lastTouchY) * MOUSE_SENSITIVITY).toInt()

                    if (deltaX != 0 || deltaY != 0) {
                        isDragging = true
                        controllerHandler.sendMouseMove(deltaX.toShort(), deltaY.toShort())
                    }

                    lastTouchX = event.x
                    lastTouchY = event.y
                } else if (pointerCount == 2 && event.pointerCount >= 2) {
                    // Two-finger drag = scroll
                    val currentY = event.getY(1)
                    val scrollDelta = (lastScrollY - currentY).toInt()
                    if (scrollDelta != 0) {
                        controllerHandler.sendMouseScroll(scrollDelta.toShort())
                    }
                    lastScrollY = currentY
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (pointerCount == 2) {
                    val elapsed = System.currentTimeMillis() - touchStartTime
                    if (elapsed < TAP_THRESHOLD_MS && !isDragging) {
                        // Two-finger tap = right click
                        controllerHandler.sendMouseButton(
                            ControllerHandler.MOUSE_BUTTON_RIGHT.toByte(), true
                        )
                        controllerHandler.sendMouseButton(
                            ControllerHandler.MOUSE_BUTTON_RIGHT.toByte(), false
                        )
                    }
                }
                pointerCount = event.pointerCount - 1
            }

            MotionEvent.ACTION_UP -> {
                val elapsed = System.currentTimeMillis() - touchStartTime
                val distX = Math.abs(event.x - touchStartX)
                val distY = Math.abs(event.y - touchStartY)

                if (pointerCount == 1 && !isDragging &&
                    distX < TAP_THRESHOLD_PX && distY < TAP_THRESHOLD_PX
                ) {
                    if (elapsed < TAP_THRESHOLD_MS) {
                        // Single tap = left click
                        controllerHandler.sendMouseButton(
                            ControllerHandler.MOUSE_BUTTON_LEFT.toByte(), true
                        )
                        controllerHandler.sendMouseButton(
                            ControllerHandler.MOUSE_BUTTON_LEFT.toByte(), false
                        )
                    }
                }

                isDragging = false
                pointerCount = 0
            }
        }
        return true
    }
}
