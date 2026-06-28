package com.example.chronosyncapp

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chronosyncapp.data.AppDatabase
import com.example.chronosyncapp.data.ScheduleEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var statUsers: TextView
    private lateinit var statEvents: TextView
    private lateinit var statMemos: TextView
    private lateinit var statTypeDistribution: TextView
    private lateinit var eventList: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        db = AppDatabase.get(this)
        statUsers = findViewById(R.id.statUsers)
        statEvents = findViewById(R.id.statEvents)
        statMemos = findViewById(R.id.statMemos)
        statTypeDistribution = findViewById(R.id.statTypeDistribution)
        eventList = findViewById(R.id.adminEventList)

        eventList.layoutManager = LinearLayoutManager(this)
        findViewById<View>(R.id.adminBackButton).setOnClickListener { finish() }

        loadStats()
    }

    private fun loadStats() {
        CoroutineScope(Dispatchers.IO).launch {
            val userCount = db.userDao().count()
            val events = db.scheduleEventDao().getAllEvents()
            val memos = db.memoDao().getAll()

            withContext(Dispatchers.Main) {
                statUsers.text = "$userCount"
                statEvents.text = "${events.size}"
                statMemos.text = "${memos.size}"

                val workCount = events.count { it.type == "WORK" }
                val lifeCount = events.count { it.type == "LIFE" }
                val studyCount = events.count { it.type == "STUDY" }
                statTypeDistribution.text = "📊 日程分布：工作 ${workCount} | 生活 ${lifeCount} | 学习 ${studyCount}"

                val recent = events.sortedByDescending { "${it.date}${it.startTime}" }.take(20)
                eventList.adapter = AdminEventAdapter(recent)
            }
        }
    }

    inner class AdminEventAdapter(private val items: List<ScheduleEventEntity>) :
        RecyclerView.Adapter<AdminEventAdapter.VH>() {
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            private val titleText: TextView = view.findViewById(android.R.id.text1)
            private val detailText: TextView = view.findViewById(android.R.id.text2)
            fun bind(e: ScheduleEventEntity) {
                titleText.text = e.title
                detailText.text = "${e.date}  ${e.startTime}-${e.endTime}  [${e.type}] P${e.priority}"
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
