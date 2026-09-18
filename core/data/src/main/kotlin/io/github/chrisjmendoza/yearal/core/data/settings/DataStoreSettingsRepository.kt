package io.github.chrisjmendoza.yearal.core.data.settings

import androidx.datastore.core.DataStore
import io.github.chrisjmendoza.yearal.core.domain.settings.SettingsRepository
import io.github.chrisjmendoza.yearal.core.domain.settings.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException
import javax.inject.Inject

/**
 * [SettingsRepository] over the typed `DataStore<UserSettings>` provided by `SettingsModule`
 * (`docs/ARCHITECTURE.md` §1 "datastore"; FEATURES W1, W2, H5).
 *
 * The contract's "a missing or unreadable store yields [UserSettings.DEFAULT]" is met in two layers:
 * a missing file is DataStore's own default, a corrupt file is replaced with the defaults by the
 * corruption handler, and any other [IOException] while reading (for example a permissions or disk
 * failure) is turned into a [UserSettings.DEFAULT] emission here. Anything that is not an
 * [IOException] is a programming error and propagates.
 */
public class DataStoreSettingsRepository
    @Inject
    constructor(
        private val store: DataStore<UserSettings>,
    ) : SettingsRepository {
        /**
         * The stored settings, emitted on collection and after every successful [update]. An
         * unreadable store yields [UserSettings.DEFAULT] instead of an error (see the class KDoc).
         */
        override val settings: Flow<UserSettings> =
            store.data.catch { e ->
                if (e is IOException) emit(UserSettings.DEFAULT) else throw e
            }

        /**
         * Atomically replaces the stored settings with `transform(current)` and returns the stored
         * value. DataStore serialises concurrent callers, so no read-modify-write is lost, and the
         * new value is durably written before this returns.
         */
        override suspend fun update(transform: (UserSettings) -> UserSettings): UserSettings =
            store.updateData(transform)

        /**
         * Resets every setting to [UserSettings.DEFAULT], durably. Intended for the "Delete all data"
         * action (`docs/security-and-privacy.md` §2.4); collectors of [settings] see the defaults.
         */
        public suspend fun clear() {
            store.updateData { UserSettings.DEFAULT }
        }
    }
