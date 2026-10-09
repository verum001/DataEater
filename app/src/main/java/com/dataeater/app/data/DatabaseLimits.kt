package com.dataeater.app.data

/**
 * Hard limits on what a `.dataeater` file is allowed to make the app do.
 *
 * WHY THIS EXISTS
 * ---------------
 * A `.dataeater` file usually arrives from somewhere else: emailed by a
 * creator, downloaded, copied off a memory card. That makes it **untrusted
 * input**, and a ZIP archive is a well-known way to attack a program.
 *
 * The attack is a "zip bomb": a file of a few hundred kilobytes that
 * decompresses into hundreds of megabytes. If the reader holds the whole thing
 * in memory, Android throws `OutOfMemoryError` and kills the app. The user
 * sees no error at all — just a crash when they open a file someone sent them.
 *
 * So every entry is read through a counted stream that gives up the moment the
 * limit is passed, **while reading**. Not after reading: by then the memory is
 * already gone.
 *
 * WHY 32 MB AND NOT SOMETHING BIGGER
 * ----------------------------------
 * This number was measured, not guessed. A first attempt used 256 MB — and the
 * guard never fired, because Android's per-app heap limit on the test phone is
 * *also* about 256 MB. The app was killed by the kernel before the check could
 * run. A limit that sits at the memory ceiling is no limit at all.
 *
 * Real databases, measured:
 *
 * | Database | Compressed | Text inside |
 * |---|---|---|
 * | the bundled demo | 3 KB | 3 KB |
 * | `Kobelco`, a real manual | 317 KB | about 1.1 MB |
 * | `FH_EX165W`, a real 873-page manual | 209 KB | about 1 MB |
 * | a hypothetical 10,000-page manual | — | roughly 17 MB |
 *
 * 32 MB leaves a real manual about twice the room it needs, while keeping the
 * byte array, the decoded string and the parsed objects comfortably inside the
 * heap of even a cheap phone.
 *
 * This is a mitigation, not a complete fix. A truly enormous database would
 * still need streaming parsing rather than being read whole — that is a larger
 * change, and it is listed in [docs/ROADMAP.md].
 */
object DatabaseLimits {

    /**
     * Most bytes one file inside the package may decompress to.
     *
     * The largest single file in a database is the text itself
     * (`chunks.jsonl`, or `payload.enc` when locked).
     */
    const val MAX_ENTRY_BYTES: Long = 32L * 1024 * 1024    // 32 MB

    /** Total decompressed work allowed in one scan, including skipped entries. */
    const val MAX_SCAN_BYTES: Long = 64L * 1024 * 1024

    /**
     * Most entries the package may contain before the app stops looking.
     *
     * A real database has three or four. This stops a crafted archive with
     * millions of tiny entries from keeping the phone busy, and bounds how
     * long a malformed file can make the reader loop.
     */
    const val MAX_ENTRIES: Int = 10_000

    /** How much is read at a time, so the count happens before any allocation. */
    const val READ_CHUNK_BYTES: Int = 64 * 1024

    /** The message a user sees. Says what happened and what to do about it. */
    fun tooLargeMessage(entryName: String, limitBytes: Long): String =
        "This database is too large to open safely. The part called " +
            "'$entryName' expands to more than ${limitBytes / (1024 * 1024)} MB, " +
            "which no real database does. This can happen with a damaged file, " +
            "or with a file made specifically to use up all the memory on the " +
            "phone. It has not been opened."
}
