package com.illakrivetsbit.nexflip

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object RemoteConnection {
    // Replace with the workers.dev URL shown by Cloudflare after deployment.
    const val BASE_URL = "https://online-flipper-pairing.YOUR_SUBDOMAIN.workers.dev"

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    fun isConfigured(): Boolean =
        BASE_URL.startsWith("https://") && !BASE_URL.contains("YOUR_SUBDOMAIN")

    fun createSession(callback: (Result<JSONObject>) -> Unit) {
        post("/api/sessions", JSONObject().put("duration", "24h"), callback)
    }

    fun join(sessionId: String, joinCode: String, deviceName: String, callback: (Result<JSONObject>) -> Unit) {
        post("/api/sessions/$sessionId/join",
            JSONObject().put("joinCode", joinCode).put("deviceName", deviceName), callback)
    }

    fun joinStatus(sessionId: String, requestToken: String, callback: (Result<JSONObject>) -> Unit) {
        post("/api/sessions/$sessionId/join/status",
            JSONObject().put("requestToken", requestToken), callback)
    }

    fun ownerStatus(sessionId: String, ownerToken: String, callback: (Result<JSONObject>) -> Unit) {
        val request = Request.Builder().url(BASE_URL.trimEnd('/') + "/api/sessions/$sessionId/status")
            .header("x-owner-token", ownerToken).get().build()
        http.newCall(request).enqueue(resultCallback(callback))
    }

    fun approve(sessionId: String, ownerToken: String, requestId: String, callback: (Result<JSONObject>) -> Unit) {
        post("/api/sessions/$sessionId/approve",
            JSONObject().put("ownerToken", ownerToken).put("requestId", requestId), callback)
    }

    fun deny(sessionId: String, ownerToken: String, requestId: String, callback: (Result<JSONObject>) -> Unit) {
        post("/api/sessions/$sessionId/deny",
            JSONObject().put("ownerToken", ownerToken).put("requestId", requestId), callback)
    }

    fun revoke(sessionId: String, ownerToken: String, callback: (Result<JSONObject>) -> Unit) {
        post("/api/sessions/$sessionId/revoke", JSONObject().put("ownerToken", ownerToken), callback)
    }

    fun connect(sessionId: String, token: String, onMessage: (WebSocket, JSONObject) -> Unit, onState: (String) -> Unit): WebSocket {
        val wsBase = BASE_URL.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
        val request = Request.Builder()
            .url("$wsBase/api/sessions/$sessionId/ws?token=$token")
            .build()
        return http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { onState("connected") }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try { onMessage(webSocket, JSONObject(text)) } catch (_: Exception) { }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { onState("closing") }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { onState("disconnected") }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onState("error: ${t.message ?: "connection failed"}")
            }
        })
    }

    fun sendCommand(socket: WebSocket?, action: String): Boolean {
        if (socket == null || action !in setOf("get_status", "vibrate", "ring", "stop_ring")) return false
        val id = java.util.UUID.randomUUID().toString()
        return socket.send(JSONObject().put("type", "command").put("id", id).put("action", action).toString())
    }

    fun sendResult(socket: WebSocket?, id: String?, payload: JSONObject) {
        socket?.send(JSONObject().put("type", "result").put("id", id).put("payload", payload).toString())
    }

    private fun post(path: String, body: JSONObject, callback: (Result<JSONObject>) -> Unit) {
        val request = Request.Builder().url(BASE_URL.trimEnd('/') + path)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        http.newCall(request).enqueue(resultCallback(callback))
    }

    private fun resultCallback(callback: (Result<JSONObject>) -> Unit) = object : Callback {
        override fun onFailure(call: Call, e: IOException) { callback(Result.failure(e)) }
        override fun onResponse(call: Call, response: Response) {
            response.use {
                val raw = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    val message = try { JSONObject(raw).optString("error", "HTTP ${it.code}") } catch (_: Exception) { "HTTP ${it.code}" }
                    callback(Result.failure(IllegalStateException(message)))
                } else {
                    try { callback(Result.success(JSONObject(raw))) }
                    catch (e: Exception) { callback(Result.failure(e)) }
                }
            }
        }
    }
}
