package com.appojellyapp.feature.streaming.moonlight.input

import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.net.ssl.SSLSocket

/**
 * Handles sending controller, mouse, and keyboard input to the server
 * via the Moonlight input protocol.
 *
 * The input protocol uses a TCP+TLS connection on port 35043.
 * Each input event is sent as a binary message with a type header
 * and payload specific to the input type.
 *
 * Message format:
 * - 4 bytes: message type (little-endian)
 * - 4 bytes: payload length (little-endian)
 * - N bytes: payload
 */
class ControllerHandler(
    socket: SSLSocket,
) {
    private val output: OutputStream = socket.getOutputStream()
    private val writeLock = Any()

    // Input message types (matching Moonlight protocol)
    companion object {
        const val INPUT_TYPE_MOUSE_MOVE = 0x00000008
        const val INPUT_TYPE_MOUSE_BUTTON = 0x00000005
        const val INPUT_TYPE_KEYBOARD = 0x00000003
        const val INPUT_TYPE_MULTI_CONTROLLER = 0x0000000D
        const val INPUT_TYPE_MOUSE_SCROLL = 0x00000009

        // Mouse button codes
        const val MOUSE_BUTTON_LEFT = 1
        const val MOUSE_BUTTON_MIDDLE = 2
        const val MOUSE_BUTTON_RIGHT = 3

        // Controller button flags (matching xinput)
        const val DPAD_UP = 0x0001
        const val DPAD_DOWN = 0x0002
        const val DPAD_LEFT = 0x0004
        const val DPAD_RIGHT = 0x0008
        const val START = 0x0010
        const val BACK = 0x0020
        const val LEFT_STICK = 0x0040
        const val RIGHT_STICK = 0x0080
        const val LEFT_SHOULDER = 0x0100
        const val RIGHT_SHOULDER = 0x0200
        const val A_BUTTON = 0x1000
        const val B_BUTTON = 0x2000
        const val X_BUTTON = 0x4000
        const val Y_BUTTON = 0x8000
        const val SPECIAL_BUTTON = 0x0400 // Guide/Home button
    }

    /**
     * Send a multi-controller input event.
     *
     * This is the primary method for sending gamepad input. It supports
     * multiple controllers and all standard gamepad inputs.
     */
    fun sendMultiControllerInput(
        controllerNumber: Int,
        activeGamepadMask: Int,
        buttonFlags: Int,
        leftTrigger: Byte,
        rightTrigger: Byte,
        leftStickX: Short,
        leftStickY: Short,
        rightStickX: Short,
        rightStickY: Short,
    ) {
        val payload = ByteBuffer.allocate(30).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            // Header
            putInt(0x0A) // Header type
            // Multi-controller payload
            putShort(controllerNumber.toShort())
            putShort(activeGamepadMask.toShort())
            putShort(0) // Mid button flags
            putShort(buttonFlags.toShort())
            put(leftTrigger)
            put(rightTrigger)
            putShort(leftStickX)
            putShort(leftStickY)
            putShort(rightStickX)
            putShort(rightStickY)
            // Padding
            putShort(0)
            putShort(0)
            putShort(0)
        }

        sendInputMessage(INPUT_TYPE_MULTI_CONTROLLER, payload.array())
    }

    /**
     * Send a mouse move event (relative movement).
     */
    fun sendMouseMove(deltaX: Short, deltaY: Short) {
        val payload = ByteBuffer.allocate(8).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            putShort(deltaX)
            putShort(deltaY)
            putInt(0) // Padding
        }

        sendInputMessage(INPUT_TYPE_MOUSE_MOVE, payload.array())
    }

    /**
     * Send a mouse button press/release event.
     */
    fun sendMouseButton(button: Byte, pressed: Boolean) {
        val payload = ByteBuffer.allocate(4).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put(if (pressed) 0x07.toByte() else 0x08.toByte()) // Down/Up action
            put(button)
            putShort(0) // Padding
        }

        sendInputMessage(INPUT_TYPE_MOUSE_BUTTON, payload.array())
    }

    /**
     * Send a mouse scroll event.
     */
    fun sendMouseScroll(scrollAmount: Short) {
        val payload = ByteBuffer.allocate(4).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            putShort(scrollAmount)
            putShort(0) // Padding
        }

        sendInputMessage(INPUT_TYPE_MOUSE_SCROLL, payload.array())
    }

    /**
     * Send a keyboard key press/release event.
     */
    fun sendKeyboardInput(keyCode: Short, pressed: Boolean, modifiers: Byte = 0) {
        val payload = ByteBuffer.allocate(8).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put(if (pressed) 0x03.toByte() else 0x04.toByte()) // Down/Up action
            put(0) // Flags
            putShort(keyCode)
            put(modifiers)
            put(0) // Padding
            putShort(0) // Padding
        }

        sendInputMessage(INPUT_TYPE_KEYBOARD, payload.array())
    }

    /**
     * Send a raw input message over the input stream.
     */
    private fun sendInputMessage(type: Int, payload: ByteArray) {
        val message = ByteBuffer.allocate(8 + payload.size).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            putInt(type)
            putInt(payload.size)
            put(payload)
        }

        synchronized(writeLock) {
            try {
                output.write(message.array())
                output.flush()
            } catch (_: IOException) {
                // Input stream broken - connection may have dropped
            }
        }
    }
}
