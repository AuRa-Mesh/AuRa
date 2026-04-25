package com.example.aura

import android.app.Application
import android.os.Build
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.room.Room
import com.example.aura.app.AppForegroundState
import com.example.aura.app.AppUptimeTracker
import com.example.aura.bluetooth.MeshNodeFullSettingsPrefetcher
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.bluetooth.tryAutoConnectSavedBleNode
import com.example.aura.bluetooth.tryAutoScanAndConnectSavedBleNode
import com.example.aura.mesh.nodedb.MeshNodeDbRepository
import com.example.aura.data.local.AuraDatabase
import com.example.aura.history.MessageHistoryRecorder
import com.example.aura.history.MessageHistoryRepository
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.aura.app.AuraBackgroundUptimeTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.example.aura.mesh.MeshService
import com.example.aura.mesh.ResilientMeshWorker
import com.example.aura.mesh.uptime.UptimeMeshSyncWorker
import com.example.aura.mesh.repository.MeshIncomingChatRepository
import com.example.aura.mesh.repository.MessageRepository
import com.example.aura.notifications.MeshNotificationDispatcher
import com.example.aura.preferences.AppLocalePreferences
import com.example.aura.vip.VipMeshRecoveryCoordinator
import com.example.aura.vip.VipStatusBroadcaster
import com.example.aura.ui.map.MapBeaconSyncRepository
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.WellKnownTileServer
import java.util.concurrent.TimeUnit

class AuraApplication : Application(), ImageLoaderFactory {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var chatDatabase: AuraDatabase
        private set

    val favGroupDao by lazy { chatDatabase.favGroupDao() }

    lateinit var channelMessageRepository: MessageRepository
        private set

    lateinit var messageHistoryRepository: MessageHistoryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        LegacyStorageMigration.migrateIfNeeded(this)
        Mapbox.getInstance(this, "offline-aura", WellKnownTileServer.MapLibre)
        coil.Coil.setImageLoader(this)
        AppUptimeTracker.init(this)
        AppLocalePreferences.applyStoredLocale(this)
        MeshService.ensureChannel(this)
        MeshNotificationDispatcher.ensureChannels(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                AppForegroundState.setForeground(true)
                AuraBackgroundUptimeTracker.onAppForegrounded(this@AuraApplication)
            }

            override fun onStop(owner: LifecycleOwner) {
                AppForegroundState.setForeground(false)
                AuraBackgroundUptimeTracker.onAppBackgrounded(this@AuraApplication)
            }
        })
        chatDatabase = Room.databaseBuilder(
            applicationContext,
            AuraDatabase::class.java,
            "aura_chat.db",
        )
            .addMigrations(*AuraDatabase.ALL_MIGRATIONS)
            .fallbackToDestructiveMigration()
            .build()
        channelMessageRepository = MessageRepository(chatDatabase.channelChatMessageDao())
        messageHistoryRepository = MessageHistoryRepository(applicationContext, chatDatabase.messageHistoryDao())
        MessageHistoryRecorder.repository = messageHistoryRepository
        MeshNodeSyncMemoryStore.init(this)
        // MapBeacon до MeshIncoming: при FromRadio [MeshIncomingChatRepository] сразу зовёт [MapBeaconSyncRepository.consumeStreamFrame].
        MapBeaconSyncRepository.install(this)
        MeshIncomingChatRepository.install(this)
        MeshNodeDbRepository.init(this)
        MeshNodeDbRepository.attachPeerUptimeDao(chatDatabase.meshNodePeerUptimeDao())
        MeshNodeFullSettingsPrefetcher.start(this)
        VipStatusBroadcaster.start(this)
        VipMeshRecoveryCoordinator.start(this)
        MeshService.setMaintainConnection(true)
        MeshService.startIfNeeded(this)
        val resilientWork = PeriodicWorkRequestBuilder<ResilientMeshWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "resilient_mesh_keepalive",
            ExistingPeriodicWorkPolicy.KEEP,
            resilientWork,
        )
        val uptimeWork = PeriodicWorkRequestBuilder<UptimeMeshSyncWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "aura_uptime_mesh_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            uptimeWork,
        )
        // При старте приложения сразу пытаемся вернуть BLE-связь с последней нодой.
        tryAutoConnectSavedBleNode(applicationContext)
        tryAutoScanAndConnectSavedBleNode(applicationContext)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()

    companion object {
        private const val TAG = "AuraApplication"
    }
}
