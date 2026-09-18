package io.github.chrisjmendoza.yearal.core.holidays

// Reads the oracle tables under src/test/resources/oracle. Those files are inputs copied from the
// published pages named in their header comments (WORKFLOW.md §4.2, CLAUDE.md rule 12): a test that
// disagrees with them is a bug in the pack or the engine, never a reason to edit the table.
internal object OracleCsv {
    /** Returns the data rows of `/oracle/<name>` as column maps keyed by the header row. */
    fun rows(name: String): List<Map<String, String>> {
        val stream = checkNotNull(javaClass.getResourceAsStream("/oracle/$name")) { "missing oracle $name" }
        val lines =
            stream
                .use { it.readBytes().toString(Charsets.UTF_8) }
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toList()
        check(lines.isNotEmpty()) { "oracle $name has no header row" }
        val header = lines.first().split(',')
        return lines.drop(1).map { line ->
            val cells = line.split(',')
            check(cells.size == header.size) { "oracle $name: row '$line' does not match header $header" }
            header.zip(cells).toMap()
        }
    }
}
