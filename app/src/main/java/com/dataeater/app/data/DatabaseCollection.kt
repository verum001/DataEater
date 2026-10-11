package com.dataeater.app.data

/** An in-memory search collection. Original files and their identifiers stay unchanged. */
object DatabaseCollection {
    private const val MAX_TEXT_BYTES = 64L * 1024 * 1024
    private const val MAX_PASSAGES = 100_000

    fun checkSize(bytes: Long, passages: Long) {
        if (bytes > MAX_TEXT_BYTES || passages > MAX_PASSAGES) {
            throw DatabaseReader.DataEaterException(
                "These databases are too large to search together. Choose one database instead.",
            )
        }
    }

    fun combine(
        databases: List<DatabaseReader.OpenedDatabase>,
        excluded: List<String> = emptyList(),
    ): DatabaseReader.OpenedDatabase {
        require(databases.isNotEmpty())
        var bytes = 0L
        var count = 0L
        for (database in databases) {
            count += database.chunks.size
            for (chunk in database.chunks) {
                bytes += chunk.text.toByteArray(Charsets.UTF_8).size
                bytes += chunk.searchContext.toByteArray(Charsets.UTF_8).size
            }
            checkSize(bytes, count)
        }
        val sources = databases.flatMapIndexed { index, database ->
            database.sources.map { source ->
                source.copy(id = "$index:${source.id}",
                    title = "${source.title} (${database.fileName.ifBlank { database.manifest.name }})")
            }
        }
        val chunks = databases.flatMapIndexed { index, database ->
            database.chunks.map { it.copy(id = "$index:${it.id}", sourceId = "$index:${it.sourceId}") }
        }
        val description = "Searching: " + databases.joinToString { it.fileName.ifBlank { it.manifest.name } } +
            if (excluded.isEmpty()) "" else ". Locked files excluded: ${excluded.joinToString()}"
        return DatabaseReader.OpenedDatabase(
            manifest = databases.first().manifest.copy(
                databaseId = "collection", name = DatabaseChoice.ALL,
                description = description, language = "multiple", license = "See each source database",
                encryption = "none", creatorPublicKey = "", contact = "", creator = "",
                documentCount = sources.size, chunkCount = chunks.size,
            ), chunks = chunks, sources = sources,
        )
    }
}
