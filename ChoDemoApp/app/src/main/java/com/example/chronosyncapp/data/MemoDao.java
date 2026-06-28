package com.example.chronosyncapp.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface MemoDao {
    @Query("SELECT COUNT(*) FROM memos")
    int count();

    @Query("SELECT * FROM memos ORDER BY updatedAt DESC")
    List<MemoEntity> getAll();

    @Query("DELETE FROM memos")
    void deleteAll();

    @Insert
    long insert(MemoEntity memo);

    @Update
    void update(MemoEntity memo);

    @Delete
    void delete(MemoEntity memo);
}
