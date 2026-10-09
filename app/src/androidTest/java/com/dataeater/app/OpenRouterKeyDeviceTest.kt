package com.dataeater.app

import androidx.test.platform.app.InstrumentationRegistry
import com.dataeater.app.ai.*
import org.junit.Assert.*
import org.junit.Test

class OpenRouterKeyDeviceTest {
    @Test fun savedKeyIsEncryptedAndRemovingItClearsConfiguration() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".research"))
        val store=OpenRouterSettingsStore(context)
        try {
            store.remove()
            assertFalse(store.read().hasKey)
            store.save("synthetic-device-test-key",OpenRouterProtocol.Model("openrouter/free","Free models",true),false)
            assertEquals("synthetic-device-test-key",store.key())
            val raw=context.getSharedPreferences("openrouter-private",0).all.toString()
            assertFalse(raw.contains("synthetic-device-test-key"));assertTrue(store.read().hasKey);assertFalse(store.read().allowDatabase)
            store.save("",OpenRouterProtocol.Model("x/model","Example",false),true)
            assertEquals("synthetic-device-test-key",store.key());assertTrue(store.read().allowDatabase)
            store.remove();assertFalse(store.read().hasKey);assertEquals("openrouter/free",store.read().modelId)
        } finally {store.remove()}
    }
}
