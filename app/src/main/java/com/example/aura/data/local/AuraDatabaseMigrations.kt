package com.example.aura.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AuraDatabaseMigrations {

    /** Цитирование / ответ по Mesh: reply_id + превью в таблице. */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE channel_chat_messages ADD COLUMN replyToPacketId INTEGER")
            db.execSQL("ALTER TABLE channel_chat_messages ADD COLUMN replyToFromNodeNum INTEGER")
            db.execSQL("ALTER TABLE channel_chat_messages ADD COLUMN replyPreviewText TEXT")
        }
    }

    /** Флаг прочитанности для счётчика непрочитанных на списке каналов. */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE channel_chat_messages ADD COLUMN isRead INTEGER NOT NULL DEFAULT 1",
            )
        }
    }

    /** Реакции (tapback): JSON карта nodenum → эмодзи. */
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE channel_chat_messages ADD COLUMN reactionsJson TEXT NOT NULL DEFAULT '{}'",
            )
        }
    }

    /** Унифицированная история: группы + сообщения (текст, голос, координаты, сервис). */
    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS message_history_groups (
                    groupId TEXT NOT NULL PRIMARY KEY,
                    deviceMac TEXT NOT NULL,
                    channelIndex INTEGER NOT NULL,
                    lastMessageAtMs INTEGER NOT NULL,
                    title TEXT
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_message_history_groups_mac_ch " +
                    "ON message_history_groups (deviceMac, channelIndex)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS message_history_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    groupId TEXT NOT NULL,
                    type INTEGER NOT NULL,
                    createdAtEpochMs INTEGER NOT NULL,
                    textBody TEXT,
                    latitude REAL,
                    longitude REAL,
                    serviceKind TEXT,
                    servicePayloadJson TEXT,
                    voiceFileRelativePath TEXT,
                    voiceDurationMs INTEGER,
                    imageFileRelativePath TEXT,
                    isOutgoing INTEGER NOT NULL,
                    fromNodeNum INTEGER NOT NULL,
                    dedupKey TEXT,
                    deliveryStatus INTEGER,
                    FOREIGN KEY(groupId) REFERENCES message_history_groups(groupId) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_message_history_entries_group_time " +
                    "ON message_history_entries (groupId, createdAtEpochMs)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_message_history_entries_groupId " +
                    "ON message_history_entries (groupId)",
            )
        }
    }

    /** Aura: аптайм приложения других узлов (последнее известное значение + время приёма). */
    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS mesh_node_peer_uptime (
                    deviceMacNorm TEXT NOT NULL,
                    nodeNum INTEGER NOT NULL,
                    lastKnownUptimeSec INTEGER NOT NULL,
                    uptimeTimestampEpochSec INTEGER NOT NULL,
                    PRIMARY KEY (deviceMacNorm, nodeNum)
                )
                """.trimIndent(),
            )
        }
    }

    /** Карта: маячки по каналам для реактивного списка и синхронизации. */
    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS map_beacons (
                    id INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    timestampCreated INTEGER NOT NULL,
                    creatorNodeNum INTEGER NOT NULL,
                    creatorLongName TEXT NOT NULL,
                    channelId TEXT NOT NULL,
                    channelIndex INTEGER NOT NULL,
                    ttlMs INTEGER NOT NULL,
                    PRIMARY KEY (channelId, id)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_map_beacons_channelIndex ON map_beacons(channelIndex)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_map_beacons_timestampCreated ON map_beacons(timestampCreated)",
            )
        }
    }

    /** Карта: цвет метки (выбирается пользователем при создании). */
    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE map_beacons ADD COLUMN color TEXT NOT NULL DEFAULT '#39E7FF'")
        }
    }

    /** Узлы: избранные папки-группы контактов. */
    val MIGRATION_8_9: Migration = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS fav_groups (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS fav_group_members (
                    groupId INTEGER NOT NULL,
                    nodeNum INTEGER NOT NULL,
                    PRIMARY KEY (groupId, nodeNum),
                    FOREIGN KEY(groupId) REFERENCES fav_groups(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_fav_group_members_groupId ON fav_group_members(groupId)",
            )
        }
    }

    /** Маяки: признак «из чата» для режима «Локально» на карте. */
    val MIGRATION_9_10: Migration = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE map_beacons ADD COLUMN fromChatLink INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    /** Маяки: локальная установка с карты (без mesh), видна в «Локально». */
    val MIGRATION_10_11: Migration = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE map_beacons ADD COLUMN localMapInstall INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    /** Личные сообщения: привязка строки к собеседнику (nodenum). */
    val MIGRATION_11_12: Migration = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE channel_chat_messages ADD COLUMN dmPeerNodeNum INTEGER")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_channel_chat_messages_mac_ch_dm_created " +
                    "ON channel_chat_messages (deviceMac, channelIndex, dmPeerNodeNum, createdAtMs)",
            )
        }
    }

    /** История выполненных квестов (mesh progression; колонка rewardAu зарезервирована). */
    val MIGRATION_12_13: Migration = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS quest_completions (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    questId TEXT NOT NULL,
                    rewardAu INTEGER NOT NULL,
                    completedAtMs INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_quest_completions_questId " +
                    "ON quest_completions (questId)",
            )
        }
    }

    /** Квесты в Room: владелец — mesh node key (раздельная история на узел). */
    val MIGRATION_13_14: Migration = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE quest_completions ADD COLUMN ownerNodeKey TEXT NOT NULL DEFAULT ''",
            )
            db.execSQL("DROP INDEX IF EXISTS index_quest_completions_questId")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_quest_completions_owner_quest " +
                    "ON quest_completions (ownerNodeKey, questId)",
            )
        }
    }

    /** Удаление таблицы квестов (квесты убраны из приложения). */
    val MIGRATION_14_15: Migration = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS quest_completions")
        }
    }

    /** ОП и счётчики «Аллеи славы» (Room). */
    val MIGRATION_15_16: Migration = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS user_stats (
                    ownerNodeKey TEXT NOT NULL PRIMARY KEY,
                    totalXp INTEGER NOT NULL,
                    updatedAtMs INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS hof_counters (
                    ownerNodeKey TEXT NOT NULL,
                    statKey TEXT NOT NULL,
                    value INTEGER NOT NULL,
                    PRIMARY KEY(ownerNodeKey, statKey)
                )
                """.trimIndent(),
            )
        }
    }
}
