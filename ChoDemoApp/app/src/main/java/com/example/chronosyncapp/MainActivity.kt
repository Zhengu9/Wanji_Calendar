package com.example.chronosyncapp

import android.annotation.SuppressLint
import android.Manifest
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.app.TimePickerDialog
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.updatePadding
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.CalendarMonth
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.core.nextMonth
import com.kizitonwose.calendar.core.previousMonth
import com.kizitonwose.calendar.view.CalendarView
import com.kizitonwose.calendar.view.DaySize
import com.kizitonwose.calendar.view.MarginValues
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.MonthHeaderFooterBinder
import com.kizitonwose.calendar.view.ViewContainer
import com.example.chronosyncapp.LynxCard.DemoTemplateProvider
import com.lynx.tasm.LynxView
import com.lynx.tasm.LynxViewBuilder
import com.example.chronosyncapp.data.AppDatabase
import com.example.chronosyncapp.data.EventMarker
import com.example.chronosyncapp.data.MemoDao
import com.example.chronosyncapp.data.MemoEntity
import com.example.chronosyncapp.data.ScheduleEventDao
import com.example.chronosyncapp.data.ScheduleEventEntity
import com.example.chronosyncapp.network.ChronoSyncApi
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.YearMonth
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume
import java.io.ByteArrayOutputStream
import java.io.File

class MainActivity : AppCompatActivity() {

	private lateinit var calendarView: CalendarView
	private lateinit var monthTitleText: TextView
	private lateinit var selectedDateText: TextView
	private lateinit var eventsRecyclerView: RecyclerView
	private lateinit var addEventFab: View
	private lateinit var locationFab: View
	private lateinit var calendarTab: View
	private lateinit var assistantTab: View
	private lateinit var memoTab: View
	private lateinit var lynxTab: View
	private lateinit var memoRecyclerView: RecyclerView
	private lateinit var memoEmptyText: TextView
	private lateinit var addMemoButton: Button
	private lateinit var memoScanButton: View
	private var lynxView: LynxView? = null
	private val eventsAdapter = EventsAdapter()
	private val memoAdapter = MemoAdapter()
	
	// 登录状态按钮
	private lateinit var loginStatusImage: android.widget.ImageView
	
	// Agent V2 - 对话历史
	private lateinit var conversationRecyclerView: RecyclerView
	private val conversationAdapter = ConversationAdapter()
	private lateinit var clearConversationButton: View
	private lateinit var suggestionLayout: View
	private lateinit var suggestionText: TextView
	private lateinit var acceptSuggestionButton: View
	private lateinit var rejectSuggestionButton: View
	private var isWaitingForSuggestionConfirm = false
	private lateinit var db: AppDatabase
	private lateinit var eventDao: ScheduleEventDao
	private lateinit var memoDao: MemoDao
	private var markersByDate: Map<LocalDate, List<EventMarker>> = emptyMap()
	// 当前页面数据刷新时用到的缓存

	private var selectedDate: LocalDate? = null
	private var pendingMemoPhotoUri: Uri? = null

	private var syncedFromServer: Boolean = false
	private val hhmmFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

	private fun getAccessToken(): String? = ChronoSyncApp.prefs.getString("access_token", null)
	private fun setAccessToken(token: String?) {
		ChronoSyncApp.prefs.edit().putString("access_token", token).apply()
	}

	private fun isLoggedIn(): Boolean = !getAccessToken().isNullOrBlank()
	
	private fun updateLoginStatusUI() {
		// 更新登录状态图标
		if (::loginStatusImage.isInitialized) {
			if (isLoggedIn()) {
				// 已登录 - 使用亮色图标
				loginStatusImage.setImageResource(R.drawable.ic_tab_assistant)
				loginStatusImage.alpha = 1.0f
				loginStatusImage.contentDescription = "已登录，点击退出"
			} else {
				// 未登录 - 使用灰色图标提示
				loginStatusImage.setImageResource(R.drawable.ic_tab_assistant)
				loginStatusImage.alpha = 0.5f
				loginStatusImage.contentDescription = "未登录，点击登录"
			}
		}
	}

	private fun formatChronoSyncError(t: Throwable): String {
		val apiEx = (t as? ChronoSyncApi.ApiException)
		if (apiEx != null) {
			val body = apiEx.rawBody?.takeIf { it.isNotBlank() }?.let { "; body=${it.take(300)}" }.orEmpty()
			return "HTTP ${apiEx.code}${body}"
		}
		return t.message?.takeIf { it.isNotBlank() } ?: t.toString()
	}

	private fun handleApiError(t: Throwable) {
		val apiEx = t as? ChronoSyncApi.ApiException
		if (apiEx != null && apiEx.code == 401) {
			setAccessToken(null)
			syncedFromServer = false
			updateLoginStatusUI()
			lifecycleScope.launch {
				refreshMemos()
				selectedDate?.let { refreshEventsForDate(it) }
			}
			// 自动弹出登录对话框
			showAuthDialog {
				updateLoginStatusUI()
				toast("登录成功，请重新操作")
			}
		} else {
			toast("操作失败：${formatChronoSyncError(t)}")
		}
	}

	private fun encodeMemoPayload(title: String, content: String): String {
		return JSONObject()
			.put("title", title)
			.put("content", content)
			.toString()
	}

	private fun decodeMemoPayload(raw: String): Pair<String, String> {
		// 尝试解析 JSON 格式（如果 content 是 {title, content} JSON）
		val obj = runCatching { JSONObject(raw) }.getOrNull()
		if (obj != null) {
			val title = obj.optString("title")?.trim().orEmpty()
			val content = obj.optString("content")?.trim().orEmpty()
			if (title.isNotBlank() || content.isNotBlank()) {
				return title.ifBlank { "未命名" } to content
			}
		}
		// 纯文本格式：整个 content 作为备忘录内容，标题取前几个字或"未命名"
		val trimmed = raw.trim()
		val title = if (trimmed.length <= 10) trimmed else trimmed.substring(0, 10) + "..."
		return (if (title.isBlank()) "未命名" else title) to trimmed
	}

	// 注意：type 和 priority 现在作为独立字段由后端原生支持，
	// 不再需要 encode/decode 到 description 中

	private fun toIsoOffsetString(date: LocalDate, hhmm: String): String {
		val t = runCatching { LocalTime.parse(hhmm) }.getOrNull() ?: LocalTime.of(9, 0)
		return ZonedDateTime.of(date, t, beijingZone).toOffsetDateTime().toString()
	}

	// 使用北京时间（Asia/Shanghai），与后端保持一致
	private val beijingZone = ZoneId.of("Asia/Shanghai")
	
	// 获取北京时间当前日期
	private fun beijingToday(): LocalDate {
		return LocalDate.now(beijingZone)
	}
	
	// 获取北京时间当前日期时间
	private fun beijingNow(): LocalDateTime {
		return LocalDateTime.now(beijingZone)
	}
	
	private fun remoteEventToEntity(ev: ChronoSyncApi.RemoteEvent): ScheduleEventEntity {
		// 将后端返回的时间转换为北京时间显示
		val start = OffsetDateTime.parse(ev.startTime).atZoneSameInstant(beijingZone)
		val end = ev.endTime?.let { OffsetDateTime.parse(it).atZoneSameInstant(beijingZone) }
		val date = start.toLocalDate().toString()
		val startHhmm = start.toLocalTime().format(hhmmFormatter)
		val endHhmm = (end?.toLocalTime() ?: start.toLocalTime().plusHours(1)).format(hhmmFormatter)
		val type = ev.type?.uppercase(Locale.US)?.takeIf { it == "WORK" || it == "LIFE" || it == "STUDY" } ?: "LIFE"
		val priority = ev.priority?.coerceIn(1, 3) ?: 2
		return ScheduleEventEntity(
			0,
			ev.id,
			date,
			ev.title,
			startHhmm,
			endHhmm,
			type,
			priority,
		)
	}

	private fun remoteMemoToEntity(m: ChronoSyncApi.RemoteMemo): MemoEntity {
		val (title, content) = decodeMemoPayload(m.content)
		val updatedAtMs = runCatching {
			m.updatedAt?.let { OffsetDateTime.parse(it).toInstant().toEpochMilli() }
		}.getOrNull() ?: System.currentTimeMillis()
		return MemoEntity(
			0,
			m.id,
			title,
			content,
			updatedAtMs,
		)
	}

	private suspend fun syncFromServerIfNeeded() {
		if (syncedFromServer) return
		forceSyncFromServer()
		syncedFromServer = true
	}
	
