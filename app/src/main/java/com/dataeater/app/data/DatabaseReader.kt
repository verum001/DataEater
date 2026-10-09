package com.dataeater.app.data

import android.util.Base64
import com.dataeater.app.model.Chunk
import com.dataeater.app.model.DatabaseManifest
import com.dataeater.app.model.SourceInfo
import com.dataeater.app.security.DeviceKeys
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Opens a `.dataeater` database file and turns it into ordinary Kotlin objects.
 *
 * A PLAIN `.dataeater` is a ZIP containing:
 *   manifest.json   - what this database is   (always first)
 *   sources.json    - which documents it holds
 *   chunks.jsonl    - the text itself, one JSON object per line
 *
 * A LOCKED `.dataeater` is a ZIP containing:
 *   manifest.json   - the same, plus "encryption" and the payload nonce
 *   payload.enc     - sources + chunks, encrypted, as one AES-GCM blob
 *
 * The parsing code is shared between the two, so a locked database is read
 * by exactly the same logic once it has been decrypted.
 *
 * We use the ZIP reader that is built into Android, so there is nothing
 * extra to install.
 */
object DatabaseReader {

    /** The newest format version this version of the app understands. */
    const val SUPPORTED_FORMAT_VERSION = 1

    private const val MANIFEST_FILE = "manifest.json"
    private const val SOURCES_FILE = "sources.json"
    private const val CHUNKS_FILE = "chunks.jsonl"
    private const val PAYLOAD_FILE = "payload.enc"

    /** Thrown when a database file cannot be understood. Shown to the user. */
    open class DataEaterException(message: String) : Exception(message)

    class IntegrityException(message: String) : DataEaterException(message)

    /** A database that has been opened successfully. */
    data class OpenedDatabase(
        val manifest: DatabaseManifest,
        val chunks: List<Chunk>,
        val sources: List<SourceInfo>,
        val fileName: String = "",
    ) {
        /** Title of a source document, or the raw id if the id is unknown. */
        fun titleOfSource(sourceId: String): String =
            sources.firstOrNull { it.id == sourceId }?.title ?: sourceId
    }

    // ------------------------------------------------------------------
    // Opening
    // ------------------------------------------------------------------

    /** Opens a plain database from a real file on the phone. */
    fun openFile(file: java.io.File): OpenedDatabase {
        requireExists(file)
        return openPlain({ file.inputStream() }, file.name)
    }

    private fun openPlain(openStream: () -> InputStream, fileName: String): OpenedDatabase {
        val manifestText = readTextFromPackage(openStream, MANIFEST_FILE)
        val manifest = parseManifest(manifestText)
        val sources = parseSources(String(
            readVerifiedBytes(openStream, manifestText, SOURCES_FILE), Charsets.UTF_8,
        ))
        val chunks = parseChunks(String(
            readVerifiedBytes(openStream, manifestText, CHUNKS_FILE), Charsets.UTF_8,
        ))
        return OpenedDatabase(
            manifest = manifest,
            chunks = chunks,
            sources = sources,
            fileName = fileName,
        )
    }

    /** Verify the same bytes that will be parsed or decrypted, before using them. */
    private fun readVerifiedBytes(
        openStream: () -> InputStream,
        manifestText: String,
        name: String,
    ): ByteArray {
        val bytes = readBytesFromPackage(openStream, name)
        DatabaseIntegrity.verify(manifestText, name, bytes)
        return bytes
    }

    /**
     * Opens a LOCKED database by decrypting the payload with the content key
     * recovered from an Unlock Code.
     *
     * After the AES step the data is the same two text files a plain database
     * has, so the ordinary parsing below is reused and there is only ever one
     * set of parsing rules to keep working.
     */
    fun openLocked(file: java.io.File, contentKey: ByteArray): OpenedDatabase {
        requireExists(file)

        val manifestText = readTextFromPackage({ file.inputStream() }, MANIFEST_FILE)
        val manifest = parseManifest(manifestText)
        if (manifest.encryption.isBlank() || manifest.encryption == "none") {
            throw DataEaterException("This database is not encrypted.")
        }

        val nonceBase64 = JSONObject(manifestText).optString("payload_nonce")
        if (nonceBase64.isBlank()) {
            throw DataEaterException("This database is damaged: it has no payload nonce.")
        }

        val ciphertext = readVerifiedBytes({ file.inputStream() }, manifestText, PAYLOAD_FILE)
        val plain = try {
            DeviceKeys.gcmDecrypt(
                contentKey,
                Base64.decode(nonceBase64, Base64.DEFAULT),
                ciphertext,
            )
        } catch (problem: Exception) {
            throw DataEaterException(
                "The knowledge inside could not be decrypted. This Unlock Code " +
                    "is probably for a different database."
            )
        }

        val envelope = try {
            JSONObject(String(plain, Charsets.UTF_8))
        } catch (problem: Exception) {
            throw DataEaterException("The decrypted data could not be read.")
        }
        if (envelope.optString("format") != "dataeater-payload") {
            throw DataEaterException("This is not a DataEater payload.")
        }

        return OpenedDatabase(
            manifest = manifest,
            chunks = parseChunks(envelope.optString("chunks")),
            sources = parseSources(envelope.optString("sources")),
            fileName = file.name,
        )
    }

