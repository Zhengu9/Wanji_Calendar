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

    // ===== 搜索（方案A新增） =====

    @Query("SELECT * FROM memos WHERE title LIKE '%' || :keyword || '%' OR content LIKE '%' || :keyword || '%' ORDER BY updatedAt DESC")
    List<MemoEntity> searchByKeyword(String keyword);

    @Query("DELETE FROM memos WHERE id = :id")
    void deleteById(long id);
}
