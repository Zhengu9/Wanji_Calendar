package com.example.chronosyncapp.agent

import com.example.chronosyncapp.data.AppDatabase
import com.example.chronosyncapp.data.ConversationHistoryEntity
import com.example.chronosyncapp.data.MemoEntity
import com.example.chronosyncapp.data.ScheduleEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Kimi Agent 服务 —— 方案A核心
 *
 * 直接调用 Kimi API（OpenAI 兼容格式 + Function Calling），
 * 对本地 Room 数据库执行日程和备忘录的增删改查操作。
 */
class KimiAgentService(
    private val db: AppDatabase,
    private val apiKey: String,
    private val baseUrl: String = "https://api.moonshot.cn",
    private val model: String = "kimi-k2-turbo-preview",
) {
    private val eventDao = db.scheduleEventDao()
    private val memoDao = db.memoDao()
    private val conversationDao = db.conversationHistoryDao()

    private val beijingZone = ZoneId.of("Asia/Shanghai")
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 结果类型 */
    sealed class AgentResult {
        data class Reply(val text: String) : AgentResult()
        data class Error(val message: String) : AgentResult()
        data class ScheduleChanged(val reply: String, val date: String) : AgentResult()
    }

    /**
     * 处理用户消息，返回AI回复
     */
    suspend fun processUserMessage(
        userId: Long,
        userMessage: String,
    ): AgentResult = withContext(Dispatchers.IO) {
        try {
            // 加载历史
            val history = conversationDao.getRecent(30)
            // 保存用户消息
            conversationDao.insert(ConversationHistoryEntity("user", userMessage))

            // 构造消息数组
            val messages = JSONArray()
            messages.put(systemMessage())
            for (h in history) {
                if (h.role == "user") messages.put(userMsg(h.content))
                else messages.put(assistantMsg(h.content))
            }
            messages.put(userMsg(userMessage))

            // 调用Kimi
            var loopCount = 0
            while (loopCount < 5) {
                loopCount++
                val resp = callKimi(messages)
                val choice = resp.optJSONArray("choices")?.optJSONObject(0) ?: break
                val msg = choice.optJSONObject("message") ?: break
                val finishReason = choice.optString("finish_reason", "stop")

                if (finishReason == "tool_calls" || msg.has("tool_calls")) {
                    // 执行工具调用
                    val toolCalls = msg.optJSONArray("tool_calls") ?: break
                    // 保存assistant消息（含tool_calls）
                    conversationDao.insert(ConversationHistoryEntity("assistant", msg.toString()))
                    messages.put(msg)

                    for (i in 0 until toolCalls.length()) {
                        val tc = toolCalls.optJSONObject(i) ?: continue
                        val fn = tc.optJSONObject("function") ?: continue
                        val name = fn.optString("name", "")
                        val args = fn.optString("arguments", "{}")
                        val toolId = tc.optString("id", "call_$i")

                        val result = executeToolCall(name, JSONObject(args))
                        conversationDao.insert(ConversationHistoryEntity("tool", result))
                        messages.put(toolMsg(toolId, result))
                    }
                    // 继续循环，把tool结果送回Kimi
                } else {
                    // 纯文本回复
                    val reply = msg.optString("content", "处理完成")
                    conversationDao.insert(ConversationHistoryEntity("assistant", reply))
                    return@withContext AgentResult.Reply(reply)
                }
            }
            AgentResult.Error("对话轮次超限")
        } catch (e: Exception) {
            AgentResult.Error(e.message ?: "未知错误")
        }
    }

    // ===== 消息构造 =====

    private fun systemMessage(): JSONObject = JSONObject().apply {
        val now = java.time.LocalDateTime.now(beijingZone)
        put("role", "system")
        put("content", """
你是"万机"，一个智能日程助手，运行在用户的Android手机上。

当前北京时间：${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}
星期：${now.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.CHINESE)}

核心能力：
1. 创建、查询、修改、删除日程
2. 管理备忘录
3. 日程冲突检测和统计

规则：
- 所有时间使用北京时间（Asia/Shanghai）
- 日期格式 yyyy-MM-dd，时间格式 HH:mm
- 日程类型：WORK（工作）、LIFE（生活）、STUDY（学习）
- 优先级：1=低、2=中、3=高
- 创建日程前自动检测时间冲突
- 如果用户输入的时间不明确，请主动询问
- 回复简洁友好，使用中文
- 创建/修改/删除日程后简要确认操作结果
        """.trimIndent())
    }

    private fun userMsg(content: String) = JSONObject().apply {
        put("role", "user")
        put("content", content)
    }

    private fun assistantMsg(content: String) = JSONObject().apply {
        put("role", "assistant")
        put("content", content)
    }

    private fun toolMsg(toolCallId: String, content: String) = JSONObject().apply {
        put("role", "tool")
        put("tool_call_id", toolCallId)
        put("content", content)
    }

    // ===== 调用 Kimi API =====

    private fun callKimi(messages: JSONArray): JSONObject {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("tools", buildTools())
            put("temperature", 0.3)
            put("max_tokens", 4096)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val req = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody(mediaType))
            .build()

        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful) {
            throw Exception("Kimi API 返回错误：HTTP ${resp.code}")
        }
        val respBody = resp.body?.string() ?: throw Exception("Kimi API 返回空")
        return JSONObject(respBody)
    }

    // ===== 工具定义 =====

    private fun buildTools(): JSONArray = JSONArray().apply {
        put(toolDef("add_schedule", "创建新的日程安排", JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject().apply {
                put("title", prop("string", "日程标题"))
                put("date", prop("string", "日期，格式yyyy-MM-dd，如2026-07-01"))
                put("startTime", prop("string", "开始时间，格式HH:mm，如14:00"))
                put("endTime", prop("string", "结束时间，格式HH:mm，如15:30"))
                put("type", JSONObject().apply {
                    put("type", "string")
                    put("enum", JSONArray(listOf("WORK", "LIFE", "STUDY")))
                    put("description", "日程类型")
                })
                put("priority", JSONObject().apply {
                    put("type", "integer")
                    put("enum", JSONArray(listOf(1, 2, 3)))
                    put("description", "优先级 1低/2中/3高")
                })
            })
            put("required", JSONArray(listOf("title", "date", "startTime", "endTime")))
        }))

        put(toolDef("query_schedule", "查询指定日期的日程", JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject().apply {
                put("date", prop("string", "日期 yyyy-MM-dd，不填默认今天"))
            })
        }))

        put(toolDef("query_schedule_range", "查询日期范围内的日程", JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject().apply {
                put("startDate", prop("string", "开始日期 yyyy-MM-dd"))
                put("endDate", prop("string", "结束日期 yyyy-MM-dd"))
            })
            put("required", JSONArray(listOf("startDate", "endDate")))
        }))

        put(toolDef("delete_schedule", "删除日程（需要先通过query_schedule获取日程ID）", JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject().apply {
                put("scheduleId", prop("integer", "日程的本地ID"))
            })
            put("required", JSONArray(listOf("scheduleId")))
        }))

        put(toolDef("update_schedule", "修改日程（只需提供要修改的字段）", JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject().apply {
                put("scheduleId", prop("integer", "日程的本地ID"))
                put("title", prop("string", "新标题（可选）"))
                put("date", prop("string", "新日期（可选）"))
                put("startTime", prop("string", "新开始时间（可选）"))
                put("endTime", prop("string", "新结束时间（可选）"))
                put("type", prop("string", "新类型（可选）"))
                put("priority", prop("integer", "新优先级（可选）"))
            })
            put("required", JSONArray(listOf("scheduleId")))
        }))

        put(toolDef("add_memo", "创建备忘录", JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject().apply {
                put("title", prop("string", "备忘录标题"))
                put("content", prop("string", "备忘录内容"))
            })
            put("required", JSONArray(listOf("title", "content")))
        }))

        put(toolDef("query_memo", "查询备忘录", JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject().apply {
                put("keyword", prop("string", "搜索关键词，不填返回全部"))
            })
        }))

        put(toolDef("statistics", "获取日程统计概览", JSONObject().apply {
            put("type", "object")
            put("properties", JSONObject())
        }))
    }

    private fun toolDef(name: String, desc: String, params: JSONObject) = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", name)
            put("description", desc)
            put("parameters", params)
        })
    }

    private fun prop(type: String, desc: String) = JSONObject().apply {
        put("type", type)
        put("description", desc)
    }

    // ===== 工具执行 =====

    private suspend fun executeToolCall(name: String, args: JSONObject): String {
        return try {
            when (name) {
                "add_schedule" -> addSchedule(args)
                "query_schedule" -> querySchedule(args)
                "query_schedule_range" -> queryScheduleRange(args)
                "delete_schedule" -> deleteSchedule(args)
                "update_schedule" -> updateSchedule(args)
                "add_memo" -> addMemo(args)
                "query_memo" -> queryMemo(args)
                "statistics" -> statistics()
                else -> """{"error":"未知工具: $name"}"""
            }
        } catch (e: Exception) {
            """{"error":"${e.message?.replace("\"", "'")}"}"""
        }
    }

    private suspend fun addSchedule(args: JSONObject): String {
        val title = args.optString("title")
        val date = args.optString("date")
        val startTime = args.optString("startTime")
        val endTime = args.optString("endTime")
        val type = args.optString("type", "LIFE")
        val priority = args.optInt("priority", 2)

        // 冲突检测
        val conflicts = detectConflict(date, startTime, endTime)
        if (conflicts.isNotEmpty()) {
            val c = conflicts.first()
            return """{"status":"conflict","message":"时间冲突！已有日程「${c.title}」(${c.startTime}-${c.endTime})","suggestion":"请更换时间或检查冲突"}"""
        }

        val entity = ScheduleEventEntity(0, null, date, title, startTime, endTime, type, priority)
        val id = eventDao.insert(entity)
        return """{"status":"ok","message":"已创建日程「$title」($date $startTime-$endTime)","scheduleId":$id}"""
    }

    private suspend fun querySchedule(args: JSONObject): String {
        val today = LocalDate.now(beijingZone).format(dateFmt)
        val date = args.optString("date", today).ifBlank { today }
        val events = eventDao.getEventsForDate(date)
        if (events.isEmpty()) return buildResult(date, emptyList())
        return buildResult(date, events)
    }

    private suspend fun queryScheduleRange(args: JSONObject): String {
        val start = args.optString("startDate")
        val end = args.optString("endDate")
        val events = eventDao.getEventsInRange(start, end)
        val grouped = events.groupBy { it.date }
        val sb = StringBuilder("日程范围 $start 至 $end：")
        if (events.isEmpty()) sb.append("\n暂无日程")
        else grouped.forEach { (d, list) ->
            sb.append("\n$d：")
            list.forEachIndexed { i, e ->
                sb.append("\n  ${i + 1}. [${e.id}] ${e.title} (${e.startTime}-${e.endTime}) [${e.type}${priorityLabel(e.priority)}]")
            }
        }
        return JSONObject().put("result", sb.toString()).toString()
    }

    private suspend fun deleteSchedule(args: JSONObject): String {
        val id = args.optLong("scheduleId")
        val all = eventDao.getAllEvents()
        val target = all.find { it.id == id }
            ?: return """{"status":"error","message":"未找到ID=$id的日程，请先通过query_schedule查询获取ID"}"""
        eventDao.delete(target)
        return """{"status":"ok","message":"已删除日程「${target.title}」"}"""
    }

    private suspend fun updateSchedule(args: JSONObject): String {
        val id = args.optLong("scheduleId")
        val all = eventDao.getAllEvents()
        val target = all.find { it.id == id }
            ?: return """{"status":"error","message":"未找到ID=$id的日程"}"""

        if (args.has("title")) target.title = args.optString("title")
        if (args.has("date")) target.date = args.optString("date")
        if (args.has("startTime")) target.startTime = args.optString("startTime")
        if (args.has("endTime")) target.endTime = args.optString("endTime")
        if (args.has("type")) target.type = args.optString("type")
        if (args.has("priority")) target.priority = args.optInt("priority")

        eventDao.update(target)
        return """{"status":"ok","message":"已更新日程「${target.title}」(${target.date} ${target.startTime}-${target.endTime})"}"""
    }

    private suspend fun addMemo(args: JSONObject): String {
        val title = args.optString("title")
        val content = args.optString("content")
        val now = System.currentTimeMillis()
        val entity = MemoEntity(0, null, title, content, now)
        val id = memoDao.insert(entity)
        return """{"status":"ok","message":"已创建备忘录「$title」","memoId":$id}"""
    }

    private suspend fun queryMemo(args: JSONObject): String {
        val keyword = args.optString("keyword", "")
        val memos = if (keyword.isBlank()) memoDao.getAll()
        else memoDao.searchByKeyword(keyword)
        if (memos.isEmpty()) return """{"result":"暂无备忘录"}"""
        val sb = StringBuilder("备忘录：")
        memos.forEachIndexed { i, m ->
            sb.append("\n${i + 1}. ${m.title}：${m.content.take(60)}${if (m.content.length > 60) "..." else ""}")
        }
        return JSONObject().put("result", sb.toString()).toString()
    }

    private fun statistics(): String {
        val events = eventDao.getAllEvents()
        val memos = memoDao.getAll()
        val typeCounts = events.groupBy { it.type }.mapValues { it.value.size }
        return JSONObject().apply {
            put("total_events", events.size)
            put("total_memos", memos.size)
            put("work_count", typeCounts["WORK"] ?: 0)
            put("life_count", typeCounts["LIFE"] ?: 0)
            put("study_count", typeCounts["STUDY"] ?: 0)
        }.toString()
    }

    // ===== 冲突检测 =====

    private suspend fun detectConflict(
        date: String,
        newStart: String,
        newEnd: String,
        excludeId: Long = 0,
    ): List<ScheduleEventEntity> {
        val events = eventDao.getEventsForDate(date)
        val newStartTime = parseTime(newStart)
        val newEndTime = parseTime(newEnd)
        return events.filter { e ->
            e.id != excludeId &&
            parseTime(e.startTime) < newEndTime &&
            parseTime(e.endTime) > newStartTime
        }
    }

    private fun parseTime(hhmm: String): LocalTime {
        return try {
            val parts = hhmm.split(":")
            LocalTime.of(parts[0].toInt(), parts[1].toInt())
        } catch (_: Exception) {
            LocalTime.of(9, 0)
        }
    }

    // ===== 辅助 =====

    private fun buildResult(date: String, events: List<ScheduleEventEntity>): String {
        val obj = JSONObject()
        obj.put("date", date)
        obj.put("count", events.size)
        val arr = JSONArray()
        events.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("title", e.title)
                put("startTime", e.startTime)
                put("endTime", e.endTime)
                put("type", e.type)
                put("priority", e.priority)
            })
        }
        obj.put("events", arr)
        return obj.toString()
    }

    private fun priorityLabel(p: Int): String = when (p) {
        3 -> "🔴"
        2 -> "🟡"
        else -> "🟢"
    }

    companion object {
        fun create(db: AppDatabase): KimiAgentService {
            val apiKey = com.example.chronosyncapp.BuildConfig.KIMI_API_KEY
            val baseUrl = com.example.chronosyncapp.BuildConfig.KIMI_BASE_URL.ifBlank { "https://api.moonshot.cn" }
            val model = com.example.chronosyncapp.BuildConfig.KIMI_MODEL.ifBlank { "kimi-k2-turbo-preview" }
            return KimiAgentService(db, apiKey, baseUrl, model)
        }
    }
}
