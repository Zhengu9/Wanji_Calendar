package com.example.chronosyncapp.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "users", indices = {@Index(value = "username", unique = true)})
public class UserEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "username")
    public String username;

    @ColumnInfo(name = "password_hash")
    public String passwordHash;

    @ColumnInfo(name = "role")
    public String role;  // "admin" or "user"

    @ColumnInfo(name = "created_at")
    public long createdAt;

    public UserEntity(long id, String username, String passwordHash, String role, long createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }

    @Ignore
    public UserEntity(String username, String passwordHash, String role, long createdAt) {
        this(0, username, passwordHash, role, createdAt);
    }

    @Ignore
    public UserEntity(String username, String passwordHash, String role) {
        this(0, username, passwordHash, role, System.currentTimeMillis());
    }
}
