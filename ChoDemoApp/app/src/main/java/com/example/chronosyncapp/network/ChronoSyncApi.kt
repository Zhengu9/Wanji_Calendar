package com.example.chronosyncapp.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class ChronoSyncApi(
	private val client: OkHttpClient,
	private val baseUrl: String,
) {
	data class Token(
		val accessToken: String,
		val tokenType: String,
		val expiresIn: Long,
	)

	data class Provider(
		val id: String,
		val name: String,
		val capabilities: List<String>,
	)

	data class RemoteEvent(
		val id: String,
		val title: String,
		val description: String?,
		val startTime: String,
		val endTime: String?,
		val location: String?,
		val status: String?,
		val type: String?,      // WORK, LIFE, STUDY
		val priority: Int?,    // 1=低, 2=中, 3=高
	)

	data class RemoteMemo(
		val id: String,
		val content: String,
		val tags: List<String>,
		val updatedAt: String?,
	)

	data class PageResult<T>(
		val items: List<T>,
		val total: Int,
		val page: Int,
		val size: Int,
	)

	data class AgentResponse(
		val action: String,
		val entity: String,
		val data: JSONObject,
		val reply: String,
	)

	class ApiException(
		val code: Int,
		val rawBody: String?,
		message: String,
	) : IOException(message)

	private fun baseHttpUrl(): HttpUrl = (baseUrl.trimEnd('/') + "/").toHttpUrl()

	private fun buildUrl(path: String, query: Map<String, String?> = emptyMap()): HttpUrl {
		val url = baseHttpUrl().newBuilder()
			.addPathSegments(path.trimStart('/'))
		for ((k, v) in query) {
			if (!v.isNullOrBlank()) url.addQueryParameter(k, v)
		}
		return url.build()
	}

	private fun jsonRequestBody(obj: JSONObject) =
		obj.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

	private fun executeForString(req: Request): String {
		val resp = client.newCall(req).execute()
		resp.use {
			val body = it.body?.string()
			if (!it.isSuccessful) {
				throw ApiException(it.code, body, "HTTP ${it.code}")
			}
			return body.orEmpty()
		}
	}

	private fun executeForJsonObject(req: Request): JSONObject {
		val body = executeForString(req)
		return if (body.isBlank()) JSONObject() else JSONObject(body)
	}

	private fun authHeader(token: String) = "Bearer $token"

	fun register(username: String, password: String): JSONObject {
		val url = buildUrl("/api/v1/auth/register")
		val body = JSONObject().put("username", username).put("password", password)
		val req = Request.Builder()
			.url(url)
			.header("Content-Type", "application/json")
			.post(jsonRequestBody(body))
			.build()
		return executeForJsonObject(req)
	}

	fun login(username: String, password: String): Token {
		val url = buildUrl("/api/v1/auth/login")
		val body = JSONObject()
			.put("username", username)
			.put("password", password)
		val req = Request.Builder()
			.url(url)
			.header("Content-Type", "application/json")
			.post(jsonRequestBody(body))
			.build()
		val obj = executeForJsonObject(req)
		return Token(
			accessToken = obj.getString("access_token"),
			tokenType = obj.optString("token_type", "bearer"),
			expiresIn = obj.optLong("expires_in", 0L),
		)
	}

	fun listProviders(token: String): List<Provider> {
		val url = buildUrl("/api/v1/providers/")
		val req = Request.Builder()
			.url(url)
			.header("Authorization", authHeader(token))
			.get()
			.build()
		val body = executeForString(req)
		val arr = JSONArray(body)
		return (0 until arr.length()).map { idx ->
			val o = arr.getJSONObject(idx)
			val caps = o.optJSONArray("capabilities")?.let { a ->
				(0 until a.length()).map { a.getString(it) }
			} ?: emptyList()
			Provider(
				id = o.getString("id"),
				name = o.optString("name"),
				capabilities = caps,
			)
		}
	}

	fun listEvents(
		token: String,
		startDate: String? = null,
		endDate: String? = null,
		page: Int = 1,
		size: Int = 200,
	): PageResult<RemoteEvent> {
		val url = buildUrl(
			"/api/v1/events/",
			mapOf(
				"start_date" to startDate,
				"end_date" to endDate,
				"page" to page.toString(),
				"size" to size.toString(),
			),
		)
		val req = Request.Builder()
			.url(url)
			.header("Authorization", authHeader(token))
			.get()
			.build()
		val obj = executeForJsonObject(req)
		val itemsArr = obj.optJSONArray("items") ?: JSONArray()
		val items = (0 until itemsArr.length()).map { idx ->
			val o = itemsArr.getJSONObject(idx)
			RemoteEvent(
				id = o.getString("id"),
				title = o.optString("title"),
				description = o.optString("description").takeIf { it.isNotBlank() },
				startTime = o.getString("start_time"),
				endTime = o.optString("end_time").takeIf { it.isNotBlank() },
				location = o.optString("location").takeIf { it.isNotBlank() },
				status = o.optString("status").takeIf { it.isNotBlank() },
				type = o.optString("type").takeIf { it.isNotBlank() },
				priority = o.optInt("priority").takeIf { it != 0 },
			)
		}
		return PageResult(
			items = items,
			total = obj.optInt("total", items.size),
			page = obj.optInt("page", page),
			size = obj.optInt("size", size),
		)
	}

	fun createEvent(
		token: String,
		title: String,
		description: String? = null,
		startTime: String,
		endTime: String? = null,
		location: String? = null,
		status: String? = null,
		type: String? = null,
		priority: Int? = null,
	): RemoteEvent {
		val url = buildUrl("/api/v1/events/")
		val body = JSONObject()
			.put("title", title)
			.put("start_time", startTime)
		if (!description.isNullOrBlank()) body.put("description", description)
		if (!endTime.isNullOrBlank()) body.put("end_time", endTime)
		if (!location.isNullOrBlank()) body.put("location", location)
		if (!status.isNullOrBlank()) body.put("status", status)
		if (!type.isNullOrBlank()) body.put("type", type)
		if (priority != null) body.put("priority", priority)
		val req = Request.Builder()
			.url(url)
			.header("Authorization", authHeader(token))
			.header("Content-Type", "application/json")
			.post(jsonRequestBody(body))
			.build()
		val o = executeForJsonObject(req)
		return RemoteEvent(
			id = o.getString("id"),
			title = o.optString("title"),
			description = o.optString("description").takeIf { it.isNotBlank() },
			startTime = o.getString("start_time"),
			endTime = o.optString("end_time").takeIf { it.isNotBlank() },
			location = o.optString("location").takeIf { it.isNotBlank() },
			status = o.optString("status").takeIf { it.isNotBlank() },
			type = o.optString("type").takeIf { it.isNotBlank() },
			priority = o.optInt("priority").takeIf { it != 0 },
		)
	}

	fun updateEvent(
		token: String,
		eventId: String,
		title: String? = null,
		description: String? = null,
		startTime: String? = null,
		endTime: String? = null,
		location: String? = null,
		status: String? = null,
		type: String? = null,
		priority: Int? = null,
	): RemoteEvent {
		val url = buildUrl("/api/v1/events/$eventId")
		val body = JSONObject()
		if (!title.isNullOrBlank()) body.put("title", title)
		if (!description.isNullOrBlank()) body.put("description", description)
		if (!startTime.isNullOrBlank()) body.put("start_time", startTime)
		if (!endTime.isNullOrBlank()) body.put("end_time", endTime)
		if (!location.isNullOrBlank()) body.put("location", location)
		if (!status.isNullOrBlank()) body.put("status", status)
		if (!type.isNullOrBlank()) body.put("type", type)
		if (priority != null) body.put("priority", priority)
		val req = Request.Builder()
			.url(url)
			.header("Authorization", authHeader(token))
			.header("Content-Type", "application/json")
			.put(jsonRequestBody(body))
			.build()
		val o = executeForJsonObject(req)
		return RemoteEvent(
			id = o.getString("id"),
			title = o.optString("title"),
			description = o.optString("description").takeIf { it.isNotBlank() },
			startTime = o.getString("start_time"),
			endTime = o.optString("end_time").takeIf { it.isNotBlank() },
			location = o.optString("location").takeIf { it.isNotBlank() },
			status = o.optString("status").takeIf { it.isNotBlank() },
			type = o.optString("type").takeIf { it.isNotBlank() },
			priority = o.optInt("priority").takeIf { it != 0 },
		)
	}

	fun deleteEvent(token: String, eventId: String) {
		val url = buildUrl("/api/v1/events/$eventId")
		val req = Request.Builder()
			.url(url)
			.header("Authorization", authHeader(token))
			.delete()
			.build()
		executeForString(req)
	}

	fun listMemos(token: String, page: Int = 1, size: Int = 200): PageResult<RemoteMemo> {
		val url = buildUrl(
			"/api/v1/memos/",
			mapOf("page" to page.toString(), "size" to size.toString()),
		)
		val req = Request.Builder()
			.url(url)
			.header("Authorization", authHeader(token))
			.get()
			.build()
		val obj = executeForJsonObject(req)
		val itemsArr = obj.optJSONArray("items") ?: JSONArray()
		val items = (0 until itemsArr.length()).map { idx ->
			val o = itemsArr.getJSONObject(idx)
			val tags = o.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
			RemoteMemo(
				id = o.getString("id"),
				content = o.optString("content"),
				tags = tags,
				updatedAt = o.optString("updated_at").takeIf { it.isNotBlank() },
			)
		}
		return PageResult(
			items = items,
			total = obj.optInt("total", items.size),
			page = obj.optInt("page", page),
			size = obj.optInt("size", size),
		)
	}

	fun createMemo(token: String, content: String, tags: List<String> = emptyList()): RemoteMemo {
		val url = buildUrl("/api/v1/memos/")
		val body = JSONObject().put("content", content)
		if (tags.isNotEmpty()) {
			val arr = JSONArray()
			tags.forEach { arr.put(it) }
			body.put("tags", arr)
		}
		val req = Request.Builder()
			.url(url)
			.header("Authorization", authHeader(token))
			.header("Content-Type", "application/json")
			.post(jsonRequestBody(body))
			.build()
		val o = executeForJsonObject(req)
		val rt = o.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
		return RemoteMemo(
			id = o.getString("id"),
			content = o.optString("content"),
			tags = rt,
			updatedAt = o.optString("updated_at").takeIf { it.isNotBlank() },
		)
	}

	fun updateMemo(token: String, memoId: String, content: String? = null, tags: List<String>? = null): RemoteMemo {
		val url = buildUrl("/api/v1/memos/$memoId")
		val body = JSONObject()
		if (content != null) body.put("content", content)
		if (tags != null) {
			val arr = JSONArray()
			tags.forEach { arr.put(it) }
			body.put("tags", arr)
		}
		val req = Request.Builder()
			.url(url)
			.header("Authorization", authHeader(token))
			.header("Content-Type", "application/json")
			.put(jsonRequestBody(body))
			.build()
		val o = executeForJsonObject(req)
		val rt = o.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
		return RemoteMemo(
			id = o.getString("id"),
			content = o.optString("content"),
			tags = rt,
			updatedAt = o.optString("updated_at").takeIf { it.isNotBlank() },
		)
	}

	fun deleteMemo(token: String, memoId: String) {
		val url = buildUrl("/api/v1/memos/$memoId")
		val req = Request.Builder()
			.url(url)
			.header("Authorization", authHeader(token))
			.delete()
			.build()
		executeForString(req)
	}

	fun processAgent(token: String, text: String, conversationId: String? = null): AgentResponse {
		val url = buildUrl("/api/v1/agent/process")
		val body = JSONObject().put("text", text)
		if (!conversationId.isNullOrBlank()) body.put("conversation_id", conversationId)
		val req = Request.Builder()
			.url(url)
			.header("Authorization", authHeader(token))
			.header("Content-Type", "application/json")
			.post(jsonRequestBody(body))
			.build()
		val obj = executeForJsonObject(req)
		return AgentResponse(
			action = obj.optString("action"),
			entity = obj.optString("entity"),
			data = obj.optJSONObject("data") ?: JSONObject(),
			reply = obj.optString("reply"),
		)
	}

	// ========== Agent 对话历史 API (V2) ==========

	data class AgentConversation(
		val id: String,
		val userId: String,
		val role: String,  // "user" or "assistant"
		val content: String,
		val createdAt: String,
	)

	data class AgentConversationList(
		val items: List<AgentConversation>,
		val total: Int,
	)

	data class AgentConversationClearResponse(
		val status: String,
		val message: String,
		val deletedCount: Int,
	)

	fun getAgentConversations(
		token: String,
		limit: Int = 50,
		offset: Int = 0,
	): AgentConversationList {
		val url = buildUrl(
			"/api/v1/agent/conversations",
			mapOf("limit" to limit.toString(), "offset" to offset.toString()),
		)
		val req = Request.Builder()
			.url(url)
			.header("Authorization", authHeader(token))
			.get()
			.build()
		val obj = executeForJsonObject(req)
		val itemsArr = obj.optJSONArray("items") ?: JSONArray()
		val items = (0 until itemsArr.length()).map { idx ->
			val o = itemsArr.getJSONObject(idx)
			AgentConversation(
				id = o.getString("id"),
				userId = o.getString("user_id"),
				role = o.getString("role"),
				content = o.getString("content"),
				createdAt = o.getString("created_at"),
			)
		}
		return AgentConversationList(
			items = items,
			total = obj.optInt("total", items.size),
		)
	}

	fun clearAgentConversations(token: String): AgentConversationClearResponse {
		val url = buildUrl("/api/v1/agent/conversations")
		val req = Request.Builder()
			.url(url)
			.header("Authorization", authHeader(token))
			.delete()
			.build()
		val obj = executeForJsonObject(req)
		return AgentConversationClearResponse(
			status = obj.optString("status"),
			message = obj.optString("message"),
			deletedCount = obj.optInt("deleted_count"),
		)
	}
}
