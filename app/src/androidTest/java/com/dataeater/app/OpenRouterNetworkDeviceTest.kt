package com.dataeater.app

import androidx.test.platform.app.InstrumentationRegistry
import com.dataeater.app.ai.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class OpenRouterNetworkDeviceTest {
    @Test fun liveCatalogAndInvalidCredentialRecovery() = runBlocking {
        if (InstrumentationRegistry.getArguments().getString("runOpenRouterNetwork") != "true") return@runBlocking
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".research"))
        val client=OpenRouterClient()
        try {
            val models=withTimeout(30000) {client.models()}
            assertTrue(models.isNotEmpty())
            var rejected=false
            try {withTimeout(30000) {client.generate("synthetic-invalid-test-key",OpenRouterProtocol.request("openrouter/free","Connection check",AnswerMode.GENERAL_KNOWLEDGE,emptyList())) {}}}
            catch(e:IllegalStateException) {rejected=e.message?.contains("API key")==true}
            assertTrue("Invalid credential should produce a recoverable key error",rejected)
            File(context.cacheDir,"openrouter-network-check.json").writeText(JSONObject().put("modelCount",models.size).put("freeModels",models.count {it.free}).put("invalidKeyRecovery",rejected).put("realAccountGenerationTested",false).toString(2))
        } finally {client.close()}
    }
}
