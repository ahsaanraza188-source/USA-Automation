package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.OAuthTokenStore
import com.example.data.network.DiagnosticsNetworkService
import com.example.data.network.GoogleApisClient
import com.example.data.network.GoogleOAuthManager
import com.example.data.repository.VirtualOsRepository
import com.example.domain.VirtualAppId
import com.example.ui.auth.GoogleAuthHelper
import com.example.ui.auth.LockAuthScreen
import com.example.ui.apps.BrowserSessionManager
import com.example.ui.apps.CalculatorApp
import com.example.ui.apps.CalendarApp
import com.example.ui.apps.CameraGalleryApp
import com.example.ui.apps.ChromeBrowserApp
import com.example.ui.apps.ClockApp
import com.example.ui.apps.ContactsApp
import com.example.ui.apps.DiagnosticsApp
import com.example.ui.apps.FilesApp
import com.example.ui.apps.GmailApp
import com.example.ui.apps.MapsApp
import com.example.ui.apps.MessagesApp
import com.example.ui.apps.NotesApp
import com.example.ui.apps.PlayDiscoveryApp
import com.example.ui.apps.SearchApp
import com.example.ui.apps.SettingsApp
import com.example.ui.apps.YouTubeApp
import com.example.ui.shell.AppDrawerOverlay
import com.example.ui.shell.DiagnosticsViewModel
import com.example.ui.shell.FloatingControlPill
import com.example.ui.shell.GoogleServicesViewModel
import com.example.ui.shell.OsShellViewModel
import com.example.ui.shell.QuickSettingsShadeOverlay
import com.example.ui.shell.RecentsSwitcherOverlay
import com.example.ui.shell.UiState
import com.example.ui.shell.VirtualHomeScreen
import com.example.ui.shell.VirtualNavigationBar
import com.example.ui.shell.VirtualStatusBar
import com.example.ui.theme.OsPaletteKey
import com.example.ui.theme.UsaVirtualOsTheme
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

  private lateinit var oauthTokenStore: OAuthTokenStore
  private lateinit var oauthManager: GoogleOAuthManager

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    oauthTokenStore = OAuthTokenStore(applicationContext)
    oauthManager = GoogleOAuthManager(applicationContext, oauthTokenStore)
    handleOAuthRedirectIfPresent(intent)

    setContent {
      AppNavigation(
        oauthTokenStore = oauthTokenStore,
        oauthManager = oauthManager,
      )
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleOAuthRedirectIfPresent(intent)
  }

  private fun handleOAuthRedirectIfPresent(intent: Intent?) {
    val data = intent?.data ?: return
    if (data.scheme == "com.aistudio.usavirtualos.mrxpkv" && data.path == "/oauth2redirect") {
      lifecycleScope.launch { oauthManager.handleRedirectIntent(data) }
    }
  }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(
  oauthTokenStore: OAuthTokenStore,
  oauthManager: GoogleOAuthManager,
  auth: FirebaseAuth? = null,
) {
  val context = LocalContext.current
  val isFirebaseConfigured =
    remember(context, auth) {
      auth != null ||
        FirebaseApp.getApps(context).isNotEmpty() ||
        FirebaseApp.initializeApp(context) != null
    }
  if (!isFirebaseConfigured) {
    UsaVirtualOsTheme {
      FirebaseConfigMissingScreen()
    }
    return
  }

  val resolvedAuth = remember(auth) { auth ?: Firebase.auth }
  val currentUser by
    resolvedAuth.authStateFlow().collectAsStateWithLifecycle(initialValue = resolvedAuth.currentUser)

  if (currentUser == null) {
    UsaVirtualOsTheme {
      LockAuthScreen(onAuthSuccess = {})
    }
  } else {
    val user = currentUser!!
    key(user.uid) {
      VirtualOsRoot(
        currentUserId = user.uid,
        userEmail = user.email,
        userName = user.displayName,
        oauthTokenStore = oauthTokenStore,
        oauthManager = oauthManager,
      )
    }
  }
}

