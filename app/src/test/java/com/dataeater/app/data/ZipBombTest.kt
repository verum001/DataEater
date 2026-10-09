package com.dataeater.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Proving the app cannot be made to run out of memory by opening a file.
 *
 * THE ATTACK
 * ----------
 * A "zip bomb" is a small file that decompresses into an enormous one. A few
 * kilobytes can expand to gigabytes. If the reader holds the whole thing in
 * memory, Android kills the app for using too much — and the user sees no
 * error at all, just a crash when they open a file someone sent them.
 *
 * These tests build real bombs and check they are refused, and build real
 * databases and check they are not.
 *
 * NOTE ON THE LIMIT ITSELF
 * ------------------------
 * The production entry limit is 32 MiB. Tests inject smaller limits instead
 * of allocating the production limit — the very memory being
 * protected. So the tests exercise the same counting code with a small limit
 * injected, and one test confirms the shipped limit is the generous one.
 */
class ZipBombTest {

    @Test fun oversizedUnknownEntryBeforeManifestIsRefused() {
        val file = tempFile()
        try {
            writeZip(file, linkedMapOf("unknown" to ByteArray(4096), "manifest.json" to "{}".toByteArray()))
            val problem = assertThrows(DatabaseReader.DataEaterException::class.java) {
                readWithLimit(file, "manifest.json", 1024)
            }
            assertTrue(problem.message!!.contains("unknown"))
        } finally { file.delete() }
    }

    @Test fun skippedDirectoryPayloadIsCountedToo() {
        val file = tempFile()
        try {
            writeZip(file, linkedMapOf("unknown/" to ByteArray(4096), "manifest.json" to "{}".toByteArray()))
            assertThrows(DatabaseReader.DataEaterException::class.java) {
                readWithLimit(file, "manifest.json", 1024)
            }
        } finally { file.delete() }
    }

    @Test fun multipleSkippedEntriesCannotExceedScanBudget() {
        val file = tempFile()
        try {
            writeZip(file, linkedMapOf("a" to ByteArray(700), "b" to ByteArray(700), "manifest.json" to "{}".toByteArray()))
            assertThrows(DatabaseReader.DataEaterException::class.java) {
                DatabaseReader.readBytesFromPackage({ file.inputStream() }, "manifest.json", 1024, 10, maxScanBytes = 1200)
            }
        } finally { file.delete() }
    }

    @Test fun requestedEntryAlsoCountsTowardsScanBudget() {
        val file = tempFile()
        try {
            writeZip(file, linkedMapOf("a" to ByteArray(700), "manifest.json" to ByteArray(700)))
            assertThrows(DatabaseReader.DataEaterException::class.java) {
                DatabaseReader.readBytesFromPackage({ file.inputStream() }, "manifest.json", 1024, 10, maxScanBytes = 1200)
            }
            assertEquals(700, DatabaseReader.readBytesFromPackage({ file.inputStream() }, "manifest.json", 1024, 10, maxScanBytes = 1400).size)
        } finally { file.delete() }
    }

    /**
     * The limit these tests run against.
     *
     * `DatabaseLimits.MAX_ENTRY_BYTES` is a `const val`, so it cannot be
     * swapped at runtime. The counting therefore lives in a function that
     * takes the limit, and production passes the real one.
     */
    private val testLimit = 512L * 1024      // 512 KB
    private val chunkSize = 64 * 1024

    // ------------------------------------------------------------------
    // Building the weapons
    // ------------------------------------------------------------------

