package com.example.aura

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.aura.preferences.AppThemeMode
import com.example.aura.preferences.AppThemePreferences
import com.example.aura.preferences.FirstLaunchTutorialPreferences
import com.example.aura.preferences.ProfileLocalAvatarStore
import com.example.aura.preferences.VipAccessPreferences
import com.example.aura.preferences.VipExternalReadPermission
import com.example.aura.bluetooth.MeshDeviceTransport
import com.example.aura.bluetooth.MeshNodeSyncMemoryStore
import com.example.aura.mesh.MeshBatteryOptimization
import com.example.aura.mesh.MessageResilientService
import com.example.aura.mesh.MeshService
import com.example.aura.meshwire.MeshWireNodeSummary
import com.example.aura.security.NodeAuthStore
import com.example.aura.ui.screens.ChatScreen
import com.example.aura.ui.screens.FirstLaunchInstructionScreen
import com.example.aura.ui.screens.NodeProfileAsNodeDetailScreen
import com.example.aura.ui.screens.PasswordScreen
import com.example.aura.ui.screens.SplashScreen
import com.example.aura.app.AppUptimeTracker
import com.example.aura.ui.theme.AuraTheme

enum class AppScreen { FirstLaunchInstruction, Splash, Password, Chat, NodeProfile }

private enum class FirstLaunchDismissDestination {
    /** Первый холодный старт: после закрытия — Splash и отметка «инструкция пройдена». */
    Splash,
    /** Из настроек: только возврат в чат. */
    Chat,
}

class MainActivity : ComponentActivity() {

    private val notificationIntentKey = mutableStateOf(0)
    private val profileExportDeepLinkKey = mutableStateOf(0)
    private val profileExportDeepLinkText = mutableStateOf<String?>(null)

    private fun handleProfileViewIntent(i: Intent?) {
        if (i?.action != Intent.ACTION_VIEW) return
        val u = i.data ?: return
        if (u.scheme?.equals("aura", true) != true) return
        if (u.host?.equals("profile", true) != true) return
        val s = u.toString()
        if (s.isNotBlank()) {
            profileExportDeepLinkText.value = s
            profileExportDeepLinkKey.value = profileExportDeepLinkKey.value + 1
        }
    }

