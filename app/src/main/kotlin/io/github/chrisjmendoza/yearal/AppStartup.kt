package io.github.chrisjmendoza.yearal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The background work every process start owes the rest of the app, run off the main thread so that
 * `Application.onCreate` stays cheap.
 *
 * Each task runs in its own coroutine and is isolated from the others: a task that throws does not stop
 * the rest, and the failure is handed to [onFailure] rather than swallowed or allowed to take the process
 * down during start-up. Tasks carry no event content, so neither does anything reported here (CLAUDE.md
 * rule 8).
 *
 * @param scope where the tasks run; the application passes a process-lifetime scope on a background
 *   dispatcher.
 * @param onFailure called once per failed task with its name and cause. Cancellation is not a failure.
 */
class AppStartup(
    private val scope: CoroutineScope,
    private val onFailure: (name: String, cause: Exception) -> Unit = { _, _ -> },
) {
    /**
     * Starts every task in [tasks] (name → work) concurrently and returns at once.
     *
     * @return the launched jobs, in the order given, so a test can wait for them.
     */
    fun run(tasks: List<Pair<String, suspend () -> Unit>>): List<Job> =
        tasks.map { (name, work) ->
            scope.launch {
                try {
                    work()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Exception) {
                    onFailure(name, failure)
                }
            }
        }
}
