package com.example.chronosyncapp.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "conversation_history")
public class ConversationHistoryEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "role")
    public String role;  // "user", "assistant", or "tool"

    @ColumnInfo(name = "content")
    public String content;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    public ConversationHistoryEntity(long id, String role, String content, long createdAt) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    @Ignore
    public ConversationHistoryEntity(String role, String content, long createdAt) {
        this(0, role, content, createdAt);
    }

    @Ignore
    public ConversationHistoryEntity(String role, String content) {
        this(0, role, content, System.currentTimeMillis());
    }
}
