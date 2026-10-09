package com.dataeater.app

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.dataeater.app.ai.*
import com.dataeater.app.engine.RagEngine
import com.dataeater.app.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Explicitly opted-in synthetic checks. No sessions are read or changed, no keys are exported. */
class OpenRouterBehaviorDeviceTest {
    @Test fun compareFreeModels() = runBlocking {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        if (args.getString("runBehaviorBenchmark") != "true") return@runBlocking
        val context = instrument.targetContext
        check(context.packageName == "com.dataeater.app" && args.getString("allowProductionKey") == "true")
        context.startActivity(android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        val store = OpenRouterSettingsStore(context)
        check(store.read().hasKey) { "A user-entered API key is required." }
        val catalogClient = OpenRouterClient()
        val models = try { withTimeout(45000) { catalogClient.models() }.filter { it.free && it.id.endsWith(":free") } } finally { catalogClient.close() }
        // Only zero-priced models from the fresh catalog. Never use paid fallback.
        val selectedIds = args.getString("models")?.split(",")
        val chosen = if (selectedIds != null) models.filter { it.id in selectedIds }.toMutableList() else listOf("apodex", "gemma", "qwen").mapNotNull { family -> models.firstOrNull { it.id.contains(family, true) } }.distinctBy { it.id }.toMutableList()
        if (selectedIds == null) models.filter { it !in chosen }.take((3 - chosen.size).coerceAtLeast(0)).forEach { chosen += it }
        check(chosen.size >= if (selectedIds == null) 3 else 1) { "Three free models are required for comparison." }
        val rag = RagEngine(listOf(
            Chunk("c1", "s", "PX-17 pressure and servicing", 1, "The invented PX-17 pump operates at 380 bar. Replace its filter every 12 months. The PX-17 bolt torque is not specified. Never clean the filter with compressed air."),
            Chunk("c2", "s", "PX-17 cooling", 2, "The invented PX-17 pump can overheat when coolant is insufficient or the radiator is blocked. Stop the pump before inspection. Never open the coolant cap while hot.")
        ), listOf(SourceInfo("s", "Invented benchmark manual", "DataEater", 2026)))
        data class Case(val id: String, val strictness: DatabaseStrictness, val length: ReplyLength, val question: String)
        val cases = listOf(
            Case("strict-known-short", DatabaseStrictness.STRICT, ReplyLength.SHORT, "What is the PX-17 operating pressure and filter interval?"),
            Case("strict-missing-short", DatabaseStrictness.STRICT, ReplyLength.SHORT, "What is the PX-17 bolt torque?"),
            Case("balanced-explanation-normal", DatabaseStrictness.BALANCED, ReplyLength.NORMAL, "Explain the PX-17 overheating causes and safe inspection precautions."),
            Case("flexible-background-normal", DatabaseStrictness.FLEXIBLE, ReplyLength.NORMAL, "Explain the PX-17 overheating causes, adding general background about how a radiator removes heat. Also give the PX-17 bolt torque."),
            Case("strict-length-short", DatabaseStrictness.STRICT, ReplyLength.SHORT, "Explain the PX-17 overheating causes and safe inspection precautions."),
            Case("strict-length-normal", DatabaseStrictness.STRICT, ReplyLength.NORMAL, "Explain the PX-17 overheating causes and safe inspection precautions."),
            Case("strict-explanation-detailed", DatabaseStrictness.STRICT, ReplyLength.DETAILED, "Explain the PX-17 overheating causes and safe inspection precautions.")
        )
        for (model in chosen) {
            val engine = OpenRouterAiEngine(store.key(), model.id, model.name)
            try {
                engine.load()
                for (case in cases.filter { args.getString("cases")?.split(",")?.contains(it.id) ?: (it.id !in listOf("strict-length-short", "strict-length-normal")) }) {
                    val start = System.currentTimeMillis(); val answer = StringBuilder()
                    val behavior = ReplyBehavior(case.strictness, case.length)
                    val prompt = rag.buildPrompt(case.question, rag.retrieve(case.question))
                    val row = JSONObject().put("model", model.id).put("modelName", model.name).put("freeAtTestTime", model.free).put("case", case.id).put("strictness", case.strictness.name).put("length", case.length.name).put("question", case.question).put("prompt", prompt).put("instruction", behavior.instruction(AnswerMode.DATABASE)).put("maxTokens", case.length.maxTokens)
                    try { withTimeout(35000) { engine.generate(prompt, AnswerMode.DATABASE, emptyList(), behavior) { answer.append(it) } }; row.put("complete", true) }
                    catch (e: Exception) { row.put("complete", false).put("error", e.message ?: e.javaClass.simpleName) }
                    row.put("answer", answer.toString()).put("elapsedMs", System.currentTimeMillis() - start)
                    instrument.sendStatus(0, Bundle().apply { putString("DATAEATER_BENCHMARK", row.toString()) })
                }
            } finally { engine.close() }
        }
    }
}
