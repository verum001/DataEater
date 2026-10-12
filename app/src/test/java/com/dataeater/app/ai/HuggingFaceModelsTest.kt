package com.dataeater.app.ai

import org.junit.Assert.*
import org.junit.Test

class HuggingFaceModelsTest {
    private fun repo(path: String = "folder/model.litertlm", hash: String = "b".repeat(64), gated: String = "false") =
        """{"id":"publisher/model","sha":"${"a".repeat(40)}","gated":$gated,"lastModified":"2026-10-12T01:00:00Z","cardData":{"license":"apache-2.0"},"siblings":[{"rfilename":"$path","size":100,"lfs":{"size":100,"sha256":"$hash"}},{"rfilename":"model.gguf"}]}"""
    @Test fun findsOnlyVerifiedModelFilesAtPinnedRevision() {
        val entry = HuggingFaceModels.parseRepository(repo()).single()
        assertEquals(100L, entry.bytes)
        assertTrue(entry.url.contains("/resolve/${"a".repeat(40)}/folder/model.litertlm"))
        assertFalse(entry.documentTested)
        assertFalse(entry.fileName.contains('/'))
        assertEquals("2026-10-12T01:00:00Z", entry.updatedAt)
    }
    @Test fun newVersionCannotOverwriteExistingFilename() {
        val old = HuggingFaceModels.parseRepository(repo()).single()
        val updated = HuggingFaceModels.parseRepository(repo(hash = "c".repeat(64))).single()
        assertNotEquals(old.fileName, updated.fileName)
        assertNotEquals(old.id, updated.id)
    }
    @Test fun rejectsUnverifiedGatedAndTraversalFiles() {
        assertTrue(HuggingFaceModels.parseRepository(repo(hash = "missing")).isEmpty())
        assertTrue(HuggingFaceModels.parseRepository(repo(gated = "\"auto\"")).isEmpty())
        assertTrue(HuggingFaceModels.parseRepository(repo(path = "../model.litertlm")).isEmpty())
        assertTrue(HuggingFaceModels.parseRepository(repo(path = "/model.litertlm")).isEmpty())
    }
    @Test fun savedDownloadRetainsItsExactIdentityAndRuntimeProfile() {
        val entry = HuggingFaceModels.parseRepository(repo()).single().copy(cpuOnly = true, maxOutputTokens = 256, inputChars = 3000)
        assertEquals(entry, HuggingFaceModels.decode(HuggingFaceModels.encode(entry)))
        ModelCatalog.registerDownloaded(entry)
        assertEquals(entry, ModelCatalog.forFile(entry.fileName))
    }
    @Test fun doesNotAcceptCorruptedSavedMetadata() {
        assertNull(HuggingFaceModels.decode("{\"repository\":\"other\"}"))
        val entry = HuggingFaceModels.parseRepository(repo()).single()
        assertFalse(HuggingFaceModels.valid(entry.copy(repository = "../model")))
        assertFalse(HuggingFaceModels.valid(entry.copy(bytes = Long.MAX_VALUE)))
    }
    @Test fun recommendationsDoNotPromoteUntestedUpdatesOrOtherVariants() {
        val reviewed = ModelCatalog.recommended
        assertEquals(listOf("gemma4e2", "qwen15", "qwen4"), reviewed.map { it.id })
        val entry = reviewed.first()
        assertTrue(ModelCatalog.isRecommended(entry.copy(revision = "f".repeat(40))))
        assertFalse(ModelCatalog.isRecommended(entry.copy(sha256 = "f".repeat(64))))
        assertFalse(ModelCatalog.isRecommended(entry.copy(bytes = entry.bytes + 1)))
        assertFalse(ModelCatalog.isRecommended(entry.copy(remoteFileName = "other.litertlm")))
        assertFalse(ModelCatalog.isRecommended(ModelCatalog.entries.first { it.id == "qwen17" }))
    }
    @Test fun publisherDescriptionExcludesMetadataCodeImagesAndHiddenComments() {
        val card = """---
license: apache-2.0
---
# Model
<!-- hidden metadata -->
This is a **small model** for [document chat](https://example.com).

```python
print("do not display")
```
![chart](https://example.com/chart.png)

Another paragraph.
"""
        val description = HuggingFaceModels.descriptionPreview(card)
        assertTrue(description.startsWith("This is a small model for document chat."))
        assertTrue(description.contains("Another paragraph."))
        assertFalse(description.contains("license:"))
        assertFalse(description.contains("print("))
        assertFalse(description.contains("https://"))
        assertFalse(description.contains("hidden metadata"))
    }
    @Test fun publisherDescriptionIsBoundedAndHandlesMissingText() {
        assertEquals("", HuggingFaceModels.descriptionPreview(""))
        assertEquals("", HuggingFaceModels.descriptionPreview("---\nprivate: true\n---\n```\ncode\n```"))
        assertEquals(1800, HuggingFaceModels.descriptionPreview("word ".repeat(1000)).length)
    }

}
