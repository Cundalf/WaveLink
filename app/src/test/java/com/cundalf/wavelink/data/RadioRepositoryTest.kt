package com.cundalf.wavelink.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RadioRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
    }

    /** DataStore real sobre un archivo propio: aísla cada test del singleton de producción. */
    private fun newRepository(): RadioRepository {
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(tmp.root, "radios-${UUID.randomUUID()}.preferences_pb") },
            )
        return RadioRepository(dataStore)
    }

    private fun waitForState(
        repository: RadioRepository,
        predicate: (RadioState) -> Boolean,
    ): RadioState =
        runBlocking { withTimeoutOrNull(AWAIT_TIMEOUT_MS) { repository.state.first(predicate) } }
            ?: throw AssertionError("El estado no alcanzó la condición esperada")

    @Test
    fun isLoadedStartsFalseAndAwaitLoadedCompletes() {
        val repository = newRepository()

        assertFalse(repository.isLoaded)
        runBlocking { repository.awaitLoaded(AWAIT_TIMEOUT_MS) }

        assertTrue(repository.isLoaded)
        assertTrue(repository.snapshot().isEmpty())
        assertNull(repository.snapshotDefault())
    }

    @Test
    fun firstAddedRadioBecomesDefault() {
        val repository = newRepository()
        runBlocking {
            repository.add("A", URL_A, null, true, false)
            repository.add("B", URL_B, null, true, false)
        }

        val state = waitForState(repository) { it.radios.size == 2 }

        assertEquals(listOf("A"), state.radios.filter { it.esDefault }.map { it.nombre })
        assertEquals("A", state.default?.nombre)
        assertEquals(state.radios, repository.snapshot())
    }

    @Test
    fun deletingDefaultPromotesAnotherRadio() {
        val repository = newRepository()
        runBlocking {
            repository.add("A", URL_A, null, true, false)
            repository.add("B", URL_B, null, true, false)
            repository.add("C", URL_C, null, true, false)
        }
        val idA = waitForState(repository) { it.radios.size == 3 }.default!!.id

        runBlocking { repository.delete(idA) }
        val state = waitForState(repository) { it.radios.size == 2 }

        assertEquals(listOf("B"), state.radios.filter { it.esDefault }.map { it.nombre })
        assertEquals("B", state.default?.nombre)
    }

    @Test
    fun setDefaultIsExclusive() {
        val repository = newRepository()
        runBlocking {
            repository.add("A", URL_A, null, true, false)
            repository.add("B", URL_B, null, true, false)
            repository.add("C", URL_C, null, true, false)
        }
        val idC = waitForState(repository) { it.radios.size == 3 }.radios.first { it.nombre == "C" }.id

        runBlocking { repository.setDefault(idC) }
        val state = waitForState(repository) { it.default?.nombre == "C" }

        assertEquals(listOf("C"), state.radios.filter { it.esDefault }.map { it.nombre })
    }

    @Test
    fun updateChangesFieldsAndKeepsDefault() {
        val repository = newRepository()
        runBlocking {
            repository.add("A", URL_A, null, true, false)
            repository.add("B", URL_B, null, true, false)
        }
        val idB =
            waitForState(repository) { it.radios.size == 2 }.radios.first { it.nombre == "B" }.id

        runBlocking { repository.update(idB, "B nueva", URL_C, "clave", false, true) }
        val state = waitForState(repository) { it.radios.any { radio -> radio.nombre == "B nueva" } }

        val updated = state.radios.first { it.id == idB }
        assertEquals(URL_C, updated.streamUrl)
        assertEquals("clave", updated.password)
        assertFalse(updated.showNowPlaying)
        assertTrue(updated.allowRequests)
        assertEquals("A", state.default?.nombre)
        assertEquals(state.radios, repository.snapshot())
    }

    /**
     * Regresión: con un archivo corrupto, el `catch` de `state` emitía vacío y completaba
     * el upstream; las escrituras siguientes nunca llegaban al StateFlow. Usa el DataStore
     * de producción a propósito (único test que lo toca: es singleton por proceso y
     * Robolectric cambia `filesDir` entre métodos).
     */
    @Test
    fun corruptFileRecoversThroughProductionDataStoreAndKeepsUpdating() {
        val context = RuntimeEnvironment.getApplication()
        val file = File(File(context.filesDir, "datastore"), "radios.preferences_pb")
        file.parentFile?.mkdirs()
        file.writeText("esto no es un preferences_pb válido")

        val repository = RadioRepository(context)
        runBlocking { repository.awaitLoaded(AWAIT_TIMEOUT_MS) }
        assertTrue(repository.isLoaded)
        assertTrue(repository.snapshot().isEmpty())

        runBlocking { repository.add("Recuperada", URL_A, null, true, false) }
        val state = waitForState(repository) { it.radios.size == 1 }

        assertEquals(listOf("Recuperada"), state.radios.map { it.nombre })
    }

    private companion object {
        const val AWAIT_TIMEOUT_MS = 2_000L
        const val URL_A = "https://a.example/stream.mp3"
        const val URL_B = "https://b.example/stream.mp3"
        const val URL_C = "https://c.example/stream.mp3"
    }
}
