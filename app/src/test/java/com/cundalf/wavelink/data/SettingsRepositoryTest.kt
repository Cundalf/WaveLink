package com.cundalf.wavelink.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryTest {
    /**
     * Un solo método a propósito: el DataStore de settings es singleton por proceso
     * (`preferencesDataStore`) y Robolectric cambia `filesDir` entre tests; separar los
     * casos filtraría `welcome_shown=true` al siguiente método.
     */
    @Test
    fun welcomeShownStartsFalseAndPersistsAcrossInstances() =
        runBlocking {
            val context = RuntimeEnvironment.getApplication()
            assertFalse(SettingsRepository(context).welcomeShown.first())

            SettingsRepository(context).markWelcomeShown()

            assertTrue(SettingsRepository(context).welcomeShown.first())
        }
}
