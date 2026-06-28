package com.example.chronosyncapp.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {ScheduleEventEntity.class, MemoEntity.class},
        version = 2,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    public abstract ScheduleEventDao scheduleEventDao();

    public abstract MemoDao memoDao();

    public static AppDatabase get(Context context) {
        if (INSTANCE != null) return INSTANCE;
        synchronized (AppDatabase.class) {
            if (INSTANCE == null) {
                INSTANCE = Room.databaseBuilder(
                        context.getApplicationContext(),
                        AppDatabase.class,
                        "chronosync.db"
                ).fallbackToDestructiveMigration().build();
            }
        }
        return INSTANCE;
    }
}
