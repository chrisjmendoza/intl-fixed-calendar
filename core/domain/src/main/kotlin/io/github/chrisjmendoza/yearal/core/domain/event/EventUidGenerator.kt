package io.github.chrisjmendoza.yearal.core.domain.event

import java.util.UUID

/**
 * Supplies the [Event.uid] of a new event. Injected into whatever creates events (the editor's
 * ViewModel) so that tests get predictable uids from
 * `io.github.chrisjmendoza.yearal.core.testing.FakeEventUidGenerator`.
 */
public fun interface EventUidGenerator {
    /** A uid no other event has: not blank, stable once assigned, suitable as an `.ics` `UID`. */
    public fun newUid(): String
}

/** [EventUidGenerator] producing random (version 4) UUIDs in their canonical 36-character form. */
public object RandomEventUidGenerator : EventUidGenerator {
    override fun newUid(): String = UUID.randomUUID().toString()
}