	private suspend fun forceSyncFromServer() {
		val token = getAccessToken() ?: run {
			android.util.Log.d("SyncDebug", "forceSyncFromServer: token is null")
			return
		}
		android.util.Log.d("SyncDebug", "forceSyncFromServer: starting sync")
		
		// 拉取一段时间范围内的日程与备忘录作为本地缓存
		val today = beijingToday()
		val start = today.minusDays(180).toString()
		val end = today.plusDays(365).toString()
		android.util.Log.d("SyncDebug", "Date range: $start to $end")
		
		val events = mutableListOf<ChronoSyncApi.RemoteEvent>()
		var page = 1
		while (true) {
			try {
				// 后端限制 size 最大为 100
				val pr = ChronoSyncApp.chronoSyncApi.listEvents(token, startDate = start, endDate = end, page = page, size = 100)
				android.util.Log.d("SyncDebug", "listEvents page $page: ${pr.items.size} items")
				events.addAll(pr.items)
				if (pr.items.size < pr.size) break
				page += 1
				if (page > 20) break  // 最多拉取 2000 条
			} catch (e: Exception) {
				android.util.Log.e("SyncDebug", "listEvents failed: ${e.message}")
				throw e
			}
		}
		android.util.Log.d("SyncDebug", "Total events from cloud: ${events.size}")

		val memos = mutableListOf<ChronoSyncApi.RemoteMemo>()
		page = 1
		while (true) {
			try {
				// 后端限制 size 最大为 100
				val pr = ChronoSyncApp.chronoSyncApi.listMemos(token, page = page, size = 100)
				memos.addAll(pr.items)
				if (pr.items.size < pr.size) break
				page += 1
				if (page > 20) break
			} catch (e: Exception) {
				android.util.Log.e("SyncDebug", "listMemos failed: ${e.message}")
				throw e
			}
		}
		android.util.Log.d("SyncDebug", "Total memos from cloud: ${memos.size}")

		val beforeEventCount = eventDao.count()
		val beforeMemoCount = memoDao.count()
		android.util.Log.d("SyncDebug", "Local events before sync: $beforeEventCount, memos: $beforeMemoCount")
		
		db.runInTransaction {
			eventDao.deleteAll()
			memoDao.deleteAll()
			events.forEach { eventDao.insert(remoteEventToEntity(it)) }
			memos.forEach { 
				val entity = remoteMemoToEntity(it)
				android.util.Log.d("SyncDebug", "Inserting memo: serverId=${entity.serverId}, title=${entity.title}")
				memoDao.insert(entity)
			}
		}
		
		val afterEventCount = eventDao.count()
		val afterMemoCount = memoDao.count()
		android.util.Log.d("SyncDebug", "Local events after sync: $afterEventCount, memos: $afterMemoCount")
		android.util.Log.d("SyncDebug", "forceSyncFromServer: sync completed")
	}

	private fun showAuthDialog(onAuthed: () -> Unit) {
		val layout = android.widget.LinearLayout(this).apply {
			orientation = android.widget.LinearLayout.VERTICAL
			setPadding(48, 24, 48, 0)
		}
		val usernameInput = EditText(this).apply {
			hint = "用户名"
		}
		val passwordInput = EditText(this).apply {
			hint = "密码"
			inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
		}
		layout.addView(usernameInput)
		layout.addView(passwordInput)

		AlertDialog.Builder(this)
			.setTitle("登录 ChronoSync")
			.setMessage("需要登录后才能调用后端接口（events/memos/agent）")
			.setView(layout)
			.setPositiveButton("登录") { _, _ ->
				val u = usernameInput.text?.toString()?.trim().orEmpty()
				val p = passwordInput.text?.toString()?.trim().orEmpty()
				if (u.isBlank() || p.isBlank()) {
					toast("请输入用户名和密码")
					return@setPositiveButton
				}
				lifecycleScope.launch {
			val result = withContext(Dispatchers.IO) {
				runCatching { ChronoSyncApp.chronoSyncApi.login(u, p) }
					}
					val ok = result.getOrNull()
					if (ok == null) {
						toast("登录失败：${formatChronoSyncError(result.exceptionOrNull()!!)}")
						return@launch
					}
					setAccessToken(ok.accessToken)
					syncedFromServer = false
					lifecycleScope.launch {
						withContext(Dispatchers.IO) { runCatching { syncFromServerIfNeeded() } }
						refreshMarkers()
						refreshMemos()
						selectedDate?.let { refreshEventsForDate(it) }
						onAuthed()
					}
				}
			}
			.setNeutralButton("注册并登录") { _, _ ->
				val u = usernameInput.text?.toString()?.trim().orEmpty()
				val p = passwordInput.text?.toString()?.trim().orEmpty()
				if (u.isBlank() || p.isBlank()) {
					toast("请输入用户名和密码")
					return@setNeutralButton
				}
				lifecycleScope.launch {
				val result = withContext(Dispatchers.IO) {
					runCatching {
						ChronoSyncApp.chronoSyncApi.register(u, p)
						ChronoSyncApp.chronoSyncApi.login(u, p)
					}
				}
					val token = result.getOrNull()
					if (token == null) {
						toast("注册/登录失败：${formatChronoSyncError(result.exceptionOrNull()!!)}")
						return@launch
					}
					setAccessToken(token.accessToken)
					syncedFromServer = false
					withContext(Dispatchers.IO) { runCatching { syncFromServerIfNeeded() } }
					refreshMarkers()
					refreshMemos()
					selectedDate?.let { refreshEventsForDate(it) }
					onAuthed()
				}
			}
			.setNegativeButton("取消", null)
			.show()
	}

