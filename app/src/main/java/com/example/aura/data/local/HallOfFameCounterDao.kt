package com.example.aura.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface HallOfFameCounterDao {

    @Query("SELECT value FROM hof_counters WHERE ownerNodeKey = :owner AND statKey = :key LIMIT 1")
    suspend fun getValue(owner: String, key: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HallOfFameCounterEntity)

    @Transaction
    suspend fun setValue(owner: String, key: String, value: Long) {
        upsert(HallOfFameCounterEntity(owner, key, value))
    }
}
