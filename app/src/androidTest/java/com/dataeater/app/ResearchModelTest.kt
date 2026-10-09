package com.dataeater.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dataeater.app.ai.*
import com.dataeater.app.chat.*
import com.dataeater.app.engine.*
import com.dataeater.app.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in synthetic benchmark. Never reads or modifies the owner's sessions. */
@RunWith(AndroidJUnit4::class)
class ResearchModelTest {
    @Test fun benchmark() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("runResearch") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".research"))
        val fileName = args.getString("modelFile") ?: error("modelFile required")
        val phase = args.getString("phase") ?: "baseline"
        val model = File(args.getString("modelDirectory") ?: "/sdcard/DataEater", fileName)
        assertTrue(model.isFile)
        val sources = listOf(SourceInfo("s1", "Synthetic test manual", "DataEater", 2026))
        val chunks = listOf(
            Chunk("c1", "s1", "Idle pressure", 11, "The test engine requires fuel rail pressure of 380 bar at 850 rpm. The fuel filter must be replaced every 12 months."),
            Chunk("c2", "s1", "Low oil pressure", 12, "Possible causes of low oil pressure are insufficient oil, a blocked pickup screen and a worn oil pump. Stop the engine before inspecting the lubrication system."),
            Chunk("c3", "s1", "Engine overheating", 13, "Engine overheating can be caused by insufficient coolant or a blocked radiator. Never open the coolant cap while the engine is hot."),
            Chunk("c4", "s1", "Filter servicing", 14, "Do not clean the fuel filter with compressed air. Replace the filter. Oil filter replacement is every 6 months."),
        )
        val responder = ChatResponder(RagEngine(chunks, sources), "Synthetic manual", inputBudget=ModelCatalog.forFile(fileName)?.inputChars ?: 6000)
        data class Case(val id: String, val question: String, val database: Boolean, val history: List<ChatMessage> = emptyList())
        val cases = listOf(
            Case("capital", "What is the capital of France?", false),
            Case("memory", "What machine code did I tell you?", false, listOf(ChatMessage("u1",true,"My machine code is AZ-17."),ChatMessage("a1",false,"Your machine code is AZ-17.",answeredWithoutDatabase=true))),
            Case("memory-late", "What machine code did I tell you?", false,
                listOf(ChatMessage("code-u",true,"My machine code is AZ-17.",contextKey="general"),ChatMessage("code-a",false,"Your machine code is AZ-17.",answeredWithoutDatabase=true,contextKey="general")) +
                    (1..12).flatMap { listOf(ChatMessage("u$it",true,"This is conversation turn $it.",contextKey="general"),ChatMessage("a$it",false,"Understood.",answeredWithoutDatabase=true,contextKey="general")) }),
            Case("pressure", "What fuel rail pressure is required at 850 rpm?", true),
            Case("followup", "How often should I replace it?", true, listOf(ChatMessage("u2",true,"Tell me about the fuel filter."),ChatMessage("a2",false,"The fuel filter needs replacement.",sources=listOf("Synthetic test manual — page 11")))),
            Case("oil-causes", "What are possible causes of low oil pressure?", true),
            Case("negation", "May I clean the fuel filter with compressed air?", true),
            Case("missing", "What is the coolant cap torque?", true),
        )
        val report = File(context.cacheDir,"research-$phase.json")
        val results = JSONArray()
        fun save() { report.writeText(results.toString(2)) }
        val engine = LiteRtAiEngine(model,context.cacheDir,preferGpu=args.getString("cpu")!="true")
        val loadStart=System.currentTimeMillis()
        try {
            engine.load()
            val loadMs=System.currentTimeMillis()-loadStart
            for (case in cases.filter { args.getString("case") == null || it.id == args.getString("case") }) {
                val decision=responder.decide(case.question,case.history,case.database,true)
                val row=JSONObject().put("model",fileName).put("case",case.id).put("phase",phase).put("backend",engine.backendInUse).put("loadMs",loadMs)
                val prompt=when(decision) {
                    is ChatResponder.Decision.FromDatabase -> decision.prompt
                    is ChatResponder.Decision.WithoutDatabase -> decision.prompt
                    else -> null
                }
                if(prompt==null) {row.put("answer","No passages");results.put(row);save();continue}
                val text=StringBuilder();val start=System.currentTimeMillis()
                try {
                    withTimeout(120_000) { engine.generate(prompt,if(case.database) AnswerMode.DATABASE else AnswerMode.GENERAL_KNOWLEDGE, history = when(decision) { is ChatResponder.Decision.FromDatabase -> decision.history; is ChatResponder.Decision.WithoutDatabase -> decision.history; else -> emptyList() }) {text.append(it)} }
                    row.put("answer",AnswerCleaner.clean(text.toString()))
                } catch(problem:Exception) {row.put("error",problem.javaClass.simpleName+": "+problem.message)}
                row.put("elapsedMs",System.currentTimeMillis()-start).put("promptChars",prompt.length)
                results.put(row);save()
            }
        } catch(problem:Exception) {results.put(JSONObject().put("model",fileName).put("phase",phase).put("loadError",problem.message));save()}
        finally {engine.close()}
    }
}
