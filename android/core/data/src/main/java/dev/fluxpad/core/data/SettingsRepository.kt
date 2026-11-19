package dev.fluxpad.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.fluxpad.core.model.ComputerDevice
import dev.fluxpad.core.model.GestureAction
import dev.fluxpad.core.model.GestureBindings
import dev.fluxpad.core.model.HapticLevel
import dev.fluxpad.core.model.HostOs
import dev.fluxpad.core.model.KeyboardLayout
import dev.fluxpad.core.model.ReducedMotionMode
import dev.fluxpad.core.model.SessionPreference
import dev.fluxpad.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "fluxpad_settings")

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val pointerSensitivity: Float = 1.0f,
    val scrollSensitivity: Float = 1.0f,
    val acceleration: Boolean = true,
    val naturalScroll: Boolean = true,
    val leftHanded: Boolean = false,
    val hapticLevel: HapticLevel = HapticLevel.STANDARD,
    val soundFeedback: Boolean = false,
    val theme: ThemeMode = ThemeMode.DARK,
    val keyboardLayout: KeyboardLayout = KeyboardLayout.US,
    val hostOs: HostOs = HostOs.WINDOWS,
    val autoReconnect: Boolean = true,
    val keepScreenOn: Boolean = true,
    val cursorFeedback: Boolean = true,
    val reducedMotion: ReducedMotionMode = ReducedMotionMode.SYSTEM,
    val sessionPreference: SessionPreference = SessionPreference.BLUETOOTH_FIRST,
    val lastDeviceAddress: String? = null,
    val lastDeviceName: String? = null,
    val recentDevices: List<ComputerDevice> = emptyList(),
    val sessionServiceEnabled: Boolean = true,
    val gesturesEnabled: Boolean = true,
    val twoFingerNavigation: Boolean = true,
    val twoFingerDown: GestureAction = GestureAction.DESKTOP,
    val twoFingerUp: GestureAction = GestureAction.PREVIOUS_APP,
    val fourFingerShortcuts: Boolean = true,
    val fourFingerUp: GestureAction = GestureAction.TASK_VIEW,
    val fourFingerDown: GestureAction = GestureAction.MINIMIZE,
    val fourFingerLeft: GestureAction = GestureAction.PREV_DESKTOP,
    val fourFingerRight: GestureAction = GestureAction.NEXT_DESKTOP,
    val gestureSensitivity: Float = 1f,
)

/** The multitouch bindings as one value, for the gesture settings UI. */
val AppSettings.gestureBindings: GestureBindings
    get() = GestureBindings(
        enabled = gesturesEnabled,
        twoFingerNavigation = twoFingerNavigation,
        twoDown = twoFingerDown,
        twoUp = twoFingerUp,
        fourFingerShortcuts = fourFingerShortcuts,
        fourUp = fourFingerUp,
        fourDown = fourFingerDown,
        fourLeft = fourFingerLeft,
        fourRight = fourFingerRight,
        sensitivity = gestureSensitivity,
    )

/** Applies edited gesture bindings back onto every persisted field. */
fun AppSettings.withGestures(transform: (GestureBindings) -> GestureBindings): AppSettings {
    val next = transform(gestureBindings)
    return copy(
        gesturesEnabled = next.enabled,
        twoFingerNavigation = next.twoFingerNavigation,
        twoFingerDown = next.twoDown,
        twoFingerUp = next.twoUp,
        fourFingerShortcuts = next.fourFingerShortcuts,
        fourFingerUp = next.fourUp,
        fourFingerDown = next.fourDown,
        fourFingerLeft = next.fourLeft,
        fourFingerRight = next.fourRight,
        gestureSensitivity = next.sensitivity,
    )
}

