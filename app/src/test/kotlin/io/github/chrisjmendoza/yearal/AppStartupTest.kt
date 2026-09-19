package io.github.chrisjmendoza.yearal

import io.github.chrisjmendoza.yearal.core.testing.FakeReminderScheduler
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** [AppStartup]: every start-up task runs once, and a failing or hanging task never affects the others. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppStartupTest {
    @Test
    fun `every task runs exactly once`() =
        runTest {
            val reminders = FakeReminderScheduler()
            var previews = 0

            AppStartup(this)
                .run(listOf("reminders" to { reminders.reschedule() }, "widgetPreviews" to { previews++ }))
                .joinAll()

            reminders.rescheduleCount shouldBe 1
            previews shouldBe 1
        }

    @Test
    fun `nothing runs on the calling thread before run returns`() {
        val scope = TestScope(StandardTestDispatcher())
        val reminders = FakeReminderScheduler()

        AppStartup(scope).run(listOf("reminders" to { reminders.reschedule() }))
        reminders.rescheduleCount shouldBe 0

        scope.advanceUntilIdle()
        reminders.rescheduleCount shouldBe 1
    }

    @Test
    fun `a failing task is reported and does not stop the others`() =
        runTest {
            val reminders = FakeReminderScheduler()
            val failures = mutableListOf<String>()

            AppStartup(this) { name, cause -> failures += "$name:${cause.message}" }
                .run(
                    listOf(
                        "widgetPreviews" to { throw IllegalStateException("boom") },
                        "reminders" to { reminders.reschedule() },
                    ),
                ).joinAll()

            failures shouldContainExactly listOf("widgetPreviews:boom")
            reminders.rescheduleCount shouldBe 1
        }

    @Test
    fun `a hanging task does not delay the others and cancellation is not a failure`() =
        runTest {
            val reminders = FakeReminderScheduler()
            val failures = mutableListOf<String>()

            val jobs =
                AppStartup(this) { name, _ -> failures += name }
                    .run(listOf("hang" to { awaitCancellation() }, "reminders" to { reminders.reschedule() }))
            jobs[1].join()
            reminders.rescheduleCount shouldBe 1

            jobs[0].cancelAndJoin()
            failures shouldBe emptyList()
        }
}
