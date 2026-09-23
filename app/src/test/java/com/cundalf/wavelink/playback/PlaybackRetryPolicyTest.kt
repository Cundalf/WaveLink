package com.cundalf.wavelink.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRetryPolicyTest {
    @Test
    fun allowsMaxRetriesThenStopsAndResets() {
        val policy = PlaybackRetryPolicy(maxRetries = 3)
        assertTrue(policy.nextRetry())
        assertEquals(1, policy.retries)
        assertTrue(policy.nextRetry())
        assertTrue(policy.nextRetry())
        assertFalse(policy.nextRetry())
        assertEquals(0, policy.retries)
        // Una nueva racha vuelve a permitir reintentos.
        assertTrue(policy.nextRetry())
    }

    @Test
    fun connectedConnectionResetsCounter() {
        val policy = PlaybackRetryPolicy(maxRetries = 3)
        policy.nextRetry()
        policy.nextRetry()
        policy.onConnected()
        assertEquals(0, policy.retries)
        assertTrue(policy.nextRetry())
        assertEquals(1, policy.retries)
    }

    @Test
    fun changingRadioResetsCounter() {
        val policy = PlaybackRetryPolicy(maxRetries = 3)
        policy.nextRetry()
        policy.nextRetry()
        policy.onMediaItemChanged()
        assertEquals(0, policy.retries)
        assertTrue(policy.nextRetry())
        assertEquals(1, policy.retries)
    }

    @Test
    fun resetClearsCounter() {
        val policy = PlaybackRetryPolicy(maxRetries = 1)
        policy.nextRetry()
        policy.reset()
        assertEquals(0, policy.retries)
    }

    @Test
    fun zeroRetriesNeverRetries() {
        val policy = PlaybackRetryPolicy(maxRetries = 0)
        assertFalse(policy.nextRetry())
        assertEquals(0, policy.retries)
    }
}