    /**
     * Разовый запрос `READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE` на «похожей на свежую
     * установку» сессии — нужен, чтобы восстановить VIP-sentinel из `Pictures/Aura/`
     * после удаления и переустановки приложения (см. [VipExternalReadPermission]). Без
     * этого разрешения осиротевшие файлы не видны переустановленной копии на API 30+.
     */
    private val vipExternalPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Результат нам здесь не важен: отметка «запрос сделан» уже стоит, а чтение
            // sentinel-PNG произойдёт дальше (в `ensureInitialTimerSeeded`), где и проявится
            // эффект полученного разрешения.
        }

    override fun onDestroy() {
        if (isFinishing) {
            AppUptimeTracker.checkpoint()
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationIntentKey.value++
        handleProfileViewIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Запрашиваем доступ к медиа один раз, если VIP-таймер ещё не засеян локально —
        // это даёт шанс прочитать внешний sentinel после переустановки. Диалог покажется
        // заметно раньше, чем пользователь попадёт на экран успешной авторизации, где
        // и происходит фактическое восстановление таймера.
        if (VipExternalReadPermission.shouldRequest(this)) {
            VipExternalReadPermission.markAsked(this)
            vipExternalPermLauncher.launch(VipExternalReadPermission.requiredPermission())
        }

        handleProfileViewIntent(intent)

        val savedAuth = NodeAuthStore.load(this)
        val storedIdentity = NodeAuthStore.loadStoredIdentity(this)

        setContent {
            val themeMode = AppThemePreferences.getMode(this)
            val darkTheme = when (themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            AuraTheme(darkTheme = darkTheme, dynamicColor = false) {
                // Первый запуск: предложение инструкции → Splash; дальше холодный старт с Splash без автоподключения.
                var currentScreen by remember {
                    mutableStateOf(
                        if (FirstLaunchTutorialPreferences.isFirstLaunchFlowCompleted(this@MainActivity)) {
                            AppScreen.Splash
                        } else {
                            AppScreen.FirstLaunchInstruction
                        },
                    )
                }
                /** Стартовая нижняя вкладка чата (0…3); 2 = «Карта» при возврате к той же ноде после Splash. */
                var chatInitialTabIndex by remember { mutableIntStateOf(0) }
                var profileDirectDmRequestId by remember { mutableIntStateOf(0) }
                var profileDirectDmTarget by remember { mutableStateOf<MeshWireNodeSummary?>(null) }
                var connectedNodeId by remember {
                    mutableStateOf(savedAuth?.nodeId ?: storedIdentity?.nodeId ?: "")
                }
                var meshDeviceAddress by remember {
                    mutableStateOf<String?>(savedAuth?.deviceAddress ?: storedIdentity?.deviceAddress)
                }
                var profileAvatarPath by remember { mutableStateOf<String?>(null) }
                var firstLaunchDismissDestination by remember {
                    mutableStateOf(FirstLaunchDismissDestination.Splash)
                }
                LaunchedEffect(Unit) {
                    profileAvatarPath = ProfileLocalAvatarStore.loadPath(this@MainActivity)
                }
                LaunchedEffect(currentScreen, meshDeviceAddress) {
                    val raw = meshDeviceAddress?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
                    val norm = MeshNodeSyncMemoryStore.normalizeKey(raw)
                    if (MeshDeviceTransport.isTcpAddress(norm) || MeshDeviceTransport.isUsbAddress(norm)) {
                        stopService(Intent(this@MainActivity, MessageResilientService::class.java))
                        return@LaunchedEffect
                    }
                    if (currentScreen == AppScreen.Chat || currentScreen == AppScreen.Password) {
                        MeshService.setMaintainConnection(true)
                        MeshService.startIfNeeded(this@MainActivity)
                    }
                    if (currentScreen == AppScreen.Chat) {
                        // Задержка: на Samsung и других устройствах startActivity из LaunchedEffect
                        // во время навигации открывает системные настройки батареи раньше, чем
                        // успевает завершиться анимация перехода — приложение уходит в фон.
                        delay(3_500L)
                        MeshBatteryOptimization.maybeRequestOnce(this@MainActivity)
                    }
                }

                when (currentScreen) {
                    AppScreen.FirstLaunchInstruction -> FirstLaunchInstructionScreen(
                        onDismiss = {
                            when (firstLaunchDismissDestination) {
                                FirstLaunchDismissDestination.Splash -> {
                                    FirstLaunchTutorialPreferences.markFirstLaunchFlowCompleted(this@MainActivity)
                                    currentScreen = AppScreen.Splash
                                }
                                FirstLaunchDismissDestination.Chat -> {
                                    currentScreen = AppScreen.Chat
                                }
                            }
                        },
                    )
                    AppScreen.Splash -> SplashScreen(
                        fullSessionAuth = savedAuth,
                        onNavigateToChat = { tabIndex ->
                            chatInitialTabIndex = tabIndex
                            currentScreen = AppScreen.Chat
                        },
                        onNavigateToPassword = {
                            chatInitialTabIndex = 0
                            currentScreen = AppScreen.Password
                        },
                    )
                    AppScreen.Password -> PasswordScreen(
                        nodeId = connectedNodeId.ifBlank { NodeAuthStore.loadStoredIdentity(this@MainActivity)?.nodeId.orEmpty() },
                        savedPassword = NodeAuthStore.loadStoredIdentity(this@MainActivity)?.password.orEmpty(),
                        savedDeviceAddress = meshDeviceAddress
                            ?: NodeAuthStore.loadStoredIdentity(this@MainActivity)?.deviceAddress,
                        onAuthenticated = { nodeId, password, deviceAddr ->
                            connectedNodeId = nodeId
                            meshDeviceAddress = deviceAddr
                            NodeAuthStore.save(applicationContext, nodeId, password, deviceAddr)
                            MeshService.setMaintainConnection(true)
                            MeshService.startIfNeeded(this@MainActivity)
                            // Первая успешная авторизация запускает VIP-таймер на 720 часов.
                            // При повторных входах функция идемпотентна — восстанавливает
                            // ранее сохранённый дедлайн (из SharedPrefs / Auto-Backup / sentinel).
                            VipAccessPreferences.ensureInitialTimerSeeded(applicationContext)
                            chatInitialTabIndex = 0
                            currentScreen = AppScreen.Chat
                        }
                    )
                    AppScreen.Chat -> ChatScreen(
                        nodeId = connectedNodeId,
                        deviceAddress = meshDeviceAddress,
                        profileAvatarPath = profileAvatarPath,
                        onProfileAvatarPathChange = { profileAvatarPath = it },
                        notificationIntentKey = notificationIntentKey.value,
                        initialBottomTabIndex = chatInitialTabIndex,
                        profileDirectDmRequestId = profileDirectDmRequestId,
                        profileDirectDmTarget = profileDirectDmTarget,
                        onConsumedProfileDirectDmRequest = { profileDirectDmTarget = null },
                        profileExportDeepLinkKey = profileExportDeepLinkKey.value,
                        profileExportDeepLinkText = profileExportDeepLinkText.value,
                        onConsumedProfileExportDeepLink = { profileExportDeepLinkText.value = null },
                        onOpenFirstLaunchInstruction = {
                            firstLaunchDismissDestination = FirstLaunchDismissDestination.Chat
                            currentScreen = AppScreen.FirstLaunchInstruction
                        },
                        onNodeIdChange = { connectedNodeId = it },
                        onDeviceAddressChange = { meshDeviceAddress = it },
                        onDeviceLinked = { nid, addr ->
                            NodeAuthStore.clearBleMacAfterUserDisconnect(applicationContext)
                            connectedNodeId = nid
                            meshDeviceAddress = addr
                            val auth = NodeAuthStore.load(applicationContext)
                                ?: NodeAuthStore.loadStoredIdentity(applicationContext)
                            val pw = auth?.password.orEmpty()
                            NodeAuthStore.save(applicationContext, nid, pw, addr)
                        },
                        onNavigateToPassword = {
                            MeshService.stopForLogout(this@MainActivity)
                            NodeAuthStore.clear(applicationContext)
                            connectedNodeId = ""
                            meshDeviceAddress = null
                            chatInitialTabIndex = 0
                            currentScreen = AppScreen.Password
                        },
                        onBleUnbind = {
                            val addr = meshDeviceAddress?.trim()?.takeIf { it.isNotEmpty() }
                            if (addr != null) {
                                val norm = MeshNodeSyncMemoryStore.normalizeKey(addr)
                                if (!MeshDeviceTransport.isTcpAddress(norm) &&
                                    !MeshDeviceTransport.isUsbAddress(norm)
                                ) {
                                    NodeAuthStore.rememberBleMacAfterUserDisconnect(applicationContext, addr)
                                }
                            }
                            val auth = NodeAuthStore.load(applicationContext)
                                ?: NodeAuthStore.loadStoredIdentity(applicationContext)
                            if (auth != null) {
                                NodeAuthStore.save(applicationContext, auth.nodeId, auth.password, null)
                            }
                            meshDeviceAddress = null
                        },
                        onOpenProfile = { currentScreen = AppScreen.NodeProfile },
                    )
                    AppScreen.NodeProfile -> NodeProfileAsNodeDetailScreen(
                        nodeId = connectedNodeId,
                        deviceAddress = meshDeviceAddress,
                        profileAvatarPath = profileAvatarPath,
                        onProfileAvatarPathChange = { profileAvatarPath = it },
                        onBack = { currentScreen = AppScreen.Chat },
                        onOpenDirectMessage = { node ->
                            profileDirectDmTarget = node
                            profileDirectDmRequestId++
                            currentScreen = AppScreen.Chat
                        },
                    )
                }
            }
        }
    }
}
