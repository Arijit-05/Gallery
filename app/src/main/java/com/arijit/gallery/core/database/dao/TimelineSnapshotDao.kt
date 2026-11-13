package com.arijit.gallery.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.arijit.gallery.core.database.entity.TimelineSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineSnapshotDao {

    @Query("SELECT * FROM timeline_snapshot ORDER BY positionIndex ASC")
    fun observeSnapshot(): Flow<List<TimelineSnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TimelineSnapshotEntity>)

    @Query("DELETE FROM timeline_snapshot")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceSnapshot(items: List<TimelineSnapshotEntity>) {
        clearAll()
        if (items.isNotEmpty()) {
            insertAll(items)
        }
    }
}