	private val requestLocationPermissions = registerForActivityResult(
		ActivityResultContracts.RequestMultiplePermissions()
	) { result ->
		val granted = (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
			(result[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
		if (granted) {
			startLocationSmartAddFlow()
		} else {
			toast("需要定位权限才能获取当前地点")
		}
	}

	private val takeMemoPhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
		val uri = pendingMemoPhotoUri
		pendingMemoPhotoUri = null
		if (!ok || uri == null) return@registerForActivityResult
		startVisionExtractMemoFlow(uri)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContentView(R.layout.activity_main)

		val root = findViewById<View>(R.id.main)
		val appBar = findViewById<View>(R.id.appBarLayout)
		val content = findViewById<View>(R.id.contentLayout)
		val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

		// 沉浸式适配：状态栏/导航栏/键盘 insets
		ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
			val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
			appBar.updatePadding(top = sys.top)
			bottomNav.updatePadding(bottom = sys.bottom)
			val bottomPad = maxOf(sys.bottom + bottomNav.height, ime.bottom)
			content.updatePadding(left = sys.left, right = sys.right, bottom = bottomPad)
			insets
		}
		bottomNav.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
			ViewCompat.requestApplyInsets(root)
		}
		ViewCompat.requestApplyInsets(root)

		monthTitleText = findViewById(R.id.monthTitleText)
		selectedDateText = findViewById(R.id.selectedDateText)
		calendarView = findViewById(R.id.calendarView)
		eventsRecyclerView = findViewById(R.id.eventsRecyclerView)
		addEventFab = findViewById(R.id.addEventFab)
		locationFab = findViewById(R.id.locationFab)
		calendarTab = findViewById(R.id.calendarTab)
		assistantTab = findViewById(R.id.assistantTab)
		memoTab = findViewById(R.id.memoTab)
		lynxTab = findViewById(R.id.lynxTab)
		memoRecyclerView = findViewById(R.id.memoRecyclerView)
		memoEmptyText = findViewById(R.id.memoEmptyText)
		addMemoButton = findViewById(R.id.addMemoButton)
		memoScanButton = findViewById(R.id.memoScanButton)
		
		// 登录状态按钮
		loginStatusImage = findViewById(R.id.loginStatusImage)
		loginStatusImage.setOnClickListener {
			if (isLoggedIn()) {
				// 已登录，显示退出确认
				AlertDialog.Builder(this)
					.setTitle("退出登录")
					.setMessage("确定要退出当前账号吗？")
					.setPositiveButton("退出") { _, _ ->
						setAccessToken(null)
						updateLoginStatusUI()
						toast("已退出登录")
						lifecycleScope.launch {
							refreshMemos()
							selectedDate?.let { refreshEventsForDate(it) }
						}
					}
					.setNegativeButton("取消", null)
					.show()
			} else {
				// 未登录，显示登录对话框
				showAuthDialog {
					updateLoginStatusUI()
					toast("登录成功")
				}
			}
		}
		updateLoginStatusUI()

		// 初始化 LynxView 并渲染 learnDemo.lynx.bundle
		val lynxContainer = findViewById<android.widget.FrameLayout>(R.id.lynxContainer)
		val viewBuilder = LynxViewBuilder().apply {
			setTemplateProvider(DemoTemplateProvider(this@MainActivity))
		}
		lynxView = viewBuilder.build(this).also { view ->
			lynxContainer.addView(
				view,
				android.view.ViewGroup.LayoutParams(
					android.view.ViewGroup.LayoutParams.MATCH_PARENT,
					android.view.ViewGroup.LayoutParams.MATCH_PARENT,
				),
			)
			
			// 添加加载监听，加载成功后再次注入数据，确保首屏不为空
			view.addLynxViewClient(object : com.lynx.tasm.LynxViewClient() {
				override fun onLoadSuccess() {
					super.onLoadSuccess()
					android.util.Log.d("SyncDebug", "LynxView onLoadSuccess, refreshing data...")
					// 确保主线程执行
					runOnUiThread {
						val date = selectedDate ?: beijingToday()
						refreshEventsForDate(date)
						
						// 延迟 500ms 再次刷新，确保 React 前端已加载完成并注册了监听器
						view.postDelayed({
							android.util.Log.d("SyncDebug", "LynxView delayed refresh for React hydration...")
							refreshEventsForDate(date)
						}, 500)
					}
				}
			})

			// assets 中的 learnDemo.lynx.bundle
			// 传入空 JSON 对象 "{}" 而不是空字符串，避免部分版本兼容问题
			// 这里先传入空数据，数据会在 onLoadSuccess 回调中通过 updateLynxView 更新
			// 注意：前端 App.tsx 需要能够处理 props.tasks 变化并重新渲染，
			// 或者我们需要通过 renderTemplateUrl 重新传入数据来强制刷新
			view.renderTemplateUrl("learnDemo.lynx.bundle", "{}")
		}

		// Agent V2 - 问答助手初始化
		setupAssistantTab()

		db = AppDatabase.get(this)
		eventDao = db.scheduleEventDao()
		memoDao = db.memoDao()

		findViewById<View>(R.id.prevMonthImage).setOnClickListener {
			calendarView.findFirstVisibleMonth()?.let {
				calendarView.smoothScrollToMonth(it.yearMonth.previousMonth)
			}
		}
		findViewById<View>(R.id.nextMonthImage).setOnClickListener {
			calendarView.findFirstVisibleMonth()?.let {
				calendarView.smoothScrollToMonth(it.yearMonth.nextMonth)
			}
		}

		eventsRecyclerView.layoutManager = LinearLayoutManager(this)
		eventsRecyclerView.adapter = eventsAdapter
		eventsAdapter.onItemClick = { event ->
			selectedDate?.let { date ->
				showEventDialog(date, existing = event)
			}
		}

		addEventFab.setOnClickListener {
			val date = selectedDate ?: beijingToday().also { onDateSelected(it) }
			showEventDialog(date, existing = null)
		}

		locationFab.setOnClickListener {
			ensureLocationPermissionThenStart()
		}

		memoRecyclerView.layoutManager = LinearLayoutManager(this)
		memoRecyclerView.adapter = memoAdapter
		memoAdapter.onItemClick = { memo ->
			showMemoDialog(memo)
		}

		addMemoButton.setOnClickListener {
			showMemoDialog(existing = null)
		}
		memoScanButton.setOnClickListener {
			startMemoPhotoCapture()
		}

		bottomNav.setOnItemSelectedListener { item ->
			when (item.itemId) {
				R.id.tab_calendar -> {
					selectTab(0)
					true
				}
				R.id.tab_assistant -> {
					selectTab(1)
					true
				}
				R.id.tab_memo -> {
					selectTab(2)
					true
				}
				R.id.tab_lynx -> {
					selectTab(3)
					true
				}
				else -> false
			}
		}
		
		// 重新选中同一个 Tab 时也刷新（点击当前选中的 Tab）
		bottomNav.setOnItemReselectedListener { item ->
			when (item.itemId) {
				R.id.tab_calendar -> {
					android.util.Log.d("SyncDebug", "Tab reselected: calendar")
					refreshCalendarTab()
				}
				R.id.tab_assistant -> {
					android.util.Log.d("SyncDebug", "Tab reselected: assistant")
					// 助手页面不需要刷新
				}
				R.id.tab_memo -> {
					android.util.Log.d("SyncDebug", "Tab reselected: memo")
					lifecycleScope.launch {
						val token = getAccessToken()
						if (!token.isNullOrBlank()) {
							withContext(Dispatchers.IO) {
								runCatching { forceSyncFromServer() }
							}
						}
						refreshMemos()
					}
				}
				R.id.tab_lynx -> {
					// Lynx 页面不需要刷新
				}
			}
		}
		
		bottomNav.selectedItemId = R.id.tab_calendar

		setupCalendar()

		lifecycleScope.launch {
			val token = getAccessToken()
			android.util.Log.d("SyncDebug", "onCreate: token=${token?.take(10)}...")
			if (!token.isNullOrBlank()) {
				withContext(Dispatchers.IO) {
					runCatching { forceSyncFromServer() }
				}
			}
			refreshMarkers()
			refreshMemos()
			onDateSelected(beijingToday())
			android.util.Log.d("SyncDebug", "onCreate: initialization completed")
		}
	}

	private fun refreshCalendarTab() {
		android.util.Log.d("SyncDebug", "refreshCalendarTab: refreshing...")
		lifecycleScope.launch {
			val token = getAccessToken()
			if (!token.isNullOrBlank()) {
				withContext(Dispatchers.IO) {
					runCatching { forceSyncFromServer() }
				}
			}
			refreshMarkers()
			selectedDate?.let { refreshEventsForDate(it) }
			toast("日程已刷新")
			android.util.Log.d("SyncDebug", "refreshCalendarTab: completed")
		}
	}

	private fun selectTab(index: Int) {
		calendarTab.visibility = if (index == 0) View.VISIBLE else View.GONE
		assistantTab.visibility = if (index == 1) View.VISIBLE else View.GONE
		memoTab.visibility = if (index == 2) View.VISIBLE else View.GONE
		lynxTab.visibility = if (index == 3) View.VISIBLE else View.GONE
		
		// 切换回日历Tab时自动刷新日程（强制从云端同步）
		if (index == 0) {
			android.util.Log.d("SyncDebug", "selectTab: switching to calendar, starting sync")
			lifecycleScope.launch {
				val token = getAccessToken()
				android.util.Log.d("SyncDebug", "selectTab: token=${token?.take(10)}...")
				if (!token.isNullOrBlank()) {
					withContext(Dispatchers.IO) { 
						runCatching { 
							forceSyncFromServer() 
						}.onFailure { e ->
							val apiEx = e as? ChronoSyncApi.ApiException
							if (apiEx != null) {
								android.util.Log.e("SyncDebug", "forceSyncFromServer failed: HTTP ${apiEx.code}, body=${apiEx.rawBody}")
							} else {
								android.util.Log.e("SyncDebug", "forceSyncFromServer failed: ${e.message}")
							}
						}
					}
				} else {
					android.util.Log.d("SyncDebug", "selectTab: no token, skipping sync")
				}
				refreshMarkers()
				selectedDate?.let { refreshEventsForDate(it) }
				android.util.Log.d("SyncDebug", "selectTab: refresh completed")
			}
		}
		
		// 切换到备忘录Tab时自动从云端同步并刷新
		if (index == 2) {
			android.util.Log.d("SyncDebug", "selectTab: switching to memo, starting sync")
			lifecycleScope.launch {
				val token = getAccessToken()
				android.util.Log.d("SyncDebug", "selectTab: memo token=${token?.take(10)}...")
				if (!token.isNullOrBlank()) {
					withContext(Dispatchers.IO) {
						runCatching { 
							forceSyncFromServer() 
						}.onFailure { e ->
							val apiEx = e as? ChronoSyncApi.ApiException
							if (apiEx != null) {
								android.util.Log.e("SyncDebug", "forceSyncFromServer (memo) failed: HTTP ${apiEx.code}, body=${apiEx.rawBody}")
							} else {
								android.util.Log.e("SyncDebug", "forceSyncFromServer (memo) failed: ${e.message}")
							}
						}
					}
				} else {
					android.util.Log.d("SyncDebug", "selectTab: memo no token, skipping sync")
				}
				refreshMemos()
				android.util.Log.d("SyncDebug", "selectTab: memo refresh completed")
			}
		}
		addEventFab.visibility = if (index == 0) View.VISIBLE else View.GONE
		locationFab.visibility = if (index == 0) View.VISIBLE else View.GONE
	}

	override fun onDestroy() {
		super.onDestroy()
		lynxView?.destroy()
	}

	private fun toast(msg: String) {
		Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
	}

	private fun ensureLocationPermissionThenStart() {
		val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
		val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
		val granted = (fine == PackageManager.PERMISSION_GRANTED) || (coarse == PackageManager.PERMISSION_GRANTED)
		if (granted) {
			startLocationSmartAddFlow()
			return
		}
		requestLocationPermissions.launch(
			arrayOf(
				Manifest.permission.ACCESS_FINE_LOCATION,
				Manifest.permission.ACCESS_COARSE_LOCATION,
			)
		)
	}

	private fun startLocationSmartAddFlow() {
		val loading = AlertDialog.Builder(this)
			.setTitle("定位中")
			.setMessage("正在获取当前地点…")
			.setCancelable(false)
			.create()
		loading.show()

		lifecycleScope.launch {
			val location = withContext(Dispatchers.IO) { getCurrentLocationOrNull() }
			loading.dismiss()
			if (location == null) {
				toast("定位失败：请检查定位开关/权限")
				return@launch
			}

			val candidates = withContext(Dispatchers.IO) { geocodeCandidates(location) }
			val fallback = "${"%.6f".format(location.latitude)}, ${"%.6f".format(location.longitude)}"
			val options = (candidates + listOf("使用坐标：$fallback")).distinct()

			AlertDialog.Builder(this@MainActivity)
				.setTitle("选择当前地点")
				.setItems(options.toTypedArray()) { _, which ->
					val placeText = options[which]
					startLlmSuggestAndConfirm(placeText)
				}
				.setNegativeButton("取消", null)
				.show()
		}
	}

