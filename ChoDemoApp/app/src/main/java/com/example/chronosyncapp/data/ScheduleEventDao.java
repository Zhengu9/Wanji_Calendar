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
}
