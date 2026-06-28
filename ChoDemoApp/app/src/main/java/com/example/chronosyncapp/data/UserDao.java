package com.example.chronosyncapp.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface UserDao {

    @Insert
    long insert(UserEntity user);

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    UserEntity findByUsername(String username);

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    UserEntity findById(long id);

    @Query("SELECT COUNT(*) FROM users")
    int count();

    @Query("SELECT * FROM users ORDER BY created_at DESC")
    List<UserEntity> getAllUsers();

    @Delete
    void delete(UserEntity user);
}