	@SuppressLint("MissingPermission")
	private suspend fun getCurrentLocationOrNull(): Location? {
		val lm = getSystemService(LOCATION_SERVICE) as LocationManager
		val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
			.filter { p ->
				runCatching { lm.isProviderEnabled(p) }.getOrDefault(false)
			}

		fun bestOf(a: Location?, b: Location?): Location? {
			if (a == null) return b
			if (b == null) return a
			// 更优先时间新，其次精度更高（accuracy 更小）
			return when {
				b.time > a.time -> b
				a.accuracy == 0f -> b
				b.accuracy == 0f -> a
				b.accuracy < a.accuracy -> b
				else -> a
			}
		}

		var best: Location? = null
		for (p in providers) {
			best = bestOf(best, runCatching { lm.getLastKnownLocation(p) }.getOrNull())
		}
		if (best != null) return best
		val provider = providers.firstOrNull() ?: return null

		return withTimeoutOrNull(8000) {
			kotlinx.coroutines.suspendCancellableCoroutine<Location?> { cont ->
				val listener = object : LocationListener {
					override fun onLocationChanged(location: Location) {
						if (cont.isActive) cont.resume(location)
						lm.removeUpdates(this)
					}
					@Deprecated("Deprecated in Java")
					override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
					override fun onProviderEnabled(provider: String) = Unit
					override fun onProviderDisabled(provider: String) = Unit
				}
				cont.invokeOnCancellation { lm.removeUpdates(listener) }
				lm.requestSingleUpdate(provider, listener, mainLooper)
			}
		}
	}

	private fun geocodeCandidates(location: Location): List<String> {
		if (!Geocoder.isPresent()) return emptyList()
		val geocoder = Geocoder(this, Locale.getDefault())
		val list = runCatching { geocoder.getFromLocation(location.latitude, location.longitude, 5) }
			.getOrNull()
			.orEmpty()
		return list
			.mapNotNull { addr: Address ->
				addr.getAddressLine(0)?.takeIf { it.isNotBlank() }
			}
			.distinct()
	}

	private data class MemoSuggestion(
		val title: String,
		val content: String,
		val confidence: Double,
	)

	private fun startMemoPhotoCapture() {
		val uri = createTempMemoPhotoUri() ?: run {
			toast("无法创建相机文件")
			return
		}
		pendingMemoPhotoUri = uri
		takeMemoPhoto.launch(uri)
	}

	private fun createTempMemoPhotoUri(): Uri? {
		val dir = File(cacheDir, "memo_photos").apply { mkdirs() }
		val file = File.createTempFile("memo_", ".jpg", dir)
		return FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
	}

	private fun startVisionExtractMemoFlow(uri: Uri) {
		val apiKey = BuildConfig.KIMI_API_KEY
		if (apiKey.isBlank()) {
			toast("未配置 Kimi API Key（请在 .env 或环境变量中配置）")
			return
		}
		showMemoVisionRequirementDialog(uri)
	}

	private fun showMemoVisionRequirementDialog(uri: Uri) {
		val input = EditText(this).apply {
			hint = "可选：例如‘只提取金额和日期’/‘整理成待办清单’"
		}
		AlertDialog.Builder(this)
			.setTitle("识别偏好（可选）")
			.setMessage("你希望这张照片里的信息按什么要求提取/整理？")
			.setView(input)
			.setPositiveButton("开始识别") { _, _ ->
				val requirement = input.text?.toString()?.trim().orEmpty()
				doVisionExtractMemo(uri, requirement.takeIf { it.isNotBlank() })
			}
			.setNegativeButton("取消", null)
			.show()
	}

	private fun doVisionExtractMemo(uri: Uri, userRequirement: String?) {
		val loading = AlertDialog.Builder(this)
			.setTitle("图片识别")
			.setMessage("正在提取图片中的文字和信息…")
			.setCancelable(false)
			.create()
		loading.show()

			lifecycleScope.launch {
				val suggestion = withContext(Dispatchers.IO) {
					val dataUrl = encodeImageAsJpegDataUrl(uri, maxSize = 1280)
					if (dataUrl == null) return@withContext null
					kimiVisionExtractMemo(dataUrl, userRequirement)
				}
			loading.dismiss()
			if (suggestion == null) {
				toast("识别失败：请重试")
				return@launch
			}
			showMemoPrefillDialog(suggestion)
		}
	}

	private fun showMemoPrefillDialog(suggestion: MemoSuggestion) {
		val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_memo_edit, null)
		val titleInput = dialogView.findViewById<EditText>(R.id.memoTitleInput)
		val contentInput = dialogView.findViewById<EditText>(R.id.memoContentInput)
		titleInput.setText(suggestion.title)
		contentInput.setText(suggestion.content)

