package com.dataeater.app.ai

import com.dataeater.app.chat.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader

class OpenRouterProtocolTest {
    private fun consume(raw: String): String { val out = StringBuilder(); OpenRouterProtocol.stream(StringReader(raw)) { out.append(it) }; return out.toString() }
    private fun delta(text: String) = "data: " + JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("delta", JSONObject().put("content", text)))).toString()+"\n\n"
    @Test fun requestUsesRolesWithoutPuttingKeyInBody() {
        val messages = listOf(ChatMessage("u", true, "My code is AZ-17."), ChatMessage("a", false, "AZ-17"))
        val r = JSONObject(OpenRouterProtocol.request("openrouter/free", "Question\n\"quoted\"", AnswerMode.DATABASE, messages))
        assertTrue(r.getBoolean("stream")); assertEquals(ReplyLength.NORMAL.maxTokens, r.getInt("max_tokens"))
        assertEquals("deny", r.getJSONObject("provider").getString("data_collection"))
        val m = r.getJSONArray("messages"); assertEquals(4,m.length())
        assertEquals("system",m.getJSONObject(0).getString("role")); assertEquals("assistant",m.getJSONObject(2).getString("role"))
        assertEquals("Question\n\"quoted\"",m.getJSONObject(3).getString("content")); assertFalse(r.has("api_key"))
    }
    @Test fun repeatedTokensUnicodeCommentsAndUsageArePreserved() {
        val text = ": heartbeat\n\n"+delta("page 1")+delta("1 · Париж")+"data: {\"choices\":[],\"usage\":{\"total_tokens\":7}}\n\ndata: [DONE]\n\n"
        assertEquals("page 11 · Париж",consume(text))
    }
    @Test fun multilineDataAndCrLfWork() {
        assertEquals("Paris",consume("data: {\"choices\":[\r\ndata: {\"delta\":{\"content\":\"Paris\"}}]}\r\n\r\ndata: [DONE]\r\n\r\n"))
    }
    @Test fun eofWithoutDoneFailsEvenAfterPartialText() {
        try { consume(delta("Partial")); fail() } catch(e:IllegalStateException) { assertTrue(e.message!!.contains("ended")) }
    }
    @Test fun emptyDoneIsNotSuccessfulAnswer() {
        try { consume("data: [DONE]\n\n"); fail() } catch(e:IllegalStateException) { assertTrue(e.message!!.contains("no answer")) }
    }
    @Test fun errorDuringStreamIsSanitized() {
        try { consume(delta("Partial")+"data: {\"error\":{\"code\":402,\"message\":\"secret and echoed prompt\"}}\n\n");fail() }
        catch(e:IllegalStateException) { assertTrue(e.message!!.contains("credits"));assertFalse(e.message!!.contains("secret")) }
    }
    @Test fun malformedAndOversizedEventsFail() {
        for (data in listOf("data: invalid\n\n", "data: "+"x".repeat(262145)+"\n\n")) {
            try { consume(data); fail() } catch(_:IllegalStateException) {}
        }
    }
    @Test fun lengthLimitedAnswerIsNotMarkedComplete() {
        val data=delta("Partial")+"data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}\n\n"
        try { consume(data);fail() } catch(e:IllegalStateException) {assertTrue(e.message!!.contains("length limit"))}
    }
    @Test fun reasoningChannelsAreNeverDisplayed() {
        val data="data: {\"choices\":[{\"delta\":{\"reasoning\":\"private thoughts\"}}]}\n\n"+delta("Answer")+"data: [DONE]\n\n"
        assertEquals("Answer",consume(data))
    }
    @Test fun catalogFiltersNonTextSmallContextAndUnknownPrices() {
        val input="""{"data":[{"id":"x/free","name":"Free","context_length":8192,"pricing":{"prompt":"0","completion":"0"}},{"id":"x/paid","name":"Paid"},{"id":"x/image","architecture":{"output_modalities":["image"]}},{"id":"x/small","context_length":2048}]}"""
        val models=OpenRouterProtocol.models(input);assertEquals(listOf("x/free","x/paid"),models.map {it.id});assertTrue(models[0].free);assertFalse(models[1].free)
    }
    @Test fun onlineMemoryDoesNotIncludeLocalOrLegacyMessages() {
        val local=listOf(ChatMessage("u",true,"Private local text"),ChatMessage("a",false,"Local answer",answeredWithoutDatabase=true))
        assertTrue(ConversationMemory.select(local,false,"online:general",5000).isEmpty())
        val online=local.map {it.copy(contextKey="online:general")}
        assertEquals(online,ConversationMemory.select(online,false,"online:general",5000))
        assertTrue(ConversationMemory.select(online,false,"general",5000).isEmpty())
    }
}
