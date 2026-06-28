# 同步问题排查指南

## 🔍 快速判断前端还是后端问题

### 测试 1：直接查看后端数据（最准确）

```bash
# 1. 登录获取 token
curl -X POST http://115.190.155.26:8000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"你的用户名","password":"你的密码"}'

# 2. 查看云端日程列表
curl "http://115.190.155.26:8000/api/v1/events/" \
  -H "Authorization: Bearer 你的TOKEN"
```

**如果后端返回的列表里没有 AI 刚创建的日程 → 后端问题**  
**如果后端有，但前端没有显示 → 前端问题**

---

### 测试 2：查看前端日志

在 `MainActivity.kt` 的 `selectTab` 函数中添加日志：

```kotlin
private fun selectTab(index: Int) {
    calendarTab.visibility = if (index == 0) View.VISIBLE else View.GONE
    assistantTab.visibility = if (index == 1) View.VISIBLE else View.GONE
    
    if (index == 0) {
        lifecycleScope.launch {
            val token = getAccessToken()
            android.util.Log.d("SyncDebug", "Token: $token")
            
            withContext(Dispatchers.IO) { 
                runCatching { syncFromServerIfNeeded() } 
            }
            
            // 检查本地数据库数量
            val localCount = withContext(Dispatchers.IO) { eventDao.count() }
            android.util.Log.d("SyncDebug", "本地日程数量: $localCount")
            
            refreshMarkers()
            selectedDate?.let { refreshEventsForDate(it) }
        }
    }
}
```

然后在 Android Studio 的 Logcat 中查看输出。

---

### 测试 3：强制刷新测试

修改 `syncFromServerIfNeeded` 暂时去掉 `syncedFromServer` 检查：

```kotlin
private suspend fun syncFromServerIfNeeded() {
    // 暂时注释掉这行，强制每次都同步
    // if (syncedFromServer) return
    
    val token = getAccessToken() ?: return
    android.util.Log.d("SyncDebug", "开始同步，token=$token")
    
    // ... 原有代码 ...
    
    android.util.Log.d("SyncDebug", "同步完成，拉取了 ${events.size} 条日程")
}
```

如果强制刷新后数据出来了，说明是 `syncedFromServer` 标志的问题。

---

## 🎯 最可能的原因

### 原因 1：AI 创建日程其实失败了
虽然 AI 回复"已创建日程"，但实际后端可能没存成功。

**验证**：用 curl 直接查后端，看有没有这条数据。

### 原因 2：`syncedFromServer` 标志导致跳过同步
如果之前已经同步过，`syncedFromServer = true`，就不会再拉取新数据。

**验证**：在日志中看是否打印了"开始同步"。

### 原因 3：日期范围问题
`syncFromServerIfNeeded` 只拉取 `today-180天` 到 `today+365天` 的数据。

如果 AI 创建的日程不在这个范围内（比如 2025 年的日期），就拉取不到。

**验证**：查看 AI 创建的日程日期是否在合理范围内。

### 原因 4：时区问题
前端发送给后端的日期可能有时区问题，导致数据存到了不同的日期。

---

## ✅ 建议的排查步骤

1. **先确认后端有没有数据**（curl 查询）
2. **如果后端有，前端没有** → 前端同步逻辑问题
3. **如果后端没有** → 后端 AI 创建日程的问题

你先做第一步测试，告诉我后端有没有数据，我再帮你定位！