		AlertDialog.Builder(this)
			.setTitle("识别结果（可编辑）")
			.setMessage("置信度：${"%.0f%%".format(suggestion.confidence.coerceIn(0.0, 1.0) * 100)}")
			.setView(dialogView)
			.setPositiveButton("确认加入") { _, _ ->
				val title = titleInput.text.toString().trim()
				val content = contentInput.text.toString().trim()
				if (title.isEmpty() && content.isEmpty()) return@setPositiveButton
				lifecycleScope.launch {
					val now = System.currentTimeMillis()
					try {
						withContext(Dispatchers.IO) {
							val finalTitle = title.ifEmpty { "未命名" }
							val token = getAccessToken()
							if (!token.isNullOrBlank()) {
								val payload = encodeMemoPayload(finalTitle, content)
								val remoteId = ChronoSyncApp.chronoSyncApi.createMemo(token, payload).id
								memoDao.insert(MemoEntity(0, remoteId, finalTitle, content, now))
							} else {
								memoDao.insert(MemoEntity(finalTitle, content, now))
							}
						}
						refreshMemos()
						toast("已加入备忘录")
					} catch (e: Exception) {
						handleApiError(e)
					}
				}
			}
			.setNegativeButton("取消", null)
			.show()
	}

	private fun encodeImageAsJpegDataUrl(uri: Uri, maxSize: Int): String? {
		fun decodeSampled(): Bitmap? {
			val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
			contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
			val (w, h) = opts.outWidth to opts.outHeight
			if (w <= 0 || h <= 0) return null
			var sample = 1
			while (w / sample > maxSize || h / sample > maxSize) sample *= 2
			val opts2 = BitmapFactory.Options().apply { inSampleSize = sample }
			return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts2) }
		}

		val bmp = decodeSampled() ?: return null
		val bos = ByteArrayOutputStream()
		bmp.compress(Bitmap.CompressFormat.JPEG, 85, bos)
		val bytes = bos.toByteArray()
		val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
		return "data:image/jpeg;base64,$b64"
	}

	private fun kimiVisionExtractMemo(imageDataUrl: String, userRequirement: String?): MemoSuggestion? {
		val apiKey = BuildConfig.KIMI_API_KEY
		if (apiKey.isBlank()) return null
		val baseUrl = BuildConfig.KIMI_BASE_URL.ifBlank { "https://api.moonshot.cn" }
		val model = BuildConfig.KIMI_VISION_MODEL.ifBlank { "moonshot-v1-8k-vision-preview" }

		val system = "你是一个备忘录助手。你将从用户拍摄的照片中提取文字并整理成可保存的备忘录。"
		val requirementBlock = userRequirement?.let { "\n用户额外要求（最高优先级）：$it\n" } ?: ""
		val prompt = "请从图片中提取所有可识别的文字与关键信息，并整理输出为 JSON。" + requirementBlock + "\n" +
			"仅输出 JSON，格式：{\"title\":string,\"content\":string,\"confidence\":0~1}\n" +
			"要求：title 简短准确（中文）；content 以条目/段落形式整理（可包含原文+总结）；若是票据/快递单/屏幕内容请提炼关键字段。"

		val userContent = JSONArray()
			.put(JSONObject().put("type", "text").put("text", prompt))
			.put(
				JSONObject()
					.put("type", "image_url")
					.put("image_url", JSONObject().put("url", imageDataUrl))
			)

		val bodyJson = JSONObject().apply {
			put("model", model)
			put("temperature", 0.2)
			put("response_format", JSONObject().put("type", "json_object"))
			put(
				"messages",
				JSONArray()
					.put(JSONObject().put("role", "system").put("content", system))
					.put(JSONObject().put("role", "user").put("content", userContent)),
			)
		}

		val mediaType = "application/json; charset=utf-8".toMediaType()
		val req = Request.Builder()
			.url("$baseUrl/v1/chat/completions")
			.header("Content-Type", "application/json")
			.header("Authorization", "Bearer $apiKey")
			.post(bodyJson.toString().toRequestBody(mediaType))
			.build()

		val respBody = ChronoSyncApp.httpClient.newCall(req).execute().use { resp ->
			if (!resp.isSuccessful) return null
			resp.body?.string()
		} ?: return null

		val content = runCatching {
			val root = JSONObject(respBody)
			root.getJSONArray("choices")
				.getJSONObject(0)
				.getJSONObject("message")
				.getString("content")
		}.getOrNull() ?: return null

		val obj = runCatching { JSONObject(content) }.getOrNull() ?: return null
		val title = obj.optString("title").trim()
		val memoContent = obj.optString("content").trim()
		if (title.isBlank() && memoContent.isBlank()) return null
		return MemoSuggestion(
			title = title.ifBlank { "未命名" },
			content = memoContent,
			confidence = obj.optDouble("confidence", 0.5),
		)
	}

	private data class LlmSuggestion(
		val title: String,
		val type: String,
		val priority: Int,
		val startTime: String,
		val endTime: String,
		val confidence: Double,
	)

	private fun startLlmSuggestAndConfirm(placeText: String) {
		val apiKey = BuildConfig.KIMI_API_KEY
		if (apiKey.isBlank()) {
			toast("未配置 Kimi API Key（请在 .env 或环境变量中配置）")
			return
		}

		val loading = AlertDialog.Builder(this)
			.setTitle("智能生成")
			.setMessage("正在根据地点+时间推测你在做什么…")
			.setCancelable(false)
			.create()
		loading.show()

		lifecycleScope.launch {
			val now = beijingNow()
			val suggestion = withContext(Dispatchers.IO) {
				kimiSuggestNowActivity(placeText, now)
			}
			loading.dismiss()
			if (suggestion == null) {
				toast("生成失败：请稍后重试")
				return@launch
			}

			val today = LocalDate.now()
			showGeneratedEventEditDialog(today, placeText, suggestion)
		}
	}

	private fun showGeneratedEventEditDialog(date: LocalDate, placeText: String, suggestion: LlmSuggestion) {
		val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_event_edit, null)
		val typeSpinner = dialogView.findViewById<Spinner>(R.id.eventTypeSpinner)
		val priorityGroup = dialogView.findViewById<RadioGroup>(R.id.eventPriorityGroup)
		val titleInput = dialogView.findViewById<EditText>(R.id.eventTitleInput)
		val startInput = dialogView.findViewById<EditText>(R.id.eventStartTimeInput)
		val endInput = dialogView.findViewById<EditText>(R.id.eventEndTimeInput)

		val typeLabels = resources.getStringArray(R.array.event_type_labels)
		val typeValues = resources.getStringArray(R.array.event_type_values)
		typeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, typeLabels)
		val suggestedType = suggestion.type.ifBlank { "LIFE" }
		val typeIndex = typeValues.indexOf(suggestedType).let { if (it >= 0) it else 0 }
		typeSpinner.setSelection(typeIndex)

		when (suggestion.priority) {
			1 -> priorityGroup.check(R.id.priorityLow)
			3 -> priorityGroup.check(R.id.priorityHigh)
			else -> priorityGroup.check(R.id.priorityMedium)
		}

		val initTitle = if (suggestion.title.contains(placeText)) suggestion.title else "${suggestion.title}（$placeText）"
		titleInput.setText(initTitle)
		startInput.setText(suggestion.startTime)
		endInput.setText(suggestion.endTime)

		fun showTimePicker(current: String?, onPicked: (String) -> Unit) {
			val (h0, m0) = try {
				current?.split(":")?.let { it[0].toInt() to it[1].toInt() } ?: (9 to 0)
			} catch (_: Exception) {
				9 to 0
			}
			TimePickerDialog(this, { _, hourOfDay, minute ->
				onPicked(String.format(Locale.US, "%02d:%02d", hourOfDay, minute))
			}, h0, m0, true).show()
		}
		startInput.setOnClickListener { showTimePicker(startInput.text?.toString()) { startInput.setText(it) } }
		endInput.setOnClickListener { showTimePicker(endInput.text?.toString()) { endInput.setText(it) } }

		val titleText = "生成日程（可编辑）"
		val subtitle = "地点：$placeText  置信度：${"%.0f%%".format(suggestion.confidence * 100)}"

		AlertDialog.Builder(this)
			.setTitle(titleText)
			.setMessage(subtitle)
			.setView(dialogView)
			.setPositiveButton("保存并生成") { _, _ ->
				val title = titleInput.text.toString().trim()
				val start = startInput.text.toString().trim()
				val end = endInput.text.toString().trim()
				if (title.isEmpty()) return@setPositiveButton
				if (start.isEmpty() || end.isEmpty()) return@setPositiveButton
				if (start > end) return@setPositiveButton
				val type = typeValues[typeSpinner.selectedItemPosition]
				val priority = when (priorityGroup.checkedRadioButtonId) {
					R.id.priorityLow -> 1
					R.id.priorityHigh -> 3
					else -> 2
				}
				lifecycleScope.launch {
					try {
						withContext(Dispatchers.IO) {
							val token = getAccessToken()
							if (!token.isNullOrBlank()) {
								val startIso = toIsoOffsetString(date, start)
								val endIso = toIsoOffsetString(date, end)
								val remote = ChronoSyncApp.chronoSyncApi.createEvent(
									token = token,
									title = title,
									startTime = startIso,
									endTime = endIso,
									type = type,
									priority = priority,
								)
								eventDao.insert(
									ScheduleEventEntity(
										0,
										remote.id,
										date.toString(),
										title,
										start,
										end,
										type,
										priority,
									)
								)
							} else {
								val entity = ScheduleEventEntity(
									0,
									null,
									date.toString(),
									title,
									start,
									end,
									type,
									priority,
								)
								eventDao.insert(entity)
							}
						}
						refreshMarkers()
						onDateSelected(date)
						toast("已生成 1 条日程")
					} catch (e: Exception) {
						handleApiError(e)
					}
				}
			}
			.setNegativeButton("取消", null)
			.show()
	}

	private fun clampTimeRange(s: LlmSuggestion, now: LocalDateTime): LlmSuggestion {
		fun isValidHhmm(x: String): Boolean = Regex("\\d{2}:\\d{2}").matches(x)
		val start = if (isValidHhmm(s.startTime)) s.startTime else now.toLocalTime().withSecond(0).withNano(0).let { String.format(Locale.US, "%02d:%02d", it.hour, it.minute) }
		val endFallback = now.plusHours(1).toLocalTime().withSecond(0).withNano(0).let { String.format(Locale.US, "%02d:%02d", it.hour, it.minute) }
		val end = if (isValidHhmm(s.endTime)) s.endTime else endFallback
		val fixedEnd = if (start <= end) end else endFallback
		val fixedType = s.type.takeIf { it == "WORK" || it == "LIFE" || it == "STUDY" } ?: "LIFE"
		val fixedPriority = s.priority.coerceIn(1, 3)
		val fixedConfidence = s.confidence.coerceIn(0.0, 1.0)
		return s.copy(startTime = start, endTime = fixedEnd, type = fixedType, priority = fixedPriority, confidence = fixedConfidence)
	}

	private fun kimiSuggestNowActivity(placeText: String, now: LocalDateTime): LlmSuggestion? {
		val apiKey = BuildConfig.KIMI_API_KEY
		if (apiKey.isBlank()) return null
		val baseUrl = BuildConfig.KIMI_BASE_URL.ifBlank { "https://api.moonshot.cn" }

		val nowStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
		val system = "你是一个日程助手。你要基于用户当前地点与时间，推测用户此刻可能在做什么，并给出一条可直接写入日程的建议。"
		val user = "当前时间：$nowStr\n当前地点：$placeText\n" +
			"请只输出 JSON，格式如下：" +
			"{\"title\":string,\"type\":\"WORK\"|\"LIFE\"|\"STUDY\",\"priority\":1|2|3,\"startTime\":\"HH:mm\",\"endTime\":\"HH:mm\",\"confidence\":0~1}" +
			"\n要求：title 用中文、尽量具体；startTime/endTime 以现在为基准，默认 60 分钟；confidence 给出你对推测的把握。"

		val bodyJson = JSONObject().apply {
			put("model", BuildConfig.KIMI_MODEL.ifBlank { "kimi-k2-turbo-preview" })
			put("temperature", 0.2)
			put("response_format", JSONObject().put("type", "json_object"))
			put(
				"messages",
				JSONArray()
					.put(JSONObject().put("role", "system").put("content", system))
					.put(JSONObject().put("role", "user").put("content", user)),
			)
		}

		val mediaType = "application/json; charset=utf-8".toMediaType()
		val req = Request.Builder()
			.url("$baseUrl/v1/chat/completions")
			.header("Content-Type", "application/json")
			.header("Authorization", "Bearer $apiKey")
			.post(bodyJson.toString().toRequestBody(mediaType))
			.build()

		val respBody = ChronoSyncApp.httpClient.newCall(req).execute().use { resp ->
			if (!resp.isSuccessful) return null
			resp.body?.string()
		} ?: return null

		val content = runCatching {
			val root = JSONObject(respBody)
			root.getJSONArray("choices")
				.getJSONObject(0)
				.getJSONObject("message")
				.getString("content")
		}.getOrNull() ?: return null

		val suggestion = runCatching {
			val obj = JSONObject(content)
			LlmSuggestion(
				title = obj.optString("title").trim(),
				type = obj.optString("type").trim().uppercase(Locale.US),
				priority = obj.optInt("priority", 2),
				startTime = obj.optString("startTime").trim(),
				endTime = obj.optString("endTime").trim(),
				confidence = obj.optDouble("confidence", 0.5),
			)
		}.getOrNull() ?: return null

		if (suggestion.title.isBlank()) return null
		return clampTimeRange(suggestion, now)
	}

	// ========== Agent V2 - 问答助手功能 ==========

	private fun setupAssistantTab() {
		val assistantInput = findViewById<EditText>(R.id.assistantInput)
		val assistantSendButton = findViewById<View>(R.id.assistantSendButton)
		
		// 对话历史 RecyclerView
		conversationRecyclerView = findViewById(R.id.conversationRecyclerView)
		conversationRecyclerView.layoutManager = LinearLayoutManager(this)
		conversationRecyclerView.adapter = conversationAdapter
		
		// 清空对话按钮
		clearConversationButton = findViewById(R.id.clearConversationButton)
		clearConversationButton.setOnClickListener {
			showClearConversationConfirm()
		}
		
		// 建议确认 UI
		suggestionLayout = findViewById(R.id.suggestionLayout)
		suggestionText = findViewById(R.id.suggestionText)
		acceptSuggestionButton = findViewById(R.id.acceptSuggestionButton)
		rejectSuggestionButton = findViewById(R.id.rejectSuggestionButton)
		
		acceptSuggestionButton.setOnClickListener {
			sendConfirmation("可以")
		}
		
		rejectSuggestionButton.setOnClickListener {
			hideSuggestionLayout()
			assistantInput.setText("")
		}
		
		// 发送按钮点击
		assistantSendButton.setOnClickListener {
			val text = assistantInput.text?.toString()?.trim().orEmpty()
			if (text.isBlank()) return@setOnClickListener
			
			val token = getAccessToken()
			if (token.isNullOrBlank()) {
				showAuthDialog {
					assistantSendButton.performClick()
				}
				return@setOnClickListener
			}
			
			sendMessage(token, text)
		}
		
		// 页面加载时加载对话历史
		lifecycleScope.launch {
			val token = getAccessToken()
			if (!token.isNullOrBlank()) {
				loadConversationHistory(token)
			}
		}
	}
	
	private fun sendMessage(token: String, text: String, isConfirm: Boolean = false) {
		// 如果正在等待建议确认，且用户没有点击按钮，则隐藏建议布局
		if (isWaitingForSuggestionConfirm && !isConfirm) {
			hideSuggestionLayout()
		}
		
		// 添加到对话列表（用户消息）
		if (!isConfirm) {
			conversationAdapter.addMessage(ConversationMessage.User(text))
			scrollToBottom()
		}
		
		val assistantInput = findViewById<EditText>(R.id.assistantInput)
		assistantInput.setText("")
		assistantInput.isEnabled = false
		
		lifecycleScope.launch {
			val result = withContext(Dispatchers.IO) {
				runCatching { ChronoSyncApp.chronoSyncApi.processAgent(token, text) }
			}
			
			assistantInput.isEnabled = true
			
			val resp = result.getOrNull()
			if (resp != null) {
				val reply = resp.reply.ifBlank { "（空回复）" }
				conversationAdapter.addMessage(ConversationMessage.Assistant(reply))
				scrollToBottom()
				
				// 如果AI创建/更新/删除了日程，提示用户（切换回日历时自动刷新）
				if (resp.entity == "event" && 
				    (resp.action == "create" || resp.action == "update" || resp.action == "delete")) {
					toast("日程已更新，请查看日历")
				}
				
				// 检测是否需要建议确认（只检测特定关键词组合）
				if (needsSuggestionConfirmation(reply)) {
					showSuggestionLayout(reply)
				}
			} else {
				val exception = result.exceptionOrNull()!!
				val apiEx = exception as? ChronoSyncApi.ApiException
				
				if (apiEx != null && apiEx.code == 401) {
					// 登录过期，显示错误消息并弹出登录框
					conversationAdapter.addMessage(ConversationMessage.Assistant("登录已过期，请重新登录"))
					scrollToBottom()
					handleApiError(exception)
				} else {
					val error = "请求失败：${formatChronoSyncError(exception)}"
					conversationAdapter.addMessage(ConversationMessage.Assistant(error))
					scrollToBottom()
				}
			}
		}
	}
	
	private fun sendConfirmation(confirmText: String) {
		val token = getAccessToken() ?: return
		hideSuggestionLayout()
		sendMessage(token, confirmText, isConfirm = true)
	}
	
	private fun needsSuggestionConfirmation(reply: String): Boolean {
		// 检测是否包含建议确认关键词
		// 后端返回的建议确认格式：包含"建议改到"和"是否使用建议时间"
		return reply.contains("建议改到") && reply.contains("是否使用建议时间")
	}
	
	private fun showSuggestionLayout(reply: String) {
		suggestionText.text = "💡 时间冲突！Agent 提供了建议时段"
		suggestionLayout.visibility = View.VISIBLE
		isWaitingForSuggestionConfirm = true
	}
	
	private fun hideSuggestionLayout() {
		suggestionLayout.visibility = View.GONE
		isWaitingForSuggestionConfirm = false
	}
	
	private fun showClearConversationConfirm() {
		AlertDialog.Builder(this)
			.setTitle("清空对话")
			.setMessage("确定要清空所有对话记录吗？此操作不可恢复。")
			.setPositiveButton("清空") { _, _ ->
				clearConversationHistory()
			}
			.setNegativeButton("取消", null)
			.show()
	}
	
	private fun clearConversationHistory() {
		val token = getAccessToken() ?: return
		lifecycleScope.launch {
			val result = withContext(Dispatchers.IO) {
				runCatching { ChronoSyncApp.chronoSyncApi.clearAgentConversations(token) }
			}
			
			if (result.isSuccess) {
				conversationAdapter.clear()
				val resp = result.getOrNull()
				toast("已清空 ${resp?.deletedCount ?: 0} 条对话")
			} else {
				handleApiError(result.exceptionOrNull()!!)
			}
		}
	}
	
	private suspend fun loadConversationHistory(token: String) {
		val result = withContext(Dispatchers.IO) {
			runCatching { 
				ChronoSyncApp.chronoSyncApi.getAgentConversations(token, limit = 50) 
			}
		}
		
		if (result.isSuccess) {
			result.getOrNull()?.let { list ->
				// 按时间正序显示（旧的在上，新的在下）
				val messages = list.items.reversed().map { conv ->
					if (conv.role == "user") {
						ConversationMessage.User(conv.content)
					} else {
						ConversationMessage.Assistant(conv.content)
					}
				}
				conversationAdapter.setMessages(messages)
				scrollToBottom()
			}
		} else {
			val exception = result.exceptionOrNull()!!
			val apiEx = exception as? ChronoSyncApi.ApiException
			if (apiEx != null && apiEx.code == 401) {
				handleApiError(exception)
			}
			// 加载失败不显示错误，静默处理
		}
	}
	
	private fun scrollToBottom() {
		conversationRecyclerView.post {
			if (conversationAdapter.itemCount > 0) {
				conversationRecyclerView.smoothScrollToPosition(conversationAdapter.itemCount - 1)
			}
		}
	}

	private fun setupCalendar() {
		val daysOfWeek = daysOfWeek(firstDayOfWeek = DayOfWeek.MONDAY)
		val currentMonth = YearMonth.now()
		val startMonth = currentMonth.minusMonths(12)
		val endMonth = currentMonth.plusMonths(12)

		calendarView.daySize = DaySize.Square
		calendarView.monthMargins = MarginValues(start = 16, end = 16, top = 8, bottom = 8)
		calendarView.scrollPaged = true

		calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
			override fun create(view: View) = DayViewContainer(view)
			override fun bind(container: DayViewContainer, data: CalendarDay) {
				container.day = data
				container.bind(data, markersByDate[data.date].orEmpty(), selectedDate)
			}
		}

		calendarView.monthHeaderBinder = object : MonthHeaderFooterBinder<MonthHeaderViewContainer> {
			override fun create(view: View) = MonthHeaderViewContainer(view)
			override fun bind(container: MonthHeaderViewContainer, data: CalendarMonth) {
				container.bind(daysOfWeek)
			}
		}

		calendarView.setup(startMonth, endMonth, daysOfWeek.first())
		calendarView.scrollToMonth(currentMonth)
		monthTitleText.text = "${currentMonth.year} 年 ${currentMonth.month.value} 月"

		calendarView.monthScrollListener = { month ->
			monthTitleText.text = "${month.yearMonth.year} 年 ${month.yearMonth.month.value} 月"
		}
	}

	private fun onDateSelected(date: LocalDate) {
		val oldDate = selectedDate
		if (oldDate != date) {
			selectedDate = date
			oldDate?.let { calendarView.notifyDateChanged(it) }
			calendarView.notifyDateChanged(date)
		}
		refreshEventsForDate(date)
	}

	private fun refreshEventsForDate(date: LocalDate) {
		lifecycleScope.launch {
			val events = withContext(Dispatchers.IO) { eventDao.getEventsForDate(date.toString()) }
			selectedDateText.text = if (events.isEmpty()) {
				"${date} 暂无日程"
			} else {
				"${date} 的日程 (${events.size} 项)"
			}
			eventsAdapter.submitList(events)
			updateLynxView(events)
		}
	}

	private fun updateLynxView(events: List<ScheduleEventEntity>) {
		val tasks = events.map { event ->
			val priorityStr = when (event.priority) {
				3 -> "HIGH"
				2 -> "MEDIUM"
				else -> "LOW"
			}
			val tagStr = when (event.type) {
				"WORK" -> "工作"
				"LIFE" -> "生活"
				"STUDY" -> "学习"
				else -> "其他"
			}
			mapOf(
				"id" to (event.serverId ?: event.id.toString()),
				"name" to event.title,
				"priority" to priorityStr,
				"time" to event.startTime,
				"tag" to tagStr
			)
		}
		val data = mapOf("tasks" to tasks)
		// 使用 updateData 更新数据，而不是重新加载整个 bundle
		lynxView?.updateData(data)
		
		// 手动构建 JavaOnlyArray，确保类型正确
		val args = com.lynx.react.bridge.JavaOnlyArray()
		val map = com.lynx.react.bridge.JavaOnlyMap()
		val tasksArray = com.lynx.react.bridge.JavaOnlyArray()
		
		for (task in tasks) {
			val taskMap = com.lynx.react.bridge.JavaOnlyMap()
			taskMap.putString("id", task["id"])
			taskMap.putString("name", task["name"])
			taskMap.putString("priority", task["priority"])
			taskMap.putString("time", task["time"])
			taskMap.putString("tag", task["tag"])
			tasksArray.pushMap(taskMap)
		}
		map.putArray("tasks", tasksArray)
		args.pushMap(map)
		
		// 同时发送全局事件，确保 React 端能接收到
		lynxView?.sendGlobalEvent("updateTasks", args)
	}

	private suspend fun refreshMarkers() {
		val markers = withContext(Dispatchers.IO) { eventDao.getMarkers() }
		android.util.Log.d("SyncDebug", "refreshMarkers: ${markers.size} markers")
		markersByDate = markers.groupBy { LocalDate.parse(it.date) }
		calendarView.notifyCalendarChanged()
	}

	private suspend fun refreshMemos() {
		val list = withContext(Dispatchers.IO) { memoDao.getAll() }
		android.util.Log.d("SyncDebug", "refreshMemos: got ${list.size} memos from DB")
		list.forEachIndexed { index, memo ->
			android.util.Log.d("SyncDebug", "refreshMemos: memo[$index] id=${memo.id}, title=${memo.title}")
		}
		withContext(Dispatchers.Main) {
			memoAdapter.submitList(list)
			android.util.Log.d("SyncDebug", "refreshMemos: submitted to adapter, itemCount=${memoAdapter.itemCount}")
			memoEmptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
			// 强制 RecyclerView 刷新 - 重新设置 adapter
			memoRecyclerView.adapter = null
			memoRecyclerView.adapter = memoAdapter
			android.util.Log.d("SyncDebug", "refreshMemos: adapter reset done")
		}
	}



	private fun markerColor(type: String, priority: Int): Int {
		val baseRes = when (type) {
			"WORK" -> R.color.blue_grey_700
			"LIFE" -> R.color.brown_700
			"STUDY" -> R.color.candy_lavender
			else -> R.color.example_1_selection_color
		}
		val base = getColor(baseRes)
		val blendToWhite = when (priority) {
			3 -> 0f
			2 -> 0.35f
			else -> 0.6f
		}
		return ColorUtils.blendARGB(base, Color.WHITE, blendToWhite)
	}

	private fun showEventDialog(date: LocalDate, existing: ScheduleEventEntity?) {
		val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_event_edit, null)
		val typeSpinner = dialogView.findViewById<Spinner>(R.id.eventTypeSpinner)
		val priorityGroup = dialogView.findViewById<RadioGroup>(R.id.eventPriorityGroup)
		val titleInput = dialogView.findViewById<EditText>(R.id.eventTitleInput)
		val startInput = dialogView.findViewById<EditText>(R.id.eventStartTimeInput)
		val endInput = dialogView.findViewById<EditText>(R.id.eventEndTimeInput)

		val typeLabels = resources.getStringArray(R.array.event_type_labels)
		val typeValues = resources.getStringArray(R.array.event_type_values)
		typeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, typeLabels)
		val existingType = existing?.type ?: "WORK"
		val typeIndex = typeValues.indexOf(existingType).let { if (it >= 0) it else 0 }
		typeSpinner.setSelection(typeIndex)

		when (existing?.priority ?: 2) {
			1 -> priorityGroup.check(R.id.priorityLow)
			2 -> priorityGroup.check(R.id.priorityMedium)
			else -> priorityGroup.check(R.id.priorityHigh)
		}

		fun showTimePicker(current: String?, onPicked: (String) -> Unit) {
			val (h0, m0) = try {
				current?.split(":")?.let { it[0].toInt() to it[1].toInt() } ?: (9 to 0)
			} catch (_: Exception) {
				9 to 0
			}
			TimePickerDialog(this, { _, hourOfDay, minute ->
				onPicked(String.format(Locale.US, "%02d:%02d", hourOfDay, minute))
			}, h0, m0, true).show()
		}

		startInput.setOnClickListener { showTimePicker(startInput.text?.toString()) { startInput.setText(it) } }
		endInput.setOnClickListener { showTimePicker(endInput.text?.toString()) { endInput.setText(it) } }

		if (existing != null) {
			titleInput.setText(existing.title)
			startInput.setText(existing.startTime)
			endInput.setText(existing.endTime)
		}

		val builder = AlertDialog.Builder(this)
			.setTitle(if (existing == null) "添加日程" else "编辑日程")
			.setView(dialogView)
			.setPositiveButton("保存") { _, _ ->
				val title = titleInput.text.toString().trim()
				val start = startInput.text.toString().trim()
				val end = endInput.text.toString().trim()
				if (title.isEmpty()) return@setPositiveButton
				if (start.isEmpty() || end.isEmpty()) return@setPositiveButton
				if (start > end) return@setPositiveButton
				val type = typeValues[typeSpinner.selectedItemPosition]
				val priority = when (priorityGroup.checkedRadioButtonId) {
					R.id.priorityLow -> 1
					R.id.priorityHigh -> 3
					else -> 2
				}
				lifecycleScope.launch {
					try {
						withContext(Dispatchers.IO) {
							val token = getAccessToken()
							if (!token.isNullOrBlank()) {
								val startIso = toIsoOffsetString(date, start)
								val endIso = toIsoOffsetString(date, end)
								val serverId = existing?.serverId
								val remoteId = if (serverId.isNullOrBlank()) {
									ChronoSyncApp.chronoSyncApi.createEvent(
										token = token,
										title = title,
										startTime = startIso,
										endTime = endIso,
										type = type,
										priority = priority,
									).id
								} else {
									ChronoSyncApp.chronoSyncApi.updateEvent(
										token = token,
										eventId = serverId,
										title = title,
										startTime = startIso,
										endTime = endIso,
										type = type,
										priority = priority,
									).id
								}
								val entity = ScheduleEventEntity(
									existing?.id ?: 0,
									remoteId,
									date.toString(),
									title,
									start,
									end,
									type,
									priority,
								)
								if (existing == null || serverId.isNullOrBlank()) eventDao.insert(entity) else eventDao.update(entity)
							} else {
								val entity = ScheduleEventEntity(
									existing?.id ?: 0,
									existing?.serverId,
									date.toString(),
									title,
									start,
									end,
									type,
									priority,
								)
								if (existing == null) eventDao.insert(entity) else eventDao.update(entity)
							}
						}
						refreshMarkers()
						refreshEventsForDate(date)
						toast("日程已保存")
					} catch (e: Exception) {
						handleApiError(e)
					}
				}
			}
			.setNegativeButton("取消", null)

		if (existing != null) {
			builder.setNeutralButton("删除") { _, _ ->
				lifecycleScope.launch {
					withContext(Dispatchers.IO) {
						val token = getAccessToken()
						val serverId = existing.serverId
							if (!token.isNullOrBlank() && !serverId.isNullOrBlank()) {
								runCatching { ChronoSyncApp.chronoSyncApi.deleteEvent(token, serverId) }
						}
						eventDao.delete(existing)
					}
					refreshMarkers()
					refreshEventsForDate(date)
				}
			}
		}

		builder.show()
	}

	private fun showMemoDialog(existing: MemoEntity?) {
		val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_memo_edit, null)
		val titleInput = dialogView.findViewById<EditText>(R.id.memoTitleInput)
		val contentInput = dialogView.findViewById<EditText>(R.id.memoContentInput)

		if (existing != null) {
			titleInput.setText(existing.title)
			contentInput.setText(existing.content)
		}

		val builder = AlertDialog.Builder(this)
			.setTitle(if (existing == null) "新增备忘录" else "编辑备忘录")
			.setView(dialogView)
			.setPositiveButton("保存") { _, _ ->
				val title = titleInput.text.toString().trim()
				val content = contentInput.text.toString().trim()
				if (title.isEmpty() && content.isEmpty()) return@setPositiveButton
				lifecycleScope.launch {
					val now = System.currentTimeMillis()
					try {
						withContext(Dispatchers.IO) {
							val finalTitle = title.ifEmpty { "未命名" }
							val token = getAccessToken()
							if (!token.isNullOrBlank()) {
								val payload = encodeMemoPayload(finalTitle, content)
								val serverId = existing?.serverId
								val remoteId = if (serverId.isNullOrBlank()) {
									ChronoSyncApp.chronoSyncApi.createMemo(token, payload).id
								} else {
									ChronoSyncApp.chronoSyncApi.updateMemo(token, serverId, content = payload).id
								}
								val entity = MemoEntity(
									existing?.id ?: 0,
									remoteId,
									finalTitle,
									content,
									now,
								)
								if (existing == null || serverId.isNullOrBlank()) memoDao.insert(entity) else memoDao.update(entity)
							} else {
								if (existing == null) {
									memoDao.insert(MemoEntity(0, null, finalTitle, content, now))
								} else {
									memoDao.update(MemoEntity(existing.id, existing.serverId, finalTitle, content, now))
								}
							}
						}
						refreshMemos()
						toast("备忘录已保存")
					} catch (e: Exception) {
						handleApiError(e)
					}
				}
			}
			.setNegativeButton("取消", null)

		if (existing != null) {
			builder.setNeutralButton("删除") { _, _ ->
				lifecycleScope.launch {
					withContext(Dispatchers.IO) {
						val token = getAccessToken()
						val serverId = existing.serverId
							if (!token.isNullOrBlank() && !serverId.isNullOrBlank()) {
								runCatching { ChronoSyncApp.chronoSyncApi.deleteMemo(token, serverId) }
						}
						memoDao.delete(existing)
					}
					refreshMemos()
				}
			}
		}

		builder.show()
	}

	inner class DayViewContainer(view: View) : ViewContainer(view) {
		lateinit var day: CalendarDay
		private val textView: TextView = view.findViewById(android.R.id.text1)
		private val topBar: View = view.findViewById(R.id.exFiveDayFlightTop)
		private val bottomBar: View = view.findViewById(R.id.exFiveDayFlightBottom)

		init {
			view.setOnClickListener {
				if (day.position == DayPosition.MonthDate) {
					onDateSelected(day.date)
				}
			}
		}

		fun bind(day: CalendarDay, markers: List<EventMarker>, selectedDate: LocalDate?) {
			textView.text = day.date.dayOfMonth.toString()
			if (day.position == DayPosition.MonthDate) {
				textView.alpha = 1f
			} else {
				textView.alpha = 0.3f
			}
			// 根据事件类型/优先级绘制底部条形标记（最多展示两条）
			topBar.background = null
			bottomBar.background = null
			if (day.position == DayPosition.MonthDate && markers.isNotEmpty()) {
				val colors = markers
					.sortedByDescending { it.priority }
					.take(2)
					.map { markerColor(it.type, it.priority) }
				if (colors.size == 1) {
					bottomBar.setBackgroundColor(colors[0])
				} else {
					topBar.setBackgroundColor(colors[0])
					bottomBar.setBackgroundColor(colors[1])
				}
			}
			// 选中高亮：文字颜色略微区分
			textView.setTextColor(
				if (selectedDate == day.date) textView.context.getColor(R.color.example_1_selection_color)
				else textView.context.getColor(R.color.example_5_text_grey),
			)
		}
	}

	class MonthHeaderViewContainer(view: View) : ViewContainer(view) {
		private val legendLayout: android.view.ViewGroup = view.findViewById(R.id.legendLayout)
		fun bind(daysOfWeek: List<DayOfWeek>) {
			legendLayout.children.forEachIndexed { index, child ->
				(child as? TextView)?.text = daysOfWeek[index].name.take(3)
			}
		}
	}
}

