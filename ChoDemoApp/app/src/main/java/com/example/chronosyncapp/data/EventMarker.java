package com.example.chronosyncapp.data;

/** Room 查询投影：用于日历底部条绘制 */
public class EventMarker {
    public final String date;
    public final String type;
    public final int priority;

    public EventMarker(String date, String type, int priority) {
        this.date = date;
        this.type = type;
        this.priority = priority;
    }
}

