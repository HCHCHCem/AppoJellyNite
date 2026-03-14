package com.appojellyapp.feature.streaming.moonlight.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Maps Android gamepad/controller input events to Moonlight controller input.
 *
 * Handles:
 * - D-pad buttons and analog hat switches
 * - Face buttons (A, B, X, Y)
 * - Shoulder buttons (L1, R1) and triggers (L2, R2)
 * - Analog sticks (L3, R3 press)
 * - Start, Select/Back, Guide/Home buttons
 * - Analog stick axes with proper dead zone handling
 */
class GamepadInputMapper(
    private val controllerHandler: ControllerHandler,
) {
    private var buttonFlags = 0
    private var leftTrigger: Byte = 0
    private var rightTrigger: Byte = 0
    private var leftStickX: Short = 0
    private var leftStickY: Short = 0
    private var rightStickX: Short = 0
    private var rightStickY: Short = 0

    companion object {
        private const val DEAD_ZONE = 0.15f

        // Map Android KeyEvent codes to Moonlight button flags
        private val BUTTON_MAP = mapOf(
            KeyEvent.KEYCODE_BUTTON_A to ControllerHandler.A_BUTTON,
            KeyEvent.KEYCODE_BUTTON_B to ControllerHandler.B_BUTTON,
            KeyEvent.KEYCODE_BUTTON_X to ControllerHandler.X_BUTTON,
            KeyEvent.KEYCODE_BUTTON_Y to ControllerHandler.Y_BUTTON,
            KeyEvent.KEYCODE_BUTTON_L1 to ControllerHandler.LEFT_SHOULDER,
            KeyEvent.KEYCODE_BUTTON_R1 to ControllerHandler.RIGHT_SHOULDER,
            KeyEvent.KEYCODE_BUTTON_THUMBL to ControllerHandler.LEFT_STICK,
            KeyEvent.KEYCODE_BUTTON_THUMBR to ControllerHandler.RIGHT_STICK,
            KeyEvent.KEYCODE_BUTTON_START to ControllerHandler.START,
            KeyEvent.KEYCODE_BUTTON_SELECT to ControllerHandler.BACK,
            KeyEvent.KEYCODE_BUTTON_MODE to ControllerHandler.SPECIAL_BUTTON,
            KeyEvent.KEYCODE_DPAD_UP to ControllerHandler.DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN to ControllerHandler.DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT to ControllerHandler.DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT to ControllerHandler.DPAD_RIGHT,
        )
    }

    /**
     * Handle a key event from a gamepad.
     * @return true if the event was consumed
     */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        // Only handle gamepad sources
        if (event.source and InputDevice.SOURCE_GAMEPAD == 0 &&
            event.source and InputDevice.SOURCE_JOYSTICK == 0
        ) {
            return false
        }

        val flag = BUTTON_MAP[event.keyCode] ?: return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                buttonFlags = buttonFlags or flag
                sendInput()
                return true
            }
            KeyEvent.ACTION_UP -> {
                buttonFlags = buttonFlags and flag.inv()
                sendInput()
                return true
            }
        }
        return false
    }

    /**
     * Handle a motion event (analog sticks, triggers, D-pad hat) from a gamepad.
     * @return true if the event was consumed
     */
    fun handleMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK == 0 &&
            event.source and InputDevice.SOURCE_GAMEPAD == 0
        ) {
            return false
        }

        // Left stick
        val lx = applyDeadZone(event.getAxisValue(MotionEvent.AXIS_X))
        val ly = applyDeadZone(event.getAxisValue(MotionEvent.AXIS_Y))
        leftStickX = (lx * Short.MAX_VALUE).toInt().toShort()
        leftStickY = (ly * Short.MAX_VALUE).toInt().toShort()

        // Right stick
        val rx = applyDeadZone(event.getAxisValue(MotionEvent.AXIS_Z))
        val ry = applyDeadZone(event.getAxisValue(MotionEvent.AXIS_RZ))
        rightStickX = (rx * Short.MAX_VALUE).toInt().toShort()
        rightStickY = (ry * Short.MAX_VALUE).toInt().toShort()

        // Triggers
        val lt = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
            .coerceIn(0f, 1f)
        val rt = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)
            .coerceIn(0f, 1f)
        leftTrigger = (lt * 255).toInt().toByte()
        rightTrigger = (rt * 255).toInt().toByte()

        // D-pad via hat axes
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        buttonFlags = buttonFlags and (
                ControllerHandler.DPAD_UP or ControllerHandler.DPAD_DOWN or
                        ControllerHandler.DPAD_LEFT or ControllerHandler.DPAD_RIGHT
                ).inv()

        if (hatX < -0.5f) buttonFlags = buttonFlags or ControllerHandler.DPAD_LEFT
        if (hatX > 0.5f) buttonFlags = buttonFlags or ControllerHandler.DPAD_RIGHT
        if (hatY < -0.5f) buttonFlags = buttonFlags or ControllerHandler.DPAD_UP
        if (hatY > 0.5f) buttonFlags = buttonFlags or ControllerHandler.DPAD_DOWN

        sendInput()
        return true
    }

    private fun sendInput() {
        controllerHandler.sendMultiControllerInput(
            controllerNumber = 0,
            activeGamepadMask = 0x01,
            buttonFlags = buttonFlags,
            leftTrigger = leftTrigger,
            rightTrigger = rightTrigger,
            leftStickX = leftStickX,
            leftStickY = leftStickY,
            rightStickX = rightStickX,
            rightStickY = rightStickY,
        )
    }

    private fun applyDeadZone(value: Float): Float {
        return if (Math.abs(value) < DEAD_ZONE) 0f else value
    }
}
