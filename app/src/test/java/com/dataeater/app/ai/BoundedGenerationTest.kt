package com.dataeater.app.ai

import com.dataeater.app.chat.*
import com.dataeater.app.engine.RagEngine
import com.dataeater.app.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BoundedGenerationTest {
    private class Fake(val action: (Int, String, (String) -> Unit) -> Unit): LocalAiEngine {
        var calls = 0
        override val modelName = "synthetic"
        override suspend fun load() {}
        override fun close() {}
        override suspend fun generate(prompt: String, mode: AnswerMode, history: List<ChatMessage>, behavior: ReplyBehavior, onNewText: (String) -> Unit) { action(++calls, prompt, onNewText) }
    }
    private fun request(text: String, sources: List<String> = emptyList()) = BoundedGeneration.Request(text, AnswerMode.DATABASE, emptyList(), sources)
    @Test fun runtimeCountsShrinkReplannedInputAndReplaceCitationsBeforeStreaming() = runBlocking {
        val engine = Fake { call, _, emit -> if (call == 1) error("Status Code: 3. Input token ids are too long. 2118 >= 2048") else emit("380 bar. Stop first.") }
        val sources = mutableListOf<List<String>>(); var budget = 0; val text = StringBuilder()
        BoundedGeneration.generate(engine,request("long",listOf("page1","page2")),6000,ReplyBehavior(),replan={ budget=it;request("short",listOf("page1")) },onSources={sources.add(it)}) {text.append(it)}
        assertEquals(2,engine.calls);assertTrue(budget in 1..5000);assertEquals(listOf("page1"),sources.last());assertTrue(text.contains("Stop first"))
    }
    @Test fun noRetryAfterStreamingOrForUnrelatedFailuresOrOnlineRequests() = runBlocking {
        for (mode in 0..2) {
            val engine=Fake { _,_,emit -> if(mode==0) emit("Partial");error(if(mode==1) "Network failed" else "Input token ids are too long 2118 >= 2048") }
            try { BoundedGeneration.generate(engine,request("x"),6000,ReplyBehavior(),replan=if(mode==2) null else { _ ->request("y") }) {};fail() } catch (_:IllegalStateException) {}
            assertEquals(1,engine.calls)
        }
    }
    @Test fun retriesStopAndDoNotCutAnOversizedHighestRankedPassage() = runBlocking {
        val engine=Fake { _,_,_ ->error("Input token ids are too long 9999 >= 2048") }
        val source=SourceInfo("s","Invented", "",2026)
        val chunk=Chunk("c","s","Pressure",1,"Pressure is 380 bar. Never open hot.".repeat(120))
        val rag=RagEngine(listOf(chunk),listOf(source));var cleared=false
        try { BoundedGeneration.generate(engine,request("original",listOf("page1")),6000,ReplyBehavior(),replan={ b ->BoundedGeneration.request(ChatResponder(rag,"Invented",inputBudget=b).decide("pressure",emptyList(),true,true)) },onSources={cleared=it.isEmpty()}) {};fail() }
        catch(error:IllegalArgumentException) {assertTrue(error.message!!.contains("long"))}
        assertEquals(1,engine.calls);assertTrue(cleared)
    }
    @Test fun completePassagesAndMemoryUseUnicodeCost() {
        val body="Давление 380 бар. Не открывайте горячую крышку. ".repeat(20)
        val rag=RagEngine(listOf(Chunk("c","s","Pressure",1,body)),listOf(SourceInfo("s","Test","",2026)))
        assertTrue(rag.fitContext(rag.retrieve("380"),body.length+100).isNotEmpty())
        assertTrue(rag.fitContext(rag.retrieve("380"),body.length+100){it.toByteArray().size}.isEmpty())
        val memory=listOf(ChatMessage("u",true,body),ChatMessage("a",false,"Да",answeredWithoutDatabase=true))
        assertTrue(ConversationMemory.select(memory,false,null,body.length+100).isNotEmpty())
        assertTrue(ConversationMemory.select(memory,false,null,body.length+100){it.toByteArray().size}.isEmpty())
    }
    @Test fun unchangedPlansAndPersistentOverflowHaveBoundedAttempts() = runBlocking {
        val engine=Fake { _,_,_ ->error("Input token ids are too long 2118 >= 2048") }
        try {BoundedGeneration.generate(engine,request("same"),6000,ReplyBehavior(),replan={request("same")}) {};fail()}catch(_:IllegalArgumentException){}
        assertEquals(1,engine.calls)
        val second=Fake { _,_,_ ->error("Input token ids are too long 2118 >= 2048") }
        try {BoundedGeneration.generate(second,request("start"),6000,ReplyBehavior(),replan={request("budget$it")}) {};fail()}catch(_:IllegalArgumentException){}
        assertEquals(4,second.calls)
        assertNull(BoundedGeneration.overflow(IllegalStateException("Status code 3 invalid model")))
    }
}
