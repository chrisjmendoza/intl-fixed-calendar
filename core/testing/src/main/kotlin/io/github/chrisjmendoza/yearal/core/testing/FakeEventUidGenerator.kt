package io.github.chrisjmendoza.yearal.core.testing

import io.github.chrisjmendoza.yearal.core.domain.event.EventUidGenerator

/**
 * Deterministic [EventUidGenerator]: `uid-1`, `uid-2`, … (with [prefix] in place of `uid-`), so a test
 * can predict the uid of the event an editor is about to save.
 */
public class FakeEventUidGenerator(
    private val prefix: String = "uid-",
) : EventUidGenerator {
    private var next = 1

    /** How many uids have been handed out. */
    public val issued: Int get() = next - 1

    /** The next uid in the sequence. */
    override fun newUid(): String = prefix + next++
}
