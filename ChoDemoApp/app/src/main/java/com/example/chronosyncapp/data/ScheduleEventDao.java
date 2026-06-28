package com.example.chronosyncapp.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ScheduleEventDao {
    @Query("SELECT COUNT(*) FROM schedule_events")
    int count();

    @Query("SELECT * FROM schedule_events WHERE date = :date ORDER BY startTime ASC")
    List<ScheduleEventEntity> getEventsForDate(String date);

    @Query("SELECT date, type, priority FROM schedule_events")
    List<EventMarker> getMarkers();

    @Query("DELETE FROM schedule_events")
    void deleteAll();

    @Insert
    long insert(ScheduleEventEntity event);

    @Update
    void update(ScheduleEventEntity event);

    @Delete
    void delete(ScheduleEventEntity event);

    // ===== 搜索和范围查询（方案A新增） =====

    @Query("SELECT * FROM schedule_events WHERE title LIKE '%' || :keyword || '%' ORDER BY date DESC, startTime ASC")
    List<ScheduleEventEntity> searchByTitle(String keyword);

    @Query("SELECT * FROM schedule_events WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC, startTime ASC")
    List<ScheduleEventEntity> getEventsInRange(String startDate, String endDate);

    @Query("SELECT * FROM schedule_events WHERE date >= :startDate ORDER BY date ASC, startTime ASC LIMIT :limit")
    List<ScheduleEventEntity> getUpcomingEvents(String startDate, int limit);

    @Query("SELECT * FROM schedule_events ORDER BY date ASC, startTime ASC")
    List<ScheduleEventEntity> getAllEvents();
}
