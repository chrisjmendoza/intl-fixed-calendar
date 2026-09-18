package io.github.chrisjmendoza.yearal.core.testing

import io.github.chrisjmendoza.yearal.core.domain.settings.SettingsRepository
import io.github.chrisjmendoza.yearal.core.domain.settings.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [SettingsRepository] for tests: starts at [initial], applies [update] under a mutex and
 * re-emits on every change, exactly like the DataStore-backed implementation but without I/O.
 */
public class FakeSettingsRepository(
    initial: UserSettings = UserSettings.DEFAULT,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()

    /** Emits the current value immediately, then every value stored by [update]. */
    override val settings: Flow<UserSettings> = state.asStateFlow()

    /** The value most recently stored, for assertions that do not want to collect the flow. */
    public val current: UserSettings get() = state.value

    /** Applies [transform] to the current value under a mutex and stores the result. */
    override suspend fun update(transform: (UserSettings) -> UserSettings): UserSettings =
        mutex.withLock {
            val next = transform(state.value)
            state.value = next
            next
        }
}
