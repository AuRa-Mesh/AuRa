package com.example.aura.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface UserStatsDao {

    @Query("SELECT totalXp FROM user_stats WHERE ownerNodeKey = :owner LIMIT 1")
    fun observeTotalXp(owner: String): Flow<Long?>

    @Query("SELECT totalXp FROM user_stats WHERE ownerNodeKey = :owner LIMIT 1")
    suspend fun getTotalXp(owner: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: UserStatsEntity)

    @Query(
        "UPDATE user_stats SET totalXp = totalXp + :delta, updatedAtMs = :now WHERE ownerNodeKey = :owner",
    )
    suspend fun addXp(owner: String, delta: Long, now: Long): Int

    @Transaction
    suspend fun ensureRowAndAddXp(owner: String, delta: Long, now: Long) {
        insertIgnore(UserStatsEntity(ownerNodeKey = owner, totalXp = 0L, updatedAtMs = now))
        addXp(owner, delta, now)
    }

    @Query(
        "UPDATE user_stats SET totalXp = MAX(totalXp, :value), updatedAtMs = :now WHERE ownerNodeKey = :owner",
    )
    suspend fun bumpTotalXpAtLeast(owner: String, value: Long, now: Long): Int

    @Transaction
    suspend fun ensureRowAndBumpTotalXpAtLeast(owner: String, value: Long, now: Long) {
        insertIgnore(UserStatsEntity(ownerNodeKey = owner, totalXp = 0L, updatedAtMs = now))
        bumpTotalXpAtLeast(owner, value, now)
    }
}