    /**
     * Reads only the manifest, without touching the knowledge inside.
     *
     * A locked database has no readable chunks, so the app must be able to
     * look at the header first and say "this one is encrypted" instead of
     * failing with a confusing missing-file error.
     */
    fun readManifestOnly(file: java.io.File): DatabaseManifest {
        requireExists(file)
        return readManifest { file.inputStream() }
    }

    /** The raw manifest text, for fields we do not model but still need. */
    fun manifestTextOf(file: java.io.File): String {
        requireExists(file)
        return readTextFromPackage({ file.inputStream() }, MANIFEST_FILE)
    }

    private fun requireExists(file: java.io.File) {
        if (!file.exists()) {
            throw DataEaterException("The file ${file.name} is not there any more.")
        }
    }
    // ------------------------------------------------------------------
    // manifest.json
    // ------------------------------------------------------------------

    private fun readManifest(openStream: () -> InputStream): DatabaseManifest =
        parseManifest(readTextFromPackage(openStream, MANIFEST_FILE))

    /**
     * Turns manifest text into a [DatabaseManifest], refusing anything this
     * app cannot read correctly.
     *
     * Public so `DatabaseLock` can classify a file from its manifest text
     * without going through the ZIP reader, which makes it testable.
     *
     * @throws DataEaterException when the text is not a manifest we understand
     */
    fun parseManifest(text: String): DatabaseManifest {
        val json = JSONObject(text)

        val format = json.optString("format")
        if (format != "dataeater") {
            throw DataEaterException(
                "This file is not a DataEater database (format='$format')."
            )
        }

        val version = json.optInt("format_version", -1)
        if (version < 1) {
            throw DataEaterException("The database has no valid format_version field.")
        }

        // Refuse a database that is NEWER than we understand. Opening it
        // anyway would risk reading fields in the wrong way and showing
        // wrong information to the user.
        if (version > SUPPORTED_FORMAT_VERSION) {
            throw DataEaterException(
                "This database uses format version $version. " +
                    "This app supports up to version $SUPPORTED_FORMAT_VERSION. " +
                    "Please update DataEater."
            )
        }

        val counts = json.optJSONObject("counts")

        return DatabaseManifest(
            databaseId = json.optString("database_id", "unknown"),
            name = json.optString("name", "Unnamed database"),
            description = json.optString("description", ""),
            formatVersion = version,
            language = json.optString("language", "unknown"),
            license = json.optString("license", "unknown"),
            encryption = json.optString("encryption", "none"),
            creator = json.optString("creator", ""),
            contact = json.optString("contact", ""),
            creatorPublicKey = json.optString("creator_public_key", ""),
            documentCount = counts?.optInt("documents", 0) ?: 0,
            chunkCount = counts?.optInt("chunks", 0) ?: 0,
        )
    }

    // ------------------------------------------------------------------
    // sources.json  and  chunks.jsonl
    //
    // Reading the file and parsing the text are separate on purpose, so the
    // same parsing works on text that came out of an encrypted payload.
    // ------------------------------------------------------------------

    fun parseSources(text: String): List<SourceInfo> {
        val array = JSONArray(text)
        val result = ArrayList<SourceInfo>(array.length())

        for (index in 0 until array.length()) {
            val obj = array.getJSONObject(index)
            result.add(
                SourceInfo(
                    id = obj.optString("id"),
                    title = obj.optString("title"),
                    publisher = obj.optString("publisher"),
                    year = obj.optInt("year", 0),
                )
            )
        }
        return result
    }

    fun parseChunks(text: String): List<Chunk> {
        val result = ArrayList<Chunk>()

        // JSONL means "one JSON object per line", so we simply read line by line.
        text.lineSequence().forEach { line ->
            if (line.isBlank()) return@forEach
            val obj = JSONObject(line)
            result.add(
                Chunk(
                    id = obj.optString("id"),
                    sourceId = obj.optString("source_id"),
                    section = obj.optString("section"),
                    page = obj.optInt("page", 0),
                    text = obj.optString("text"),
                    searchContext = obj.optString("search_context", "").take(2000),
                )
            )
        }
        return result
    }

    // ------------------------------------------------------------------
    // Reading files out of the ZIP
    // ------------------------------------------------------------------

    /**
     * Finds one file inside the `.dataeater` package and returns its text.
     *
     * The package is a ZIP, so we walk through the entries one by one and stop
     * when we find the name we want.
     *
     * A database usually came from somewhere else, so it is untrusted input and
     * a ZIP is a well-known way to attack a program. Reading is therefore
     * counted and capped — see [DatabaseLimits].
     */
    private fun readTextFromPackage(
        openStream: () -> InputStream,
        wantedFile: String,
    ): String =
        String(readBytesFromPackage(openStream, wantedFile), Charsets.UTF_8)

