package com.example.chronosyncapp.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ConversationHistoryDao {

    @Insert
    long insert(ConversationHistoryEntity conversation);

    @Query("SELECT * FROM conversation_history ORDER BY created_at ASC LIMIT :limit")
    List<ConversationHistoryEntity> getRecent(int limit);

    @Query("SELECT * FROM conversation_history ORDER BY created_at ASC")
    List<ConversationHistoryEntity> getAll();

    @Query("DELETE FROM conversation_history")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM conversation_history")
    int count();
}
