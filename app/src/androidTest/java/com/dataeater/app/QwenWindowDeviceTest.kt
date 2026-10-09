package com.dataeater.app

import androidx.test.platform.app.InstrumentationRegistry
import com.dataeater.app.ai.*
import com.dataeater.app.chat.ChatResponder
import com.dataeater.app.engine.RagEngine
import com.dataeater.app.model.Chunk
import com.dataeater.app.model.SourceInfo
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in real Qwen check, using only an invented technical fact. */
class QwenWindowDeviceTest {
    @Test fun overflowRecoversAndNormalGroundedQuestionAnswers() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runQwenWindow") == "true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".research"))
        val file=File("/sdcard/DataEater/qwen3_4b_instruct_2507_mixed_int4.litertlm")
        assumeTrue(file.isFile)
        val engine=LiteRtAiEngine(file,context.cacheDir,true)
        val report=JSONObject().put("model","Qwen3-4B-Instruct-2507").put("device","vivo V2453A / Android 16").put("syntheticOnly",true)
        try {
            withTimeout(90_000) { engine.load() }
            val short="REFERENCE TEXT:\n[1] Invented PX-17 guide, page 1\nThe invented PX-17 pump uses 380 bar. Stop the pump before inspection.\n\nQUESTION: What pressure does PX-17 use?"
            val oversized=short+"\nCalibration values: "+"123456789 987654321 456789123. ".repeat(65)
            var originalFailed=false
            try { withTimeout(90_000) { engine.generate(oversized,AnswerMode.DATABASE) {} } }
            catch(error:Exception) { originalFailed=BoundedGeneration.overflow(error)!=null;report.put("originalError",error.message.orEmpty().take(220)) }
            assertTrue("Synthetic input did not reproduce the runtime overflow",originalFailed)
            var replanned=0;val answer=StringBuilder();val sources=mutableListOf<List<String>>()
            withTimeout(120_000) {
                BoundedGeneration.generate(engine,BoundedGeneration.Request(oversized,AnswerMode.DATABASE,emptyList(),listOf("Invented PX-17 guide — page 1")),6000,ReplyBehavior(),
                    replan={budget -> replanned++;report.put("retryBudget",budget);BoundedGeneration.Request(short,AnswerMode.DATABASE,emptyList(),listOf("Invented PX-17 guide — page 1"))},
                    onSources={sources.add(it)}) {answer.append(it)}
            }
            assertTrue(replanned>=1);assertEquals(listOf("Invented PX-17 guide — page 1"),sources.last());assertTrue(answer.isNotBlank())
            report.put("recovered",true).put("recoveryAnswer",answer.toString().take(1000))
            val rag=RagEngine(listOf(Chunk("c1","s1","Pressure",1,"The invented PX-17 pump uses 380 bar. Stop the pump before inspection.")),listOf(SourceInfo("s1","Invented PX-17 guide","Synthetic",2026)))
            val normal=ChatResponder(rag,"Invented",inputBudget=4000,textCost={it.toByteArray().size},instructionReserve=ReplyBehavior().instruction(AnswerMode.DATABASE).toByteArray().size+128).decide("What pressure does PX-17 use?",emptyList(),true,true) as ChatResponder.Decision.FromDatabase
            val grounded=StringBuilder()
            withTimeout(120_000) { BoundedGeneration.generate(engine,BoundedGeneration.request(normal),4000,ReplyBehavior(),replan={budget -> BoundedGeneration.request(ChatResponder(rag,"Invented",inputBudget=budget,textCost={it.toByteArray().size},instructionReserve=ReplyBehavior().instruction(AnswerMode.DATABASE).toByteArray().size+128).decide("What pressure does PX-17 use?",emptyList(),true,true))}) { grounded.append(it) } }
            assertTrue("Normal reply lost the supplied value: $grounded",Regex("380\\s*bar",RegexOption.IGNORE_CASE).containsMatchIn(grounded))
            report.put("normalGrounded",true).put("normalAnswer",grounded.toString().take(1000))
        } finally {
            File(context.cacheDir,"qwen-window-invented.json").writeText(report.toString(2))
            engine.close()
        }
        Unit
    }
}
