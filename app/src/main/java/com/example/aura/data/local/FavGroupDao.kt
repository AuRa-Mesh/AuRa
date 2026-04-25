package com.example.aura.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavGroupDao {

    @Query("SELECT * FROM fav_groups ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<FavGroupEntity>>

    @Query("SELECT COUNT(*) FROM fav_groups")
    suspend fun countGroups(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGroup(group: FavGroupEntity): Long

    @Query("DELETE FROM fav_groups WHERE id = :id")
    suspend fun deleteGroup(id: Long)

    @Query("SELECT nodeNum FROM fav_group_members WHERE groupId = :groupId")
    fun observeMembers(groupId: Long): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMember(member: FavGroupMemberEntity)

    @Query("DELETE FROM fav_group_members WHERE groupId = :groupId AND nodeNum = :nodeNum")
    suspend fun deleteMember(groupId: Long, nodeNum: Long)

    @Query("SELECT COUNT(*) FROM fav_group_members WHERE groupId = :groupId")
    fun observeMemberCount(groupId: Long): Flow<Int>

    @Query("SELECT COUNT(DISTINCT nodeNum) FROM fav_group_members")
    suspend fun countDistinctPeers(): Int
}
