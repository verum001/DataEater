package com.dataeater.app
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dataeater.app.ai.*
import com.dataeater.app.data.DatabaseReader
import com.dataeater.app.engine.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.*
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DatabaseSearchDeviceTest {
    @Test fun corpusAndCancellation() = runBlocking {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("runCorpusCheck")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".research"))
        val database=DatabaseReader.openFile(File("/sdcard/DataEaterResearch",args.getString("databaseFile")!!))
        val start=System.nanoTime()
        val rag=RagEngine(database.chunks,database.sources)
        val report=JSONObject().put("chunks",database.chunks.size).put("indexMs",(System.nanoTime()-start)/1e6)
        val queries=listOf("Ohm law electrical resistance", "piston engine compression ratio", "magneto ignition timing", "turbine compressor stall", "weight balance center gravity")
        val rows=JSONArray();val timings=mutableListOf<Double>()
        for(query in queries) {
            val found=rag.retrieve(query)
            assertTrue(query,found.isNotEmpty())
            for(i in 1..20) { val t=System.nanoTime();rag.retrieve(query);timings+=(System.nanoTime()-t)/1e6 }
            rows.put(JSONObject().put("query",query).put("citations",JSONArray(found.map { it.citation })).put("passages",JSONArray(found.map { it.chunk.text })))
        }
        report.put("medianSearchMs",timings.sorted()[timings.size/2]).put("queries",rows)
        val engine=LiteRtAiEngine(File(args.getString("modelDirectory") ?: "/sdcard/DataEater",args.getString("modelFile") ?: "gemma-4-E2B-it-gpu.litertlm"),context.cacheDir,true)
        try {
            engine.load()
            val answers=JSONArray()
            for(question in listOf("What does Ohm's law relate?", "What are the four strokes of a four-stroke engine?")) {
                val passages=rag.fitContext(rag.retrieve(question),5000)
                val text=StringBuilder()
                withTimeout(60000) {engine.generate(rag.buildPrompt(question,passages),AnswerMode.DATABASE) { text.append(it) }}
                answers.put(JSONObject().put("question",question).put("answer",AnswerCleaner.clean(text.toString())).put("citations",JSONArray(passages.map {it.citation})))
            }
            report.put("manualAnswers",answers)
            try {withTimeout(400) {engine.generate("Write a very long story of 2000 words.",AnswerMode.GENERAL_KNOWLEDGE) {}}} catch(expected:kotlinx.coroutines.TimeoutCancellationException) {}
            val text=StringBuilder()
            withTimeout(15000) {engine.generate("What is the capital of France?",AnswerMode.GENERAL_KNOWLEDGE) {text.append(it)}}
            assertTrue(text.toString().contains("Paris",ignoreCase=true))
            report.put("answerAfterCancellation",text.toString())
        } finally {engine.close()}
        File(context.cacheDir,"corpus-check.json").writeText(report.toString(2))
    }
}