    private fun readBytesFromPackage(
        openStream: () -> InputStream,
        wantedFile: String,
    ): ByteArray =
        readBytesFromPackage(
            openStream,
            wantedFile,
            DatabaseLimits.MAX_ENTRY_BYTES,
            DatabaseLimits.MAX_ENTRIES,
        )

    /**
     * The reading itself, with the limits passed in.
     *
     * Production always passes the constants from [DatabaseLimits]. The
     * parameters exist so the bomb tests can drive **this** function with a
     * small limit: a genuine 256 MB bomb cannot be built inside a unit test
     * without spending the very memory the limit exists to protect.
     *
     * Testing a copy of the counting logic would prove nothing, which is why
     * the real function is what the tests call.
     */
    internal fun readBytesFromPackage(
        openStream: () -> InputStream,
        wantedFile: String,
        limitBytes: Long,
        maxEntries: Int,
        maxScanBytes: Long = DatabaseLimits.MAX_SCAN_BYTES,
    ): ByteArray {
        openStream().use { rawInput ->
            ZipInputStream(rawInput).use { zip ->
                var entry = zip.nextEntry
                var seen = 0
                var remaining = maxScanBytes
                while (entry != null) {
                    seen += 1
                    if (seen > maxEntries) {
                        throw DataEaterException(
                            "This database has more than $maxEntries files inside " +
                                "it, which no real database has. It is either " +
                                "damaged or made to exhaust memory. It has not " +
                                "been opened."
                        )
                    }
                    if (!entry.isDirectory && entry.name == wantedFile) {
                        return readEntryWithLimit(zip, wantedFile, limitBytes, remaining, maxScanBytes)
                    }
                    // closeEntry() otherwise drains an unknown entry without
                    // counting decompression. Count it without retaining bytes.
                    remaining -= discardEntryWithLimit(zip, entry.name, limitBytes, remaining, maxScanBytes)
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        throw DataEaterException(
            "The file '$wantedFile' is missing. The database is damaged."
        )
    }

    private fun discardEntryWithLimit(stream: InputStream, name: String, limit: Long, remaining: Long, scanLimit: Long): Long {
        val buffer = ByteArray(DatabaseLimits.READ_CHUNK_BYTES)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) return total
            total += read
            if (total > limit) throw DataEaterException(DatabaseLimits.tooLargeMessage(name, limit))
            if (total > remaining) throw DataEaterException(scanLimitMessage(scanLimit))
        }
    }

    private fun scanLimitMessage(limit: Long): String =
        "This database is too large to open safely. Reading its files expands to more than " +
            "${limit / (1024 * 1024)} MB in one scan. It has not been opened."

    /**
     * Reads one ZIP entry, refusing to go past [DatabaseLimits.MAX_ENTRY_BYTES].
     *
     * THE IMPORTANT PART IS *WHERE* THE CHECK IS
     * -------------------------------------------
     * It is inside the loop, before the bytes are kept — not after. The
     * obvious version of this code is
     *
     *     val bytes = zip.readBytes()
     *     if (bytes.size > limit) throw ...
     *
     * which is useless: by the time the size is known, the phone has already
     * tried to hold the whole thing in memory, and a bomb has already won.
     *
     * Reading a chunk at a time means the app never holds more than the limit
     * plus one small buffer, no matter how large the entry claims to be.
     *
     * The declared size in the ZIP header is deliberately NOT trusted, because
     * a crafted file can claim anything at all there. Only the bytes actually
     * produced by decompression are counted.
     */
    internal fun readEntryWithLimit(
        entryStream: InputStream,
        entryName: String,
        limitBytes: Long,
        scanRemaining: Long = Long.MAX_VALUE,
        scanLimit: Long = DatabaseLimits.MAX_SCAN_BYTES,
    ): ByteArray {
        val buffer = ByteArray(DatabaseLimits.READ_CHUNK_BYTES)
        val collected = java.io.ByteArrayOutputStream()

        while (true) {
            val read = try {
                entryStream.read(buffer)
            } catch (problem: OutOfMemoryError) {
                // The size cap is the real guard, but a phone can still run out
                // of memory before the cap is reached — Android's per-app heap
                // is smaller than a generous-looking file limit. Turning that
                // into the same clear message is far better than letting the
                // app be killed with no explanation at all.
                throw DataEaterException(
                    DatabaseLimits.tooLargeMessage(entryName, limitBytes)
                )
            } catch (problem: Exception) {
                throw DataEaterException(
                    "The file '$entryName' inside the database could not be read. " +
                        "It is damaged."
                )
            }
            if (read < 0) break

            if (collected.size().toLong() + read > limitBytes) {
                throw DataEaterException(
                    DatabaseLimits.tooLargeMessage(entryName, limitBytes)
                )
            }
            if (collected.size().toLong() + read > scanRemaining) {
                throw DataEaterException(scanLimitMessage(scanLimit))
            }
            try {
                collected.write(buffer, 0, read)
            } catch (problem: OutOfMemoryError) {
                throw DataEaterException(
                    DatabaseLimits.tooLargeMessage(entryName, limitBytes)
                )
            }
        }
        return collected.toByteArray()
    }
}
