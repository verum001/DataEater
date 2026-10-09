package com.dataeater.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dataeater.app.ai.AnswerMode
import com.dataeater.app.ai.LiteRtAiEngine
import com.dataeater.app.ai.ModelFiles
import com.dataeater.app.chat.ChatMessage
import com.dataeater.app.chat.ChatResponder
import com.dataeater.app.engine.AnswerCleaner
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in comparison on the actual installed files; leaves chats/settings untouched. */
@RunWith(AndroidJUnit4::class)
class ModelBehaviourDeviceTest {
    @Test fun compareInstructions() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runModelProbe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Storage access required", ModelFiles.hasStorageAccess(context))
        val requestedFile = InstrumentationRegistry.getArguments().getString("modelFile")
        val models = ModelFiles.findAll(context).filter { requestedFile == null || it.file.name == requestedFile }
        assertTrue("No installed models", models.isNotEmpty())
        val results = JSONArray()
        val report = File(context.cacheDir, "model-behaviour-report.json")
        fun save() { report.writeText(results.toString(2)) }
        val cases = listOf(
            "What is the capital of France?" to emptyList<ChatMessage>(),
            "What is 2 + 2?" to emptyList(),
            "The database is now off. What is the capital of France?" to listOf(
                ChatMessage("u", true, "What is the capital of France?"),
                ChatMessage("a", false, "The database does not contain an answer to this question.", databaseHadNoAnswer = true),
            ),
        )
        for (model in models) {
            val engine = LiteRtAiEngine(model.file, context.cacheDir, preferGpu = true)
            try {
                val loadStart = System.currentTimeMillis()
                engine.load()
                val loadMs = System.currentTimeMillis() - loadStart
                for ((question, history) in cases) {
                    val decision = ChatResponder(null, "").decide(question, history, false, true)
                        as ChatResponder.Decision.WithoutDatabase
                    for (mode in AnswerMode.entries) {
                        val start = System.currentTimeMillis()
                        val raw = StringBuilder()
                        engine.generate(decision.prompt, mode) { raw.append(it) }
                        results.put(JSONObject().put("model", model.displayName)
                            .put("backend", engine.backendInUse).put("loadMs", loadMs)
                            .put("mode", mode.name).put("question", question)
                            .put("historyMessages", history.size)
                            .put("answer", AnswerCleaner.clean(raw.toString()))
                            .put("elapsedMs", System.currentTimeMillis() - start))
                        save()
                    }
                }
                // A supplied fact checks that the database instructions still work.
                val raw = StringBuilder()
                engine.generate("INFORMATION: The test machine's code is KX-42.\nQUESTION: What is the test machine's code?\nANSWER:", AnswerMode.DATABASE) { raw.append(it) }
                results.put(JSONObject().put("model", model.displayName).put("mode", "DATABASE_SUPPLIED_FACT")
                    .put("answer", AnswerCleaner.clean(raw.toString())))
                save()
            } finally { engine.close() }
        }
    }
}
