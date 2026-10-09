package com.dataeater.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dataeater.app.ai.ModelCatalog
import com.dataeater.app.ai.ModelFiles
import com.dataeater.app.ai.ModelInstaller
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Opt-in Android storage integration check; creates/removes only its own test files. */
@RunWith(AndroidJUnit4::class)
class ModelInstallPathsDeviceTest {
    @Test fun downloadStagingCanPublishToSharedModelFolder() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runInstallProbe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(ModelFiles.hasStorageAccess(context))
        val bytes = "installer storage probe".toByteArray()
        val entry = ModelCatalog.entries.first().copy(id = "install-probe", fileName = "zz-install-path-probe.litertlm",
            bytes = bytes.size.toLong(), sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        val source = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "install-probe.part")
        val target = File(ModelFiles.dataEaterDirectory(), entry.fileName)
        assertTrue("Probe target already exists", !target.exists())
        try {
            source.writeBytes(bytes)
            ModelInstaller.install(source, ModelFiles.dataEaterDirectory(), entry)
            assertTrue(target.readBytes().contentEquals(bytes))
        } finally { source.delete(); target.delete() }
    }
}
