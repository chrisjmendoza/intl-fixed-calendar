package io.github.chrisjmendoza.yearal.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.chrisjmendoza.yearal.core.data.settings.DataStoreSettingsRepository
import io.github.chrisjmendoza.yearal.core.data.settings.UserSettingsSerializer
import io.github.chrisjmendoza.yearal.core.domain.settings.SettingsRepository
import io.github.chrisjmendoza.yearal.core.domain.settings.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Singleton

/**
 * Hilt wiring for user settings: the single `DataStore<UserSettings>` for the process and the
 * [SettingsRepository] binding over it (`docs/ARCHITECTURE.md` §1 "datastore", §2 "Dependency
 * direction": only `:app` depends on this module, features see the interface).
 */
@Module
@InstallIn(SingletonComponent::class)
public abstract class SettingsModule {
    /** Features inject [SettingsRepository]; the DataStore-backed implementation is the only one. */
    @Binds
    internal abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    /** Provider side of the module (Dagger reads a module's companion object as static providers). */
    public companion object {
        /**
         * File name of the settings store, relative to `filesDir/datastore/` (the DataStore default
         * location, which is inside the Auto Backup include set: `docs/ARCHITECTURE.md` §8,
         * `docs/security-and-privacy.md` §4.1). Renaming it orphans every user's settings.
         */
        internal const val FILE_NAME: String = "user_settings.json"

        /** The file the settings live in: `<filesDir>/datastore/user_settings.json`. */
        internal fun settingsFile(context: Context): File = context.dataStoreFile(FILE_NAME)

        /**
         * The one `DataStore<UserSettings>` of the process. **Must be a singleton**: DataStore throws
         * if two instances are active on the same file. A corrupt file is replaced by
         * [UserSettings.DEFAULT] (the contract in [SettingsRepository.settings]); reads and writes run
         * on [Dispatchers.IO] as the DataStore documentation recommends.
         */
        @Provides
        @Singleton
        public fun provideUserSettingsDataStore(
            @ApplicationContext context: Context,
        ): DataStore<UserSettings> =
            DataStoreFactory.create(
                serializer = UserSettingsSerializer,
                corruptionHandler = ReplaceFileCorruptionHandler { UserSettings.DEFAULT },
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                produceFile = { settingsFile(context) },
            )
    }
}
