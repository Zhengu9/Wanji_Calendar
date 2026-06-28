package com.example.chronosyncapp

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chronosyncapp.data.AppDatabase
import com.example.chronosyncapp.data.MemoEntity
import com.example.chronosyncapp.data.ScheduleEventEntity
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var searchInput: EditText
    private lateinit var resultList: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var tabLayout: TabLayout

    private var isEventTab = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        db = AppDatabase.get(this)
        searchInput = findViewById(R.id.searchInput)
        resultList = findViewById(R.id.searchResultList)
        emptyText = findViewById(R.id.searchEmptyText)
        tabLayout = findViewById(R.id.searchTabLayout)

        resultList.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.searchBackButton).setOnClickListener { finish() }
        findViewById<View>(R.id.searchButton).setOnClickListener { doSearch() }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) { isEventTab = tab?.position == 0; doSearch() }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun doSearch() {
        val keyword = searchInput.text?.toString()?.trim().orEmpty()
        if (keyword.isBlank()) {
            emptyText.text = "输入关键词开始搜索"
            emptyText.visibility = View.VISIBLE
            resultList.visibility = View.GONE
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            if (isEventTab) {
                val events = db.scheduleEventDao().searchByTitle(keyword)
                withContext(Dispatchers.Main) {
                    if (events.isEmpty()) {
                        emptyText.text = "未找到包含「$keyword」的日程"
                        emptyText.visibility = View.VISIBLE
                        resultList.visibility = View.GONE
                    } else {
                        resultList.visibility = View.VISIBLE
                        emptyText.visibility = View.GONE
                        resultList.adapter = EventSearchAdapter(events)
                    }
                }
            } else {
                val memos = db.memoDao().searchByKeyword(keyword)
                withContext(Dispatchers.Main) {
                    if (memos.isEmpty()) {
                        emptyText.text = "未找到包含「$keyword」的备忘录"
                        emptyText.visibility = View.VISIBLE
                        resultList.visibility = View.GONE
                    } else {
                        resultList.visibility = View.VISIBLE
                        emptyText.visibility = View.GONE
                        resultList.adapter = MemoSearchAdapter(memos)
                    }
                }
            }
        }
    }

    // ===== Event 搜索结果 Adapter =====
    inner class EventSearchAdapter(private val items: List<ScheduleEventEntity>) :
        RecyclerView.Adapter<EventSearchAdapter.VH>() {
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            private val titleText: TextView = view.findViewById(android.R.id.text1)
            private val detailText: TextView = view.findViewById(android.R.id.text2)
            fun bind(e: ScheduleEventEntity) {
                titleText.text = e.title
                detailText.text = "${e.date}  ${e.startTime}-${e.endTime}  [${e.type}]  优先级:${e.priority}"
            }
        }
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return VH(view)
        }
        override fun onBindViewHolder(holder: VH, position: Int) { holder.bind(items[position]) }
        override fun getItemCount(): Int = items.size
    }

    // ===== Memo 搜索结果 Adapter =====
    inner class MemoSearchAdapter(private val items: List<MemoEntity>) :
        RecyclerView.Adapter<MemoSearchAdapter.VH>() {
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            private val titleText: TextView = view.findViewById(android.R.id.text1)
            private val detailText: TextView = view.findViewById(android.R.id.text2)
            fun bind(m: MemoEntity) {
                titleText.text = m.title
                detailText.text = m.content
            }
        }
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return VH(view)
        }
        override fun onBindViewHolder(holder: VH, position: Int) { holder.bind(items[position]) }
        override fun getItemCount(): Int = items.size
    }
}
