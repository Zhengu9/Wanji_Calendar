package com.example.chronosyncapp

import com.example.chronosyncapp.network.ChronoSyncApi
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * ChronoSync API 烟雾测试
 * 注意：这些测试需要后端服务运行，且会创建真实数据
 * 运行前请确保 BASE_URL 正确
 */
class ChronoSyncApiSmokeTest {

    private lateinit var api: ChronoSyncApi
    private val baseUrl = "http://115.190.155.26:8000"
    private val testUsername = "smoke_test_${System.currentTimeMillis()}"
    private val testPassword = "test123456"
    private var token: String? = null
    private var createdEventId: String? = null

    @Before
    fun setup() {
        // 初始化 OkHttp 客户端
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS) // 新增写超时，避免请求卡住
            .build()
        // 初始化 API 实例
        api = ChronoSyncApi(client, baseUrl)
    }

    // 封装登录逻辑，供其他测试复用（避免直接调用@Test方法）
    private fun doLogin(): String {
        if (token.isNullOrEmpty()) {
            val result = api.login(testUsername, testPassword)
            assertNotNull("登录失败：未获取到 token", result.accessToken)
            token = result.accessToken
        }
        return token!!
    }

    // 封装创建日程逻辑，供其他测试复用
    private fun doCreateEvent(): String {
        if (createdEventId.isNullOrEmpty()) {
            val token = doLogin()
            val now = java.time.OffsetDateTime.now()
            val startTime = now.toString()
            val endTime = now.plusHours(1).toString()

            val event = api.createEvent(
                token = token,
                title = "烟雾测试日程",
                description = "测试描述",
                startTime = startTime,
                endTime = endTime,
                type = "WORK",
                priority = 3
            )

            assertNotNull("创建日程失败：未返回 id", event.id)
            createdEventId = event.id
        }
        return createdEventId!!
    }

    @Test
    fun test1_Register() {
        println("测试用户注册...")
        val result = api.register(testUsername, testPassword)
        assertTrue("注册应该返回 user_id", result.has("user_id"))
        println("✅ 注册成功: ${result.getString("user_id")}")
    }

    @Test
    fun test2_LoginWithJsonFormat() {
        println("测试登录（JSON 格式）...")
        // 先确保用户已注册（如果注册失败，登录也会失败）
        try {
            api.register(testUsername, testPassword)
        } catch (e: Exception) {
            // 忽略重复注册异常（如果用户已存在）
            println("用户可能已存在，跳过注册：${e.message}")
        }
        
        val result = api.login(testUsername, testPassword)
        assertNotNull("应该获取到 token", result.accessToken)
        assertEquals("token_type 应该是 bearer", "bearer", result.tokenType)
        token = result.accessToken
        println("✅ 登录成功，获取到 token")
    }

    @Test
    fun test3_CreateEventWithTypeAndPriority() {
        println("测试创建日程（带 type/priority）...")
        val token = doLogin() // 使用封装的登录方法

        val now = java.time.OffsetDateTime.now()
        val startTime = now.toString()
        val endTime = now.plusHours(1).toString()

        val event = api.createEvent(
            token = token,
            title = "烟雾测试日程",
            description = "测试描述",
            startTime = startTime,
            endTime = endTime,
            type = "WORK",
            priority = 3
        )

        assertNotNull("创建成功应返回 id", event.id)
        assertEquals("type 应该一致", "WORK", event.type)
        assertEquals("priority 应该一致", 3, event.priority)
        
        createdEventId = event.id
        println("✅ 创建日程成功: id=${event.id}, type=${event.type}, priority=${event.priority}")
    }

    @Test
    fun test4_ListEvents() {
        println("测试查询日程列表...")
        val token = doLogin() // 使用封装的登录方法

        val result = api.listEvents(token)
        assertNotNull("应该返回列表", result.items)
        
        if (result.items.isNotEmpty()) {
            val first = result.items[0]
            println("第一条日程: id=${first.id}, title=${first.title}, type=${first.type}, priority=${first.priority}")
            
            // 验证字段存在
            assertNotNull("日程应该有 type 字段", first.type)
            assertNotNull("日程应该有 priority 字段", first.priority)
        }
        
        println("✅ 查询日程成功，共 ${result.items.size} 条")
    }

    @Test
    fun test5_UpdateEvent() {
        println("测试更新日程...")
        val token = doLogin() // 使用封装的登录方法
        val eventId = doCreateEvent() // 使用封装的创建日程方法

        val updated = api.updateEvent(
            token = token,
            eventId = eventId,
            title = "已更新的日程",
            type = "LIFE",
            priority = 1
        )

        assertEquals("type 应该更新为 LIFE", "LIFE", updated.type)
        assertEquals("priority 应该更新为 1", 1, updated.priority)
        println("✅ 更新日程成功: type=${updated.type}, priority=${updated.priority}")
    }

    @Test
    fun test6_CreateMemo() {
        println("测试创建备忘录...")
        val token = doLogin() // 使用封装的登录方法

        // 备忘录保持现状：title 打包到 content 的 JSON 中
        val content = """{"title": "测试备忘", "content": "这是测试内容"}"""
        
        val memo = api.createMemo(
            token = token,
            content = content,
            tags = listOf("test", "smoke")
        )

        assertNotNull("创建成功应返回 id", memo.id)
        assertTrue("content 应该包含 title", memo.content.contains("title"))
        println("✅ 创建备忘录成功: id=${memo.id}")
    }

    @Test
    fun test7_DeleteEvent() {
        println("测试删除日程...")
        val token = doLogin() // 使用封装的登录方法
        val eventId = doCreateEvent() // 使用封装的创建日程方法

        // 删除不应该抛出异常
        try {
            api.deleteEvent(token, eventId)
            println("✅ 删除日程成功")
        } catch (e: Exception) {
            fail("删除日程不应该失败: ${e.message}")
        }
    }
}