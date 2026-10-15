package com.cundalf.wavelink.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RadioUrlSanitizerTest {
    @Test
    fun stripsCredentialsFromDisplayUrl() {
        assertEquals(
            "http://radio.example.com:8000/stream.mp3",
            sanitizedStreamUrl("http://user:pass@radio.example.com:8000/stream.mp3"),
        )
    }

    @Test
    fun stripsCredentialsWithoutPassword() {
        assertEquals(
            "https://radio.example.com/stream.mp3",
            sanitizedStreamUrl("https://user@radio.example.com/stream.mp3"),
        )
    }

    @Test
    fun keepsUrlUntouchedWhenThereAreNoCredentials() {
        val url = "https://radio.example.com/stream.mp3?token=abc"
        assertEquals(url, sanitizedStreamUrl(url))
    }

    @Test
    fun keepsUnparseableUrlUntouched() {
        assertEquals("no es una url", sanitizedStreamUrl("no es una url"))
    }

    @Test
    fun detectsInsecureHttpUrls() {
        assertTrue("http://radio.example.com/stream".isInsecureStreamUrl())
        assertTrue("HTTP://radio.example.com/stream".isInsecureStreamUrl())
        assertFalse("https://radio.example.com/stream".isInsecureStreamUrl())
        assertFalse(" ".isInsecureStreamUrl())
    }
}