    /** A ZIP holding one entry that expands to `decompressedBytes` of zeros. */
    private fun bomb(decompressedBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("chunks.jsonl"))
            // Highly compressible: a megabyte of zeros costs about a kilobyte.
            val block = ByteArray(64 * 1024)
            var written = 0
            while (written < decompressedBytes) {
                val step = minOf(block.size, decompressedBytes - written)
                zip.write(block, 0, step)
                written += step
            }
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun writeZip(file: java.io.File, entries: Map<String, ByteArray>) {
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun tempFile(suffix: String = ".dataeater"): java.io.File =
        java.io.File.createTempFile("zipeater", suffix)

    // ------------------------------------------------------------------
    // A real bomb must be refused
    // ------------------------------------------------------------------

    /**
     * The counting rule, exercised directly because production passes a much
     * larger limit: bytes are counted as they are produced, and the moment the
     * total would pass the limit the read stops.
     */
    @Test
    fun aBombIsRefusedRatherThanRead() {
        val bombFile = tempFile()
        try {
            bombFile.writeBytes(bomb(decompressedBytes = 64 * testLimit.toInt()))
            assertTrue(
                "the bomb should be far smaller than what it expands to",
                bombFile.length() < testLimit,
            )

            val problem = assertThrows(DatabaseReader.DataEaterException::class.java) {
                readWithLimit(bombFile, "chunks.jsonl", testLimit)
            }
            assertTrue(
                "message should explain the file is too large, was: ${problem.message}",
                problem.message!!.contains("too large to open safely"),
            )
        } finally {
            bombFile.delete()
        }
    }

    /** The refusal must name the offending part, so the user can report it. */
    @Test
    fun theRefusalNamesThePartThatWasTooLarge() {
        val bombFile = tempFile()
        try {
            bombFile.writeBytes(bomb(decompressedBytes = 8 * testLimit.toInt()))
            val problem = assertThrows(DatabaseReader.DataEaterException::class.java) {
                readWithLimit(bombFile, "chunks.jsonl", testLimit)
            }
            assertTrue(
                "message should name 'chunks.jsonl', was: ${problem.message}",
                problem.message!!.contains("chunks.jsonl"),
            )
        } finally {
            bombFile.delete()
        }
    }

    /**
     * A bomb whose *manifest* is the problem. `manifest.json` is read first
     * and must be capped too, or a hostile file gets a free pass by putting
     * the bomb in the first entry.
     */
    @Test
    fun aBombInTheManifestIsAlsoRefused() {
        val bombFile = tempFile()
        try {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                val block = ByteArray(64 * 1024)
                var written = 0
                val target = 8 * testLimit.toInt()
                while (written < target) {
                    val step = minOf(block.size, target - written)
                    zip.write(block, 0, step)
                    written += step
                }
                zip.closeEntry()
            }
            bombFile.writeBytes(out.toByteArray())

            assertThrows(DatabaseReader.DataEaterException::class.java) {
                readWithLimit(bombFile, "manifest.json", testLimit)
            }
        } finally {
            bombFile.delete()
        }
    }

