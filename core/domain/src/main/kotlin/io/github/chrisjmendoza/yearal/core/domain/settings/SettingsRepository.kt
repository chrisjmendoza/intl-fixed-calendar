package io.github.chrisjmendoza.yearal.core.domain.settings

import kotlinx.coroutines.flow.Flow

/**
 * Read and update the user's [UserSettings]. Implemented over DataStore in `:core:data`; features
 * depend only on this interface (CLAUDE.md rule 10) and are tested with
 * `io.github.chrisjmendoza.yearal.core.testing.FakeSettingsRepository`.
 */
public interface SettingsRepository {
    /**
     * The current settings, emitted immediately on collection and again after every change.
     * A missing or unreadable store yields [UserSettings.DEFAULT] rather than an error.
     */
    public val settings: Flow<UserSettings>

    /**
     * Atomically replaces the stored settings with `transform(current)`. Returns the value stored.
     * Concurrent callers are serialised, so read-modify-write through [transform] never loses an
     * update.
     */
    public suspend fun update(transform: (UserSettings) -> UserSettings): UserSettings
}
