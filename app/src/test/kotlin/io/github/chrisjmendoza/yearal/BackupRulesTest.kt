package io.github.chrisjmendoza.yearal

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses `data_extraction_rules.xml` and `full_backup_content.xml` against
 * `docs/security-and-privacy.md` §4.1 "Android Auto Backup & device-to-device transfer" and
 * `docs/ARCHITECTURE.md` §3.2, now that a Room database and a DataStore file exist to back up:
 *
 * - Cloud backup includes the database and DataStore directories, restricted to a device that can
 *   encrypt client-side (`disableIfNoEncryptionCapabilities="true"`).
 * - Device-to-device transfer includes the same two directories with no encryption restriction
 *   (a user-initiated, already-authenticated transfer).
 * - The legacy `full_backup_content.xml` (read on API ≤ 30) mirrors both includes, each requiring
 *   `clientSideEncryption` directly (the pre-`disableIfNoEncryptionCapabilities` mechanism).
 *
 * **`YearalDatabase.FILE_NAME`** (`"yearal.db"`) is `private` and **`SettingsModule.FILE_NAME`**
 * (`"user_settings.json"`) is `internal` to `:core:data` — neither is part of that module's public
 * API, so this test cannot read them and asserts the literals directly instead (with a comment tying
 * each to the storage API that places it, per the task's guidance to report this rather than reaching
 * into `:core:data`'s internals). A `path="."` / `path="datastore/"` **directory** include, not a
 * filename, is what the production rules actually use — see the two tests at the bottom — and that is
 * what is asserted to "cover" each literal.
 */
class BackupRulesTest {
    private val dataExtractionRules = parseXmlResource("data_extraction_rules.xml").documentElement
    private val fullBackupContent = parseXmlResource("full_backup_content.xml").documentElement

    // ----- data_extraction_rules.xml (API 31+) -----

    @Test
    fun `cloud backup is restricted to a device that can encrypt client-side`() {
        val cloudBackup = dataExtractionRules.requireChild("cloud-backup")

        cloudBackup.getAttribute("disableIfNoEncryptionCapabilities") shouldBe "true"
    }

    @Test
    fun `cloud backup includes the database directory and the datastore directory`() {
        val includes = dataExtractionRules.requireChild("cloud-backup").children("include")

        includes.map { it.domainAndPath() } shouldContainExactlyInAnyOrder
            listOf("database" to ".", "file" to "datastore/")
    }

    @Test
    fun `device-to-device transfer carries the same two directories with no encryption restriction`() {
        val deviceTransfer = dataExtractionRules.requireChild("device-transfer")

        deviceTransfer.hasAttribute("disableIfNoEncryptionCapabilities") shouldBe false
        deviceTransfer.children("include").map { it.domainAndPath() } shouldContainExactlyInAnyOrder
            listOf("database" to ".", "file" to "datastore/")
    }

    // ----- full_backup_content.xml (API <= 30) -----

    @Test
    fun `the legacy file mirrors both includes, each requiring client-side encryption directly`() {
        val includes = fullBackupContent.children("include")

        includes.size shouldBe 2
        includes.forEach { it.getAttribute("requireFlags") shouldBe "clientSideEncryption" }
        includes.map { it.domainAndPath() } shouldContainExactlyInAnyOrder
            listOf("database" to ".", "file" to "datastore/")
    }

    // ----- The included directories actually cover the real database and DataStore file names -----

    @Test
    fun `the database domain's whole directory is included, covering the WAL and SHM files too`() {
        // Room's production database, "yearal.db" (core/data's YearalDatabase.FILE_NAME, private to
        // that module), is opened with a bare file name, so Room resolves it through
        // Context.getDatabasePath(name) — inside the "database" backup domain. WAL mode adds
        // "yearal.db-wal" / "yearal.db-shm" next to it; a whole-directory include (path="."), not a
        // named file, is what catches those without listing them.
        val databaseInclude =
            dataExtractionRules
                .requireChild("cloud-backup")
                .children("include")
                .single { it.getAttribute("domain") == "database" }

        databaseInclude.getAttribute("path") shouldBe "."
    }

    @Test
    fun `the file domain's datastore directory covers the settings file`() {
        // DataStore's production file, "user_settings.json" (core/data's SettingsModule.FILE_NAME,
        // internal to that module), is opened through Context.dataStoreFile(name), which
        // androidx.datastore places at "<filesDir>/datastore/<name>" — the "file" domain's
        // "datastore/" path is exactly that directory.
        val fileInclude =
            dataExtractionRules
                .requireChild("cloud-backup")
                .children("include")
                .single { it.getAttribute("domain") == "file" }

        fileInclude.getAttribute("path") shouldBe "datastore/"
    }
}

private fun Element.domainAndPath(): Pair<String, String> = getAttribute("domain") to getAttribute("path")

/** Direct child elements named [tagName] (not all descendants — `getElementsByTagName` is recursive). */
private fun Element.children(tagName: String): List<Element> =
    (0 until childNodes.length)
        .map { childNodes.item(it) }
        .filterIsInstance<Element>()
        .filter { it.tagName == tagName }

private fun Element.requireChild(tagName: String): Element =
    children(tagName).singleOrNull()
        ?: error("Expected exactly one <$tagName> under <${this.tagName}>, found none or several")

/**
 * Parses `app/src/main/res/xml/$name` directly off disk: a resource-id-based read (`R.xml.…` through
 * Robolectric) would still be reading the same compiled file, and a plain file read needs no Android
 * runtime. Resolved relative to this module's directory and falling back to the repo layout, so the
 * test does not depend on the Gradle test task's working directory.
 */
private fun parseXmlResource(name: String): Document {
    val candidates =
        listOf(
            File("src/main/res/xml/$name"),
            File("app/src/main/res/xml/$name"),
        )
    val file =
        candidates.firstOrNull { it.isFile }
            ?: error("Could not find $name under any of $candidates (cwd=${File(".").absolutePath})")
    return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
}
