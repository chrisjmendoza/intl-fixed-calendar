package io.github.chrisjmendoza.yearal.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.chrisjmendoza.yearal.core.data.di.SettingsModule
import io.github.chrisjmendoza.yearal.core.domain.settings.ThemeMode
import io.github.chrisjmendoza.yearal.core.domain.settings.UserSettings
import io.github.chrisjmendoza.yearal.core.domain.settings.WeekdayDisplay
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * [DataStoreSettingsRepository] over a real DataStore on a temporary file, so persistence, the
 * corruption fallback and DataStore's write serialisation are exercised for real
 * (`docs/ARCHITECTURE.md` §6, ":core:data — DataStore serializer round trip and corruption fallback").
 * Robolectric only supplies the [Context] for a temp directory and for [SettingsModule].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DataStoreSettingsRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dispatcher = UnconfinedTestDispatcher()
    private val storeJobs = mutableListOf<Job>()
    private lateinit var file: File

    @Before
    fun createFile() {
        file = File(context.cacheDir, "settings-${UUID.randomUUID()}.json")
    }

    @After
    fun releaseStores() =
        runTest(dispatcher) {
            // Completing a store's scope closes its file handle and frees the path for another store.
            storeJobs.forEach { it.cancelAndJoin() }
            file.delete()
        }

    /** A store exactly as `SettingsModule` builds it, but on [file] and a scope this test can end. */
    private fun newStore(): DataStore<UserSettings> {
        val job = Job().also { storeJobs += it }
        return DataStoreFactory.create(
            serializer = UserSettingsSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { UserSettings.DEFAULT },
            scope = CoroutineScope(dispatcher + job),
            produceFile = { file },
        )
    }

    private fun newRepository() = DataStoreSettingsRepository(newStore())

    @Test
    fun `a fresh store emits the defaults first`() =
        runTest(dispatcher) {
            newRepository().settings.first() shouldBe UserSettings.DEFAULT
        }

    @Test
    fun `update returns the stored value and re-emits it to collectors`() =
        runTest(dispatcher) {
            val repo = newRepository()
            repo.settings.test {
                awaitItem() shouldBe UserSettings.DEFAULT

                val returned = repo.update { it.copy(themeMode = ThemeMode.DARK) }
                returned shouldBe UserSettings.DEFAULT.copy(themeMode = ThemeMode.DARK)
                awaitItem() shouldBe returned

                repo.update { it.copy(weekdayDisplay = WeekdayDisplay.NOMINAL, dynamicColor = false) }
                awaitItem() shouldBe
                    UserSettings(
                        weekdayDisplay = WeekdayDisplay.NOMINAL,
                        themeMode = ThemeMode.DARK,
                        dynamicColor = false,
                        enabledHolidaySets = UserSettings.DEFAULT.enabledHolidaySets,
                    )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a value written by one repository is read by a second one on the same file`() =
        runTest(dispatcher) {
            val stored =
                UserSettings(
                    weekdayDisplay = WeekdayDisplay.ACTUAL,
                    themeMode = ThemeMode.LIGHT,
                    dynamicColor = false,
                    enabledHolidaySets = setOf("ifc"),
                )
            newRepository().update { stored } shouldBe stored
            storeJobs.single().cancelAndJoin()
            file.shouldExist()

            newRepository().settings.first() shouldBe stored
        }

    @Test
    fun `a corrupt file yields the defaults and the next update succeeds`() =
        runTest(dispatcher) {
            file.writeBytes(ByteArray(128) { (it * 31 + 7).toByte() })
            val repo = newRepository()
            repo.settings.first() shouldBe UserSettings.DEFAULT
            repo.update { it.copy(themeMode = ThemeMode.DARK) } shouldBe
                UserSettings.DEFAULT.copy(themeMode = ThemeMode.DARK)
            repo.settings.first() shouldBe UserSettings.DEFAULT.copy(themeMode = ThemeMode.DARK)
        }

    @Test
    fun `a file with an unknown enum constant yields the defaults`() =
        runTest(dispatcher) {
            file.writeText("""{"themeMode":"SEPIA"}""")
            newRepository().settings.first() shouldBe UserSettings.DEFAULT
        }

    @Test
    fun `clear returns every setting to the defaults`() =
        runTest(dispatcher) {
            val repo = newRepository()
            repo.update {
                UserSettings(
                    weekdayDisplay = WeekdayDisplay.NOMINAL,
                    themeMode = ThemeMode.DARK,
                    dynamicColor = false,
                    enabledHolidaySets = emptySet(),
                )
            }
            repo.settings.test {
                awaitItem().themeMode shouldBe ThemeMode.DARK
                repo.clear()
                awaitItem() shouldBe UserSettings.DEFAULT
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `concurrent read-modify-write updates are all applied`() =
        runTest(dispatcher) {
            val repo = newRepository()
            (1..20)
                .map { n ->
                    async { repo.update { it.copy(enabledHolidaySets = it.enabledHolidaySets + "set$n") } }
                }.awaitAll()
            val sets = repo.settings.first().enabledHolidaySets
            sets.size shouldBe UserSettings.DEFAULT.enabledHolidaySets.size + 20
            sets shouldContainAll (1..20).map { "set$it" }
        }

    @Test
    fun `the production store lives in the DataStore default location under filesDir`() {
        // docs/ARCHITECTURE.md §8: DataStore is included in Auto Backup via filesDir/datastore/.
        SettingsModule.settingsFile(context) shouldBe File(context.filesDir, "datastore/user_settings.json")
    }

    @Test
    fun `the production store starts at the defaults`() =
        runTest {
            // Robolectric gives every test its own filesDir, so this real singleton store does not
            // collide with another test's; its Dispatchers.IO scope is left to die with the JVM.
            SettingsModule.provideUserSettingsDataStore(context).data.first() shouldBe UserSettings.DEFAULT
        }
}