class EventsAdapter : RecyclerView.Adapter<EventsAdapter.EventViewHolder>() {
	private val items = mutableListOf<ScheduleEventEntity>()
	var onItemClick: ((ScheduleEventEntity) -> Unit)? = null

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
		val view = LayoutInflater.from(parent.context)
			.inflate(R.layout.item_event, parent, false)
		return EventViewHolder(view)
	}

	override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
		holder.bind(items[position])
	}

	override fun getItemCount(): Int = items.size

	fun submitList(newItems: List<ScheduleEventEntity>) {
		items.clear()
		items.addAll(newItems)
		notifyDataSetChanged()
	}

	inner class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		private val titleText: TextView = view.findViewById(R.id.eventTitleText)
		private val timeText: TextView = view.findViewById(R.id.eventTimeText)
		private val colorBar: View = view.findViewById(R.id.eventColorBar)
		private val dateBlock: TextView = view.findViewById(R.id.itemFlightDateText)

		init {
			view.setOnClickListener {
				val position = bindingAdapterPosition
				if (position != RecyclerView.NO_POSITION) {
					onItemClick?.invoke(items[position])
				}
			}
		}
		fun bind(event: ScheduleEventEntity) {
			titleText.text = event.title
			timeText.text = "${event.startTime} - ${event.endTime}"
			val baseRes = when (event.type) {
				"WORK" -> R.color.blue_grey_700
				"LIFE" -> R.color.brown_700
				"STUDY" -> R.color.candy_lavender
				else -> R.color.example_1_selection_color
			}
			val base = itemView.context.getColor(baseRes)
			val blendToWhite = when (event.priority) {
				3 -> 0f
				2 -> 0.35f
				else -> 0.6f
			}
			val color = ColorUtils.blendARGB(base, Color.WHITE, blendToWhite)
			colorBar.setBackgroundColor(color)
			dateBlock.setBackgroundColor(color)
			dateBlock.text = event.startTime
		}
	}
}

