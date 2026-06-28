package com.example.chronosyncapp.data;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "schedule_events")
public class ScheduleEventEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    /** 服务端事件 ID（UUID），为空表示仅本地数据 */
    public String serverId;

    /** ISO-8601 日期字符串：yyyy-MM-dd */
    public String date;
    public String title;
    /** 24小时制：HH:mm */
    public String startTime;
    /** 24小时制：HH:mm */
    public String endTime;
    /** 事件类型：WORK/LIFE/STUDY */
    public String type;
    /** 优先级：1(低)/2(中)/3(高) */
    public int priority;

    public ScheduleEventEntity(long id, String serverId, String date, String title, String startTime, String endTime, String type, int priority) {
        this.id = id;
        this.serverId = serverId;
        this.date = date;
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.type = type;
        this.priority = priority;
    }

    @Ignore
    public ScheduleEventEntity(String date, String title, String startTime, String endTime, String type, int priority) {
        this(0, null, date, title, startTime, endTime, type, priority);
    }

    @Ignore
    public ScheduleEventEntity(String serverId, String date, String title, String startTime, String endTime, String type, int priority) {
        this(0, serverId, date, title, startTime, endTime, type, priority);
    }
}
