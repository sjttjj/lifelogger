package com.sam.lifelogger.squeeze

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibleSqueezeActionBridgeTest {

    @Test
    fun dispatchReturnsFalseWhenNoVisibleHandlerIsRegistered() {
        VisibleSqueezeActionBridge.unregister()

        val handled = VisibleSqueezeActionBridge.dispatchToggleRecording()

        assertFalse(handled)
    }

    @Test
    fun dispatchInvokesRegisteredVisibleHandler() {
        var called = false
        VisibleSqueezeActionBridge.register(
            object : VisibleSqueezeActionBridge.Handler {
                override fun toggleRecording() {
                    called = true
                }
            }
        )

        val handled = VisibleSqueezeActionBridge.dispatchToggleRecording()

        assertTrue(handled)
        assertTrue(called)
        VisibleSqueezeActionBridge.unregister()
    }

    @Test
    fun unregisterPreventsLaterDispatch() {
        var callCount = 0
        VisibleSqueezeActionBridge.register(
            object : VisibleSqueezeActionBridge.Handler {
                override fun toggleRecording() {
                    callCount += 1
                }
            }
        )
        VisibleSqueezeActionBridge.unregister()

        val handled = VisibleSqueezeActionBridge.dispatchToggleRecording()

        assertFalse(handled)
        assertTrue(callCount == 0)
    }
}