class MemoAdapter : RecyclerView.Adapter<MemoAdapter.MemoViewHolder>() {
	private val items = mutableListOf<MemoEntity>()
	var onItemClick: ((MemoEntity) -> Unit)? = null

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoViewHolder {
		val view = LayoutInflater.from(parent.context)
			.inflate(android.R.layout.simple_list_item_2, parent, false)
		return MemoViewHolder(view)
	}

	override fun onBindViewHolder(holder: MemoViewHolder, position: Int) {
		holder.bind(items[position])
	}

	override fun getItemCount(): Int = items.size

	fun submitList(newItems: List<MemoEntity>) {
		android.util.Log.d("SyncDebug", "MemoAdapter.submitList: old=${items.size}, new=${newItems.size}")
		items.clear()
		items.addAll(newItems)
		android.util.Log.d("SyncDebug", "MemoAdapter.submitList: after update items=${items.size}")
		notifyDataSetChanged()
		android.util.Log.d("SyncDebug", "MemoAdapter.submitList: notifyDataSetChanged done")
	}

	inner class MemoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		private val titleText: TextView = view.findViewById(android.R.id.text1)
		private val contentText: TextView = view.findViewById(android.R.id.text2)

		init {
			view.setOnClickListener {
				val position = bindingAdapterPosition
				if (position != RecyclerView.NO_POSITION) {
					onItemClick?.invoke(items[position])
				}
			}
		}