@Composable
private fun FirebaseConfigMissingScreen() {
  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background,
  ) {
    Box(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Card(modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp)) {
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = "Firebase Setup Required",
            style = MaterialTheme.typography.titleLarge,
          )
          Text(
            text =
              "This build is missing app/google-services.json, so Google Sign-In and Firestore cloud sync cannot initialize.",
            style = MaterialTheme.typography.bodyMedium,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text =
              "To enable sign-in in GitHub Actions builds, copy app/google-services.json from AI Studio into a GitHub repository secret named GOOGLE_SERVICES_JSON (and optionally debug.keystore.base64 into DEBUG_KEYSTORE_BASE64), then re-run Build Android APK.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
fun VirtualOsRoot(
  currentUserId: String,
  userEmail: String?,
  userName: String?,
  oauthTokenStore: OAuthTokenStore,
  oauthManager: GoogleOAuthManager,
) {
  val context = LocalContext.current
  val appContext = context.applicationContext
  val lifecycleOwner = LocalLifecycleOwner.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val saveableStateHolder = rememberSaveableStateHolder()

  val browserSessionManager: BrowserSessionManager =
    viewModel(
      key = "browser_$currentUserId",
      factory =
        viewModelFactory {
          initializer {
            val dbId = appContext.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(dbId)
            BrowserSessionManager(appContext, currentUserId, VirtualOsRepository(db))
          }
        },
    )

  DisposableEffect(browserSessionManager, lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
        browserSessionManager.persistImmediately()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      browserSessionManager.persistImmediately()
    }
  }

  val shellViewModel: OsShellViewModel =
    viewModel(
      key = currentUserId,
      factory =
        viewModelFactory {
          initializer {
            val dbId = appContext.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(dbId)
            OsShellViewModel(VirtualOsRepository(db), currentUserId)
          }
        },
    )

  val diagnosticsViewModel: DiagnosticsViewModel =
    viewModel(
      key = "diag_$currentUserId",
      factory =
        viewModelFactory {
          initializer { DiagnosticsViewModel(DiagnosticsNetworkService(appContext)) }
        },
    )

  val googleServicesViewModel: GoogleServicesViewModel =
    viewModel(
      key = "gsvc_$currentUserId",
      factory =
        viewModelFactory {
          initializer {
            GoogleServicesViewModel(oauthTokenStore, oauthManager, GoogleApisClient())
          }
        },
    )

  val shellState by shellViewModel.shellState.collectAsStateWithLifecycle()
  val preferences by shellViewModel.preferences.collectAsStateWithLifecycle()
  val clockTick by shellViewModel.clockTick.collectAsStateWithLifecycle()
  val notesState by shellViewModel.notesState.collectAsStateWithLifecycle()
  val contactsState by shellViewModel.contactsState.collectAsStateWithLifecycle()
  val threadsState by shellViewModel.threadsState.collectAsStateWithLifecycle()
  val activeMessages by shellViewModel.activeThreadMessages.collectAsStateWithLifecycle()
  val eventsState by shellViewModel.eventsState.collectAsStateWithLifecycle()
  val alarmsState by shellViewModel.alarmsState.collectAsStateWithLifecycle()
  val filesState by shellViewModel.filesState.collectAsStateWithLifecycle()

  val diagState by diagnosticsViewModel.uiState.collectAsStateWithLifecycle()
  val tokenState by googleServicesViewModel.tokenState.collectAsStateWithLifecycle()
  val googleState by googleServicesViewModel.uiState.collectAsStateWithLifecycle()

  val paletteKey =
    remember(preferences.themePalette) {
      runCatching { OsPaletteKey.valueOf(preferences.themePalette) }.getOrDefault(OsPaletteKey.COBALT)
    }

  val usTime = remember(clockTick, preferences.usTimezoneId) {
    shellViewModel.formatUsTime(preferences.usTimezoneId)
  }
  val usDate = remember(clockTick, preferences.usTimezoneId) {
    shellViewModel.formatUsDate(preferences.usTimezoneId)
  }

  val nextEvent =
    remember(eventsState) { (eventsState as? UiState.Success)?.data?.firstOrNull() }

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(shellState.statusMessage) {
    shellState.statusMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      shellViewModel.clearStatusMessage()
    }
  }

  val handleBackAction: () -> Unit = {
    val isChromeForeground =
      shellState.activeApp == VirtualAppId.CHROME &&
        !shellState.isShadeOpen &&
        !shellState.isRecentsOpen &&
        !shellState.isDrawerOpen
    if (isChromeForeground && browserSessionManager.goBackInActiveTab()) {
      // Handled inside active Chrome tab history
    } else {
      if (shellState.activeApp == VirtualAppId.CHROME) {
        browserSessionManager.persistImmediately()
      }
      shellViewModel.handleVirtualBack()
    }
  }

  BackHandler(
    enabled =
      shellState.isShadeOpen ||
        shellState.isRecentsOpen ||
        shellState.isDrawerOpen ||
        shellState.activeApp != null
  ) {
    handleBackAction()
  }

  UsaVirtualOsTheme(paletteKey = paletteKey, darkTheme = preferences.darkMode) {
    Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      containerColor = MaterialTheme.colorScheme.background,
    ) { _ ->
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .alpha(shellState.brightnessLevel)
      ) {
        Column(modifier = Modifier.fillMaxSize()) {
          VirtualStatusBar(
            timeFormatted = usTime,
            timezoneId = preferences.usTimezoneId,
            wifiEnabled = shellState.wifiEnabled,
            dndEnabled = shellState.dndEnabled,
            ipReport = diagState.ipReport,
            onToggleShade = { shellViewModel.toggleShade() },
          )

          var hasOpenedChrome by remember(currentUserId) { mutableStateOf(false) }
          LaunchedEffect(shellState.activeApp) {
            if (shellState.activeApp == VirtualAppId.CHROME) {
              hasOpenedChrome = true
            }
          }
          val isChromeActive = shellState.activeApp == VirtualAppId.CHROME
          val chromeAlpha by
            animateFloatAsState(
              targetValue = if (isChromeActive) 1f else 0f,
              animationSpec = tween(durationMillis = if (isChromeActive) 180 else 120),
              label = "chrome_layer_alpha",
            )
          val chromeScale by
            animateFloatAsState(
              targetValue = if (isChromeActive) 1f else 0.96f,
              animationSpec = spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = Spring.DampingRatioNoBouncy,
              ),
              label = "chrome_layer_scale",
            )

          Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (hasOpenedChrome) {
              Box(
                modifier =
                  Modifier.fillMaxSize()
                    .zIndex(if (isChromeActive) 1f else -1f)
                    .graphicsLayer {
                      alpha = chromeAlpha
                      scaleX = chromeScale
                      scaleY = chromeScale
                    }
              ) {
                ChromeBrowserApp(
                  sessionManager = browserSessionManager,
                  isActive = isChromeActive,
                  initialUrl = shellState.pendingBrowserUrl,
                  onConsumeInitialUrl = { shellViewModel.consumePendingBrowserUrl() },
                  onCloseApp = shellViewModel::goHome,
                )
              }
            }

            AnimatedContent(
              targetState = shellState.activeApp,
              transitionSpec = {
                (scaleIn(
                  initialScale = 0.94f,
                  animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy,
                  ),
                ) + fadeIn(animationSpec = tween(200))) togetherWith
                  fadeOut(animationSpec = tween(150))
              },
              modifier = Modifier.fillMaxSize().zIndex(0f),
              label = "virtual_app_transition",
            ) { active ->
              saveableStateHolder.SaveableStateProvider(key = active?.id ?: "virtual_home") {
              when (active) {
                null ->
                  VirtualHomeScreen(
                    paletteKey = paletteKey,
                    dateFormatted = usDate,
                    timeFormatted = usTime,
                    nextEvent = nextEvent,
                    ipReport = diagState.ipReport,
                    pinnedAppIds = preferences.pinnedAppIds,
                    onLaunchApp = shellViewModel::launchApp,
                    onOpenDrawer = { shellViewModel.toggleDrawer(true) },
                    onOpenSearch = { shellViewModel.openSearchWithQuery("") },
                  )

                VirtualAppId.CALCULATOR -> CalculatorApp(onCloseApp = shellViewModel::goHome)
                VirtualAppId.CHROME -> Box(modifier = Modifier.fillMaxSize())
                VirtualAppId.SEARCH ->
                  SearchApp(
                    initialQuery = shellState.pendingSearchQuery,
                    notesState = notesState,
                    contactsState = contactsState,
                    filesState = filesState,
                    onLaunchApp = shellViewModel::launchApp,
                    onSearchWeb = shellViewModel::openUrlInChrome,
                    onCloseApp = shellViewModel::goHome,
                  )
                VirtualAppId.GMAIL ->
                  GmailApp(
                    oauthClientId = preferences.oauthClientId,
                    tokenState = tokenState,
                    googleState = googleState,
                    onConnectOAuth = { googleServicesViewModel.connectOAuth(preferences.oauthClientId) },
                    onRefreshInbox = googleServicesViewModel::refreshGmail,
                    onSendEmail = googleServicesViewModel::sendGmail,
                    onOpenSettings = { shellViewModel.launchApp(VirtualAppId.SETTINGS) },
                    onCloseApp = shellViewModel::goHome,
                  )
                VirtualAppId.YOUTUBE ->
                  YouTubeApp(
                    oauthClientId = preferences.oauthClientId,
                    tokenState = tokenState,
                    googleState = googleState,
                    onConnectOAuth = { googleServicesViewModel.connectOAuth(preferences.oauthClientId) },
                    onSearchYouTube = googleServicesViewModel::searchYouTube,
                    onLoadLiked = googleServicesViewModel::loadLikedYouTube,
                    onOpenVideoInBrowser = shellViewModel::openUrlInChrome,
                    onOpenSettings = { shellViewModel.launchApp(VirtualAppId.SETTINGS) },
                    onCloseApp = shellViewModel::goHome,
                  )
                VirtualAppId.MAPS ->
                  MapsApp(
                    googleState = googleState,
                    onSearchPlaces = googleServicesViewModel::searchUsPlaces,
                    onCloseApp = shellViewModel::goHome,
                  )
                VirtualAppId.CONTACTS ->
                  ContactsApp(
                    contactsState = contactsState,
                    onSaveContact = shellViewModel::saveContact,
                    onDeleteContact = shellViewModel::deleteContact,
                    onMessageContact = shellViewModel::startMessageToContact,
                    onCloseApp = shellViewModel::goHome,
                  )
                VirtualAppId.MESSAGES ->
                  MessagesApp(
                    threadsState = threadsState,
                    activeThreadId = shellState.activeThreadId,
                    activeMessages = activeMessages,
                    onSelectThread = shellViewModel::setActiveThread,
                    onCreateThread = shellViewModel::createThread,
                    onSendMessage = shellViewModel::sendMessage,
                    onDeleteThread = shellViewModel::deleteThread,
                    onCloseApp = shellViewModel::goHome,
                  )
                VirtualAppId.CAMERA_GALLERY -> CameraGalleryApp(onCloseApp = shellViewModel::goHome)
                VirtualAppId.FILES ->
                  FilesApp(
                    filesState = filesState,
                    onSaveFile = shellViewModel::saveFileRecord,
                    onDeleteFile = shellViewModel::deleteFileRecord,
                    onCloseApp = shellViewModel::goHome,
                  )
                VirtualAppId.CLOCK ->
                  ClockApp(
                    alarmsState = alarmsState,
                    usTimezoneId = preferences.usTimezoneId,
                    onSaveAlarm = shellViewModel::saveAlarm,
                    onDeleteAlarm = shellViewModel::deleteAlarm,
                    onCloseApp = shellViewModel::goHome,
                  )
                VirtualAppId.CALENDAR ->
                  CalendarApp(
                    eventsState = eventsState,
                    usTimezoneId = preferences.usTimezoneId,
                    onSaveEvent = shellViewModel::saveEvent,
                    onDeleteEvent = shellViewModel::deleteEvent,
                    onCloseApp = shellViewModel::goHome,
                  )
                VirtualAppId.NOTES ->
                  NotesApp(
                    notesState = notesState,
                    darkTheme = preferences.darkMode,
                    onSaveNote = shellViewModel::saveNote,
                    onDeleteNote = shellViewModel::deleteNote,
                    onCloseApp = shellViewModel::goHome,
                  )
                VirtualAppId.PLAY_DISCOVERY ->
                  PlayDiscoveryApp(
                    pinnedAppIds = preferences.pinnedAppIds,
                    onTogglePin = shellViewModel::togglePinToHome,
                    onLaunchApp = shellViewModel::launchApp,
                    onCloseApp = shellViewModel::goHome,
                  )
                VirtualAppId.SETTINGS ->
                  SettingsApp(
                    userEmail = userEmail,
                    userName = userName,
                    preferences = preferences,
                    tokenState = tokenState,
                    googleFeedback = googleState.oauthFeedback,
                    onUpdatePreferences = shellViewModel::updatePreferences,
                    onConnectOAuth = { googleServicesViewModel.connectOAuth(preferences.oauthClientId) },
                    onDisconnectOAuth = googleServicesViewModel::disconnectOAuth,
                    onSignOut = { GoogleAuthHelper.signOut(credentialManager, scope) },
                    onOpenDiagnostics = { shellViewModel.launchApp(VirtualAppId.DIAGNOSTICS) },
                    onCloseApp = shellViewModel::goHome,
                  )
                VirtualAppId.DIAGNOSTICS ->
                  DiagnosticsApp(
                    diagState = diagState,
                    usTimezoneId = preferences.usTimezoneId,
                    oauthClientId = preferences.oauthClientId,
                    oauthConnected = tokenState.isConnected,
                    isSignedIn = true,
                    onRefreshIp = diagnosticsViewModel::refreshPublicIp,
                    onRunFifteenTests = { hasCam ->
                      diagnosticsViewModel.runFifteenTests(
                        usTimezoneId = preferences.usTimezoneId,
                        oauthClientId = preferences.oauthClientId,
                        oauthConnected = tokenState.isConnected,
                        isSignedIn = true,
                        cloudHealthCheck = shellViewModel::checkCloudHealth,
                        hasCameraHardware = hasCam,
                      )
                    },
                    onCloseApp = shellViewModel::goHome,
                  )
              }
              }
            }

            AppDrawerOverlay(
              visible = shellState.isDrawerOpen,
              onDismiss = { shellViewModel.toggleDrawer(false) },
              onLaunchApp = shellViewModel::launchApp,
            )

            RecentsSwitcherOverlay(
              visible = shellState.isRecentsOpen,
              recentsStack = shellState.recentsStack,
              onResumeApp = shellViewModel::launchApp,
              onDismissApp = { appId ->
                if (appId == VirtualAppId.CHROME) {
                  hasOpenedChrome = false
                  browserSessionManager.releaseInMemoryViewsKeepingSession()
                }
                shellViewModel.dismissRecentApp(appId)
              },
              onClearAll = {
                hasOpenedChrome = false
                browserSessionManager.releaseInMemoryViewsKeepingSession()
                shellViewModel.clearAllRecents()
              },
              onCloseOverlay = { shellViewModel.toggleRecents(false) },
            )
          }

          VirtualNavigationBar(
            recentsCount = shellState.recentsStack.size,
            onBack = handleBackAction,
            onHome = {
              if (shellState.activeApp == VirtualAppId.CHROME) {
                browserSessionManager.persistImmediately()
              }
              shellViewModel.goHome()
            },
            onRecents = {
              if (shellState.activeApp == VirtualAppId.CHROME) {
                browserSessionManager.persistImmediately()
              }
              shellViewModel.toggleRecents()
            },
          )
        }

        QuickSettingsShadeOverlay(
          visible = shellState.isShadeOpen,
          dateFormatted = usDate,
          shellState = shellState,
          darkMode = preferences.darkMode,
          paletteKey = paletteKey,
          ipReport = diagState.ipReport,
          onDismiss = { shellViewModel.toggleShade(false) },
          onToggleWifi = shellViewModel::toggleWifi,
          onToggleBluetooth = shellViewModel::toggleBluetooth,
          onToggleDnd = shellViewModel::toggleDnd,
          onToggleDarkMode = {
            shellViewModel.updatePreferences { it.copy(darkMode = !it.darkMode) }
          },
          onSelectPalette = { p ->
            shellViewModel.updatePreferences { it.copy(themePalette = p.name) }
          },
          onBrightnessChange = shellViewModel::setBrightness,
          onOpenApp = shellViewModel::launchApp,
        )

        FloatingControlPill(
          onBack = handleBackAction,
          onHome = {
            if (shellState.activeApp == VirtualAppId.CHROME) {
              browserSessionManager.persistImmediately()
            }
            shellViewModel.goHome()
          },
          onStatusMessage = shellViewModel::showStatus,
          modifier = Modifier.align(Alignment.CenterEnd).zIndex(5f),
        )
      }
    }
  }
}
