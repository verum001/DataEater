package com.dataeater.app.ai

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OpenRouterClientTest {
    private fun withServer(block: (HttpServer,OpenRouterClient) -> Unit) {
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        val executor=Executors.newCachedThreadPool();server.executor=executor;server.start()
        val client=OpenRouterClient("http://127.0.0.1:${server.address.port}")
        try { block(server,client) } finally { client.close();server.stop(0);executor.shutdownNow() }
    }
    @Test fun actualHttpRequestAndSseResponse() = withServer { server, client ->
        var auth="";var body=""
        server.createContext("/chat/completions") { ex ->
            auth=ex.requestHeaders.getFirst("Authorization");body=ex.requestBody.reader().readText()
            ex.responseHeaders.add("Content-Type","text/event-stream; charset=utf-8")
            val data="data: {\"choices\":[{\"delta\":{\"content\":\"Paris\"}}]}\n\ndata: [DONE]\n\n".toByteArray()
            ex.sendResponseHeaders(200,data.size.toLong());ex.responseBody.use {it.write(data)}
        }
        val out=StringBuilder()
        runBlocking { withTimeout(5000) {client.generate("synthetic-test-key",OpenRouterProtocol.request("openrouter/free","Capital?",AnswerMode.GENERAL_KNOWLEDGE,emptyList())) {out.append(it)} } }
        assertEquals("Bearer synthetic-test-key",auth);assertEquals("Paris",out.toString());assertFalse(body.contains("synthetic-test-key"));assertTrue(JSONObject(body).getBoolean("stream"))
    }
    @Test fun unauthorizedResponseDoesNotEchoSecret() = withServer {server,client ->
        server.createContext("/chat/completions") {ex ->val data="echoed-private-key".toByteArray();ex.sendResponseHeaders(401,data.size.toLong());ex.responseBody.use {it.write(data)}}
        runBlocking {
            try { withTimeout(5000) {client.generate("synthetic-test-key","{}") {}};fail() }
            catch(e:IllegalStateException) {assertTrue(e.message!!.contains("API key"));assertFalse(e.message!!.contains("echoed"))}
        }
    }
    @Test fun redirectNeverForwardsCredentials() = withServer {server,client ->
        var forwarded=false
        server.createContext("/chat/completions") {ex ->ex.responseHeaders.add("Location","/other");ex.sendResponseHeaders(302,-1);ex.close()}
        server.createContext("/other") {ex ->forwarded=true;ex.sendResponseHeaders(200,-1);ex.close()}
        runBlocking {try {client.generate("synthetic-test-key","{}") {};fail()} catch(_:IllegalStateException) {}}
        assertFalse(forwarded)
    }
    @Test fun cancellationDisconnectsAndClientCanMakeNextRequest() = withServer {server,client ->
        val started=CountDownLatch(1)
        server.createContext("/chat/completions") {ex ->
            ex.responseHeaders.add("Content-Type","text/event-stream");ex.sendResponseHeaders(200,0)
            try {ex.responseBody.write(": wait\n\n".toByteArray());ex.responseBody.flush();started.countDown();Thread.sleep(10000)} catch(_:Exception) {} finally {ex.close()}
        }
        server.createContext("/models") {ex ->val data="{\"data\":[]}".toByteArray();ex.sendResponseHeaders(200,data.size.toLong());ex.responseBody.use {it.write(data)}}
        runBlocking {
            val job=launch(Dispatchers.Default) {client.generate("synthetic-test-key","{}") {}}
            assertTrue(started.await(3,TimeUnit.SECONDS));withTimeout(2000) {job.cancelAndJoin()}
            assertEquals(emptyList<OpenRouterProtocol.Model>(),withTimeout(3000) {client.models()})
        }
    }
}