		fun bind(memo: MemoEntity) {
			android.util.Log.d("SyncDebug", "MemoViewHolder.bind: id=${memo.id}, title=${memo.title}")
			titleText.text = memo.title
			contentText.text = memo.content
		}
	}
}


// ========== Agent V2 - 对话历史数据类 ==========

sealed class ConversationMessage {
	abstract val content: String
	
	data class User(override val content: String) : ConversationMessage()
	data class Assistant(override val content: String) : ConversationMessage()
}

class ConversationAdapter : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {
	private val items = mutableListOf<ConversationMessage>()
	
	companion object {
		private const val TYPE_USER = 1
		private const val TYPE_ASSISTANT = 2
	}
	
	fun addMessage(message: ConversationMessage) {
		items.add(message)
		notifyItemInserted(items.size - 1)
	}
	
	fun setMessages(messages: List<ConversationMessage>) {
		items.clear()
		items.addAll(messages)
		notifyDataSetChanged()
	}
	
	fun clear() {
		items.clear()
		notifyDataSetChanged()
	}
	
	override fun getItemViewType(position: Int): Int {
		return when (items[position]) {
			is ConversationMessage.User -> TYPE_USER
			is ConversationMessage.Assistant -> TYPE_ASSISTANT
		}
	}
	
	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val layoutRes = when (viewType) {
			TYPE_USER -> R.layout.item_conversation_user
			TYPE_ASSISTANT -> R.layout.item_conversation_assistant
			else -> R.layout.item_conversation_assistant
		}
		val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
		return ViewHolder(view)
	}
	
	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		holder.bind(items[position])
	}
	
	override fun getItemCount(): Int = items.size
	
	inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		private val contentText: TextView = view.findViewById(R.id.contentText)
		
		fun bind(message: ConversationMessage) {
			contentText.text = message.content
		}
	}
}
