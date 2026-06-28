package com.example.chronosyncapp.data;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "memos")
public class MemoEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    /** 服务端备忘录 ID（UUID），为空表示仅本地数据 */
    public String serverId;

    public String title;
    public String content;
    public long updatedAt;

    public MemoEntity(long id, String serverId, String title, String content, long updatedAt) {
        this.id = id;
        this.serverId = serverId;
        this.title = title;
        this.content = content;
        this.updatedAt = updatedAt;
    }

    @Ignore
    public MemoEntity(String title, String content, long updatedAt) {
        this(0, null, title, content, updatedAt);
    }

    @Ignore
    public MemoEntity(String serverId, String title, String content, long updatedAt) {
        this(0, serverId, title, content, updatedAt);
    }
}
