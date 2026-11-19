package dev.fluxpad.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.fluxpad.app.AppContainer
import dev.fluxpad.app.service.SessionService
import dev.fluxpad.app.ui.OnboardingScreen
import dev.fluxpad.app.ui.screens.AboutScreen
import dev.fluxpad.app.ui.screens.ConnectionScreen
import dev.fluxpad.app.ui.screens.DashboardScreen
import dev.fluxpad.app.ui.screens.DeckScreen
import dev.fluxpad.app.ui.screens.DiagnosticsScreen
import dev.fluxpad.app.ui.screens.KeyboardScreen
import dev.fluxpad.app.ui.screens.MediaScreen
import dev.fluxpad.app.ui.screens.PresentationScreen
import dev.fluxpad.app.ui.screens.PrivacyScreen
import dev.fluxpad.app.ui.screens.SettingsScreen
import dev.fluxpad.app.ui.screens.ShortcutsScreen
import dev.fluxpad.app.ui.screens.TouchpadScreen
import dev.fluxpad.core.data.AppSettings
import dev.fluxpad.core.design.DockItem
import dev.fluxpad.core.design.Flux
import dev.fluxpad.core.design.FluxBackground
import dev.fluxpad.core.design.FluxDock
import dev.fluxpad.core.design.FluxGlyph
import dev.fluxpad.core.design.FluxLogo
import dev.fluxpad.core.design.FluxTheme
import dev.fluxpad.core.design.FluxType
import dev.fluxpad.core.design.rememberReducedMotion
import dev.fluxpad.core.model.ConnectionState

@Composable
fun FluxpadApp(container: AppContainer) {
    val factory = remember(container) { FluxViewModelFactory(container) }
    val appVm: AppViewModel = viewModel(factory = factory)
    val deckVm: DeckViewModel = viewModel(factory = factory)
    val settingsState by appVm.settings.collectAsStateWithLifecycle()
    val settings = settingsState ?: AppSettings()

    val reducedMotion = rememberReducedMotion(settings.reducedMotion)

    FluxTheme(themeMode = settings.theme, reducedMotion = reducedMotion) {
        val feedback = rememberFluxFeedback(settingsState)
        val context = LocalContext.current
        val connectionState by appVm.connectionState.collectAsStateWithLifecycle()
        val view = LocalView.current

        LaunchedEffect(settings.keepScreenOn, connectionState.isConnected) {
            view.keepScreenOn = settings.keepScreenOn && connectionState.isConnected
        }

        LaunchedEffect(connectionState, settings.sessionServiceEnabled) {
            val state = connectionState
            if (state is ConnectionState.Connected && settings.sessionServiceEnabled) {
                SessionService.start(context, state.deviceName)
            } else {
                SessionService.stop(context)
            }
        }

        LaunchedEffect(
            settings.onboardingComplete,
            settings.autoReconnect,
            settings.lastDeviceAddress,
        ) {
            appVm.autoReconnectOnce()
        }

        FluxBackground {
            if (settingsState == null) {
                LoadingScreen()
            } else {
                RootNavigation(appVm, deckVm, settings, feedback)
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    val p = Flux.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FluxLogo(markSize = 46.dp, showWordmark = true)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(14.dp))
            CircularProgressIndicator(
                color = p.accent,
                strokeWidth = 2.dp,
                modifier = Modifier.padding(10.dp),
            )
            androidx.compose.material3.Text(
                "PREPARING HID STACK",
                style = FluxType.label,
                color = p.faint,
            )
        }
    }
}

@Composable
private fun RootNavigation(
    appVm: AppViewModel,
    deckVm: DeckViewModel,
    settings: AppSettings,
    feedback: FluxFeedback,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Routes.HOME
    val topLevel = setOf(
        Routes.HOME, Routes.TOUCHPAD, Routes.KEYBOARD, Routes.MEDIA, Routes.DECK,
    )
    val showDock = route in topLevel
    val reduced = dev.fluxpad.core.design.LocalReducedMotion.current
    val duration = if (reduced) 0 else 170

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = if (settings.onboardingComplete) Routes.HOME else Routes.ONBOARDING,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (showDock) 94.dp else 0.dp),
            enterTransition = {
                fadeIn(tween(duration)) + slideInHorizontally(tween(duration)) { it / 20 }
            },
            exitTransition = { fadeOut(tween(if (reduced) 0 else 90)) },
            popEnterTransition = {
                fadeIn(tween(duration)) + slideInHorizontally(tween(duration)) { -it / 20 }
            },
            popExitTransition = { fadeOut(tween(if (reduced) 0 else 90)) },
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(appVm, feedback) {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            composable(Routes.HOME) {
                DashboardScreen(appVm, feedback, navController)
            }
            composable(Routes.CONNECT) {
                ConnectionScreen(appVm, feedback, navController)
            }
            composable(Routes.TOUCHPAD) {
                TouchpadScreen(appVm, feedback, navController)
            }
            composable(Routes.KEYBOARD) {
                KeyboardScreen(appVm, feedback)
            }
            composable(Routes.MEDIA) {
                MediaScreen(appVm, feedback, navController)
            }
            composable(Routes.PRESENTATION) {
                PresentationScreen(appVm, feedback, navController)
            }
            composable(Routes.DECK) {
                DeckScreen(appVm, deckVm, feedback, navController)
            }
            composable(Routes.SHORTCUTS) {
                ShortcutsScreen(appVm, deckVm, feedback, navController)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(appVm, deckVm, feedback, navController)
            }
            composable(Routes.PRIVACY) {
                PrivacyScreen(appVm, deckVm, navController)
            }
            composable(Routes.DIAGNOSTICS) {
                DiagnosticsScreen(appVm, navController)
            }
            composable(Routes.ABOUT) {
                AboutScreen(navController)
            }
        }

        if (showDock) {
            FluxDock(
                items = listOf(
                    DockItem(Routes.HOME, "Home", FluxGlyph.HOME),
                    DockItem(Routes.TOUCHPAD, "Pad", FluxGlyph.TOUCHPAD),
                    DockItem(Routes.KEYBOARD, "Keys", FluxGlyph.KEYBOARD),
                    DockItem(Routes.MEDIA, "Media", FluxGlyph.PLAY),
                    DockItem(Routes.DECK, "Deck", FluxGlyph.DECK),
                ),
                selectedRoute = route,
                onSelect = { destination ->
                    if (destination != route) {
                        feedback.click()
                        navController.navigate(destination) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
            )
        }
    }
}
