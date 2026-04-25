package com.example.aura.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChannelChatMessageEntity::class,
        MessageHistoryGroupEntity::class,
        MessageHistoryEntryEntity::class,
        MeshNodePeerUptimeEntity::class,
        MapBeaconEntity::class,
        FavGroupEntity::class,
        FavGroupMemberEntity::class,
        UserStatsEntity::class,
        HallOfFameCounterEntity::class,
    ],
    version = 16,
    exportSchema = false,
)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun channelChatMessageDao(): ChannelChatMessageDao
    abstract fun messageHistoryDao(): MessageHistoryDao
    abstract fun meshNodePeerUptimeDao(): MeshNodePeerUptimeDao
    abstract fun mapBeaconDao(): MapBeaconDao
    abstract fun favGroupDao(): FavGroupDao
    abstract fun userStatsDao(): UserStatsDao
    abstract fun hallOfFameCounterDao(): HallOfFameCounterDao

    companion object {
        val ALL_MIGRATIONS = arrayOf(
            AuraDatabaseMigrations.MIGRATION_1_2,
            AuraDatabaseMigrations.MIGRATION_2_3,
            AuraDatabaseMigrations.MIGRATION_3_4,
            AuraDatabaseMigrations.MIGRATION_4_5,
            AuraDatabaseMigrations.MIGRATION_5_6,
            AuraDatabaseMigrations.MIGRATION_6_7,
            AuraDatabaseMigrations.MIGRATION_7_8,
            AuraDatabaseMigrations.MIGRATION_8_9,
            AuraDatabaseMigrations.MIGRATION_9_10,
            AuraDatabaseMigrations.MIGRATION_10_11,
            AuraDatabaseMigrations.MIGRATION_11_12,
            AuraDatabaseMigrations.MIGRATION_12_13,
            AuraDatabaseMigrations.MIGRATION_13_14,
            AuraDatabaseMigrations.MIGRATION_14_15,
            AuraDatabaseMigrations.MIGRATION_15_16,
        )
    }
}