class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { it.toSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.ONBOARDING] = next.onboardingComplete
            prefs[Keys.POINTER] = next.pointerSensitivity
            prefs[Keys.SCROLL] = next.scrollSensitivity
            prefs[Keys.ACCEL] = next.acceleration
            prefs[Keys.NATURAL_SCROLL] = next.naturalScroll
            prefs[Keys.LEFT_HANDED] = next.leftHanded
            prefs[Keys.HAPTIC] = next.hapticLevel.name
            prefs[Keys.SOUND] = next.soundFeedback
            prefs[Keys.THEME] = next.theme.name
            prefs[Keys.LAYOUT] = next.keyboardLayout.name
            prefs[Keys.HOST] = next.hostOs.name
            prefs[Keys.AUTO_RECONNECT] = next.autoReconnect
            prefs[Keys.KEEP_SCREEN_ON] = next.keepScreenOn
            prefs[Keys.CURSOR_FEEDBACK] = next.cursorFeedback
            prefs[Keys.REDUCED_MOTION] = next.reducedMotion.name
            prefs[Keys.SESSION_PREF] = next.sessionPreference.name
            if (next.lastDeviceAddress != null) {
                prefs[Keys.LAST_ADDRESS] = next.lastDeviceAddress
            } else {
                prefs.remove(Keys.LAST_ADDRESS)
            }
            if (next.lastDeviceName != null) {
                prefs[Keys.LAST_NAME] = next.lastDeviceName
            } else {
                prefs.remove(Keys.LAST_NAME)
            }
            prefs[Keys.RECENT] = json.encodeToString(next.recentDevices)
            prefs[Keys.SERVICE] = next.sessionServiceEnabled
            prefs[Keys.GESTURES] = next.gesturesEnabled
            prefs[Keys.TWO_NAV] = next.twoFingerNavigation
            prefs[Keys.TWO_DOWN] = next.twoFingerDown.name
            prefs[Keys.TWO_UP] = next.twoFingerUp.name
            prefs[Keys.FOUR_SHORTCUTS] = next.fourFingerShortcuts
            prefs[Keys.FOUR_UP] = next.fourFingerUp.name
            prefs[Keys.FOUR_DOWN] = next.fourFingerDown.name
            prefs[Keys.FOUR_LEFT] = next.fourFingerLeft.name
            prefs[Keys.FOUR_RIGHT] = next.fourFingerRight.name
            prefs[Keys.GESTURE_SENSITIVITY] = next.gestureSensitivity
        }
    }

    suspend fun rememberDevice(device: ComputerDevice, now: Long = System.currentTimeMillis()) {
        update { current ->
            val updated = listOf(device.copy(lastConnectedAtMs = now))
                .plus(current.recentDevices.filter { it.address != device.address })
                .take(8)
            current.copy(
                lastDeviceAddress = device.address,
                lastDeviceName = device.name,
                recentDevices = updated,
            )
        }
    }

    suspend fun completeOnboarding() = update { it.copy(onboardingComplete = true) }

    private fun Preferences.toSettings(): AppSettings {
        val recent = this[Keys.RECENT]
        return AppSettings(
            onboardingComplete = this[Keys.ONBOARDING] ?: false,
            pointerSensitivity = this[Keys.POINTER] ?: 1.0f,
            scrollSensitivity = this[Keys.SCROLL] ?: 1.0f,
            acceleration = this[Keys.ACCEL] ?: true,
            naturalScroll = this[Keys.NATURAL] ?: true,
            leftHanded = this[Keys.LEFT_HANDED] ?: false,
            hapticLevel = this[Keys.HAPTIC]?.let { runCatching { HapticLevel.valueOf(it) }.getOrNull() }
                ?: HapticLevel.STANDARD,
            soundFeedback = this[Keys.SOUND] ?: false,
            theme = this[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
            keyboardLayout = this[Keys.LAYOUT]?.let { runCatching { KeyboardLayout.valueOf(it) }.getOrNull() }
                ?: KeyboardLayout.US,
            hostOs = this[Keys.HOST]?.let { runCatching { HostOs.valueOf(it) }.getOrNull() } ?: HostOs.WINDOWS,
            autoReconnect = this[Keys.AUTO_RECONNECT] ?: true,
            keepScreenOn = this[Keys.KEEP_SCREEN_ON] ?: true,
            cursorFeedback = this[Keys.CURSOR_FEEDBACK] ?: true,
            reducedMotion = this[Keys.REDUCED_MOTION]?.let {
                runCatching { ReducedMotionMode.valueOf(it) }.getOrNull()
            } ?: ReducedMotionMode.SYSTEM,
            sessionPreference = this[Keys.SESSION_PREF]?.let {
                runCatching { SessionPreference.valueOf(it) }.getOrNull()
            } ?: SessionPreference.BLUETOOTH_FIRST,
            lastDeviceAddress = this[Keys.LAST_ADDRESS],
            lastDeviceName = this[Keys.LAST_NAME],
            recentDevices = recent?.let {
                runCatching { json.decodeFromString<List<ComputerDevice>>(it) }.getOrNull()
            } ?: emptyList(),
            sessionServiceEnabled = this[Keys.SERVICE] ?: true,
            gesturesEnabled = this[Keys.GESTURES] ?: true,
            twoFingerNavigation = this[Keys.TWO_NAV] ?: true,
            twoFingerDown = this[Keys.TWO_DOWN].toGestureAction(GestureAction.DESKTOP),
            twoFingerUp = this[Keys.TWO_UP].toGestureAction(GestureAction.PREVIOUS_APP),
            fourFingerShortcuts = this[Keys.FOUR_SHORTCUTS] ?: true,
            fourFingerUp = this[Keys.FOUR_UP].toGestureAction(GestureAction.TASK_VIEW),
            fourFingerDown = this[Keys.FOUR_DOWN].toGestureAction(GestureAction.MINIMIZE),
            fourFingerLeft = this[Keys.FOUR_LEFT].toGestureAction(GestureAction.PREV_DESKTOP),
            fourFingerRight = this[Keys.FOUR_RIGHT].toGestureAction(GestureAction.NEXT_DESKTOP),
            gestureSensitivity = this[Keys.GESTURE_SENSITIVITY] ?: 1f,
        )
    }

    private fun String?.toGestureAction(fallback: GestureAction): GestureAction =
        this?.let { runCatching { GestureAction.valueOf(it) }.getOrNull() } ?: fallback

    private object Keys {
        val ONBOARDING = booleanPreferencesKey("onboarding_complete")
        val POINTER = floatPreferencesKey("pointer_sensitivity")
        val SCROLL = floatPreferencesKey("scroll_sensitivity")
        val ACCEL = booleanPreferencesKey("acceleration")
        val NATURAL = booleanPreferencesKey("natural_scroll")
        val NATURAL_SCROLL = NATURAL
        val LEFT_HANDED = booleanPreferencesKey("left_handed")
        val HAPTIC = stringPreferencesKey("haptic_level")
        val SOUND = booleanPreferencesKey("sound_feedback")
        val THEME = stringPreferencesKey("theme")
        val LAYOUT = stringPreferencesKey("keyboard_layout")
        val HOST = stringPreferencesKey("host_os")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val CURSOR_FEEDBACK = booleanPreferencesKey("cursor_feedback")
        val REDUCED_MOTION = stringPreferencesKey("reduced_motion")
        val SESSION_PREF = stringPreferencesKey("session_preference")
        val LAST_ADDRESS = stringPreferencesKey("last_device_address")
        val LAST_NAME = stringPreferencesKey("last_device_name")
        val RECENT = stringPreferencesKey("recent_devices")
        val SERVICE = booleanPreferencesKey("session_service")
        val GESTURES = booleanPreferencesKey("gestures_enabled")
        val TWO_NAV = booleanPreferencesKey("two_finger_navigation")
        val TWO_DOWN = stringPreferencesKey("two_finger_down")
        val TWO_UP = stringPreferencesKey("two_finger_up")
        val FOUR_SHORTCUTS = booleanPreferencesKey("four_finger_shortcuts")
        val FOUR_UP = stringPreferencesKey("four_finger_up")
        val FOUR_DOWN = stringPreferencesKey("four_finger_down")
        val FOUR_LEFT = stringPreferencesKey("four_finger_left")
        val FOUR_RIGHT = stringPreferencesKey("four_finger_right")
        val GESTURE_SENSITIVITY = floatPreferencesKey("gesture_sensitivity")
    }
}