    /**
     * Millions of tiny entries is the other shape of the attack: the reader
     * spends its time walking entries and never finds what it wants.
     */
    @Test
    fun tooManyEntriesIsRefused() {
        val bombFile = tempFile()
        try {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                repeat(50) { index ->
                    zip.putNextEntry(ZipEntry("filler-$index"))
                    zip.write("x".toByteArray())
                    zip.closeEntry()
                }
            }
            bombFile.writeBytes(out.toByteArray())

            val problem = assertThrows(DatabaseReader.DataEaterException::class.java) {
                readWithLimit(bombFile, "chunks.jsonl", testLimit, maxEntries = 10)
            }
            assertTrue(
                "message should mention the file count, was: ${problem.message}",
                problem.message!!.contains("files inside it"),
            )
        } finally {
            bombFile.delete()
        }
    }

    // ------------------------------------------------------------------
    // Real databases must still work
    // ------------------------------------------------------------------

    /** An ordinary database is not caught by any of this. */
    @Test
    fun anOrdinaryFileIsReadNormally() {
        val good = tempFile()
        try {
            val text = "the quick brown fox jumps over the lazy dog".repeat(200)
            writeZip(good, mapOf("chunks.jsonl" to text.toByteArray()))

            val result = readWithLimit(good, "chunks.jsonl", testLimit)
            assertEquals(text, String(result, Charsets.UTF_8))
        } finally {
            good.delete()
        }
    }

    /** A file exactly at the limit is allowed; one byte more is not. */
    @Test
    fun theLimitIsInclusiveAndExact() {
        val atLimit = tempFile()
        val overLimit = tempFile()
        try {
            val exact = ByteArray(testLimit.toInt()) { 'a'.code.toByte() }
            writeZip(atLimit, mapOf("chunks.jsonl" to exact))
            assertEquals(testLimit.toInt(), readWithLimit(atLimit, "chunks.jsonl", testLimit).size)

            writeZip(overLimit, mapOf("chunks.jsonl" to exact + "b".toByteArray()))
            assertThrows(DatabaseReader.DataEaterException::class.java) {
                readWithLimit(overLimit, "chunks.jsonl", testLimit)
            }
        } finally {
            atLimit.delete()
            overLimit.delete()
        }
    }

    /**
     * Two boundaries, and both matter.
     *
     * The upper one: the limit must stay above a real manual, or honest users
     * are refused. Measured largest real database is about 1.1 MB; a
     * hypothetical 10,000-page manual is roughly 17 MB. 32 MB clears that.
     *
     * The lower one, and this is the lesson of this whole exercise: the limit
     * must stay well BELOW Android's per-app heap. A first attempt used
     * 256 MB, and the guard never fired on the test phone because the app was
     * killed at about 190 MB first. A limit at the memory ceiling is no limit.
     *
     * Android's heap is at most 256 MB on a high-memory phone and far less on a
     * cheap one, so staying under 32 MB is what makes this a guard rather than
     * an aspiration.
     */
    @Test
    fun theShippedLimitSitsBetweenRealDatabasesAndTheHeap() {
        val shipped = DatabaseLimits.MAX_ENTRY_BYTES
        val hugeRealManual = 17L * 1024 * 1024      // ~10,000 pages
        val smallestPhoneHeap = 128L * 1024 * 1024   // a cheap device

        assertTrue(
            "the limit ($shipped) must clear a 10,000-page manual ($hugeRealManual)",
            shipped > hugeRealManual,
        )
        assertTrue(
            "the limit ($shipped) must be well under a small phone's heap " +
                "($smallestPhoneHeap), or the app is killed before the guard runs",
            shipped < smallestPhoneHeap / 2,
        )
        assertEquals(64 * 1024, DatabaseLimits.READ_CHUNK_BYTES)
        assertTrue(DatabaseLimits.MAX_ENTRIES >= 10)
    }

    /** The bundled demo is a real file and must never trip the limits. */
    @Test
    fun theBundledDemoOpensWithoutComplaint() {
        val status = DatabaseLock.inspect(java.io.File("../dataeater/databases/demo.dataeater"))
        assertTrue("expected Open, got $status", status is DatabaseLock.Status.Open)
    }

    /**
     * The distinction that actually matters, made observable.
     *
     * A cap can be checked in two places:
     *
     *   during - count each chunk and stop the moment the total passes the limit
     *   after  - read the whole entry into memory, then notice it was too big
     *
     * The second version is worse than having no cap at all, because it has
     * already spent the memory it was trying to protect. It even looks
     * correct when read.
     *
     * So this test measures **how much the reader actually asked for**. It
     * hands the real function a stream that reports every byte it produces.
     * If the check happens during reading, it stops within one buffer of the
     * limit. If it happens afterwards, it is asked for everything.
     *
     * This is the test that would have caught the wrong version. It did: when
     * the check was moved after the read, this test failed and the size-cap
     * tests alone still passed.
     */
    @Test
    fun theLimitIsEnforcedWhileReadingNotAfterwards() {
        val whatTheReaderAskedFor = java.util.concurrent.atomic.AtomicLong(0)
        val available = 64L * 1024 * 1024      // pretend 64 MB, capped at 512 KB

        val endlessStream = object : java.io.InputStream() {
            override fun read(): Int {
                whatTheReaderAskedFor.incrementAndGet()
                return 'a'.code
            }

            override fun read(target: ByteArray, offset: Int, length: Int): Int {
                val left = available - whatTheReaderAskedFor.get()
                if (left <= 0) return -1
                val step = minOf(length.toLong(), left).toInt()
                java.util.Arrays.fill(target, offset, offset + step, 'a'.code.toByte())
                whatTheReaderAskedFor.addAndGet(step.toLong())
                return step
            }
        }

        assertThrows(DatabaseReader.DataEaterException::class.java) {
            DatabaseReader.readEntryWithLimit(
                endlessStream, "chunks.jsonl", testLimit,
            )
        }

        val askedFor = whatTheReaderAskedFor.get()
        assertTrue(
            "the reader asked for $askedFor bytes of an available $available. " +
                "The limit is being checked AFTER reading, which means the " +
                "memory was already spent.",
            askedFor <= testLimit + DatabaseLimits.READ_CHUNK_BYTES,
        )
    }

    // ------------------------------------------------------------------
    // The counting rule itself, kept in step with production
    // ------------------------------------------------------------------

    /**
     * Drives the **real** reader, with a small limit injected.
     *
     * This calls `DatabaseReader.readBytesFromPackage` itself. An earlier
     * version of this file re-implemented the counting loop here, which would
     * have proved only that the copy worked - if production stopped counting,
     * the copy would still pass. Nothing is duplicated now.
     */
    private fun readWithLimit(
        file: java.io.File,
        wanted: String,
        limit: Long,
        maxEntries: Int = DatabaseLimits.MAX_ENTRIES,
    ): ByteArray = DatabaseReader.readBytesFromPackage(
        { file.inputStream() },
        wanted,
        limit,
        maxEntries,
    )
}
