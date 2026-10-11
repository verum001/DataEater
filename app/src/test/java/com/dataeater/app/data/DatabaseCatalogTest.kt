package com.dataeater.app.data

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class DatabaseCatalogTest {
    private fun release(version: String, date: String, digest: String = "a".repeat(64), host: String = "github.com", draft: Boolean = false) =
        """{"tag_name":"survival-v$version","published_at":"$date","draft":$draft,"assets":[{"id":1,"state":"uploaded","name":"survival.dataeater","size":123,"digest":"sha256:$digest","browser_download_url":"https://$host/verum001/DataEater-Databases/releases/download/survival-v$version/survival.dataeater"}]}"""

    @Test fun newestPublishedVersionIsSelectedWithoutDependingOnApiOrder() {
        val entries = DatabaseCatalog.parse(JSONArray("[${release("0.1", "2026-10-01")},${release("0.2", "2026-10-11")},${release("0.3", "2026-10-12", draft = true)}]"))
        assertEquals("survival-v0.2", entries.single().version)
        assertEquals("survival-0.2.dataeater", entries.single().fileName)
        assertEquals(entries.single(), DatabaseCatalog.restore(entries.single().json().toString()))
    }
    @Test(expected = IllegalArgumentException::class) fun foreignDownloadHostIsRejected() {
        DatabaseCatalog.parse(JSONArray("[${release("0.1", "2026-10-11", host = "example.com")}]"))
    }
    @Test(expected = IllegalArgumentException::class) fun missingChecksumCannotDowngradeToOlderVersion() {
        DatabaseCatalog.parse(JSONArray("[${release("0.1", "2026-10-01")},${release("0.2", "2026-10-11", digest = "")}]"))
    }
}
