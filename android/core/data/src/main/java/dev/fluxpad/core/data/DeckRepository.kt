package dev.fluxpad.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.fluxpad.core.model.DeckPresets
import dev.fluxpad.core.model.DeckProfile
import dev.fluxpad.core.model.DefaultShortcuts
import dev.fluxpad.core.model.HostOs
import dev.fluxpad.core.model.SavedShortcut
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.deckDataStore: DataStore<Preferences> by preferencesDataStore(name = "fluxpad_decks")

/**
 * Deck profiles and custom shortcuts. Built-in decks are seeded once and then
 * persist just like user decks; nothing is regenerated behind the user's back.
 */
class DeckRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val profiles: Flow<List<DeckProfile>> = context.deckDataStore.data.map { prefs ->
        prefs[Keys.PROFILES]?.let { raw ->
            runCatching { json.decodeFromString<List<DeckProfile>>(raw) }.getOrNull()
        } ?: emptyList()
    }

    val activeProfileId: Flow<String> = context.deckDataStore.data.map { prefs ->
        prefs[Keys.ACTIVE] ?: "general"
    }

    val shortcuts: Flow<List<SavedShortcut>> = context.deckDataStore.data.map { prefs ->
        prefs[Keys.SHORTCUTS]?.let { raw ->
            runCatching { json.decodeFromString<List<SavedShortcut>>(raw) }.getOrNull()
        } ?: DefaultShortcuts.all
    }

    suspend fun ensureSeeded(hostOs: HostOs) {
        context.deckDataStore.edit { prefs ->
            if (prefs[Keys.PROFILES] == null) {
                prefs[Keys.PROFILES] = json.encodeToString(DeckPresets.all(hostOs))
            }
            if (prefs[Keys.ACTIVE] == null) prefs[Keys.ACTIVE] = "general"
            if (prefs[Keys.SHORTCUTS] == null) prefs[Keys.SHORTCUTS] = json.encodeToString(DefaultShortcuts.all)
        }
    }

    suspend fun saveProfiles(profiles: List<DeckProfile>) {
        context.deckDataStore.edit { it[Keys.PROFILES] = json.encodeToString(profiles) }
    }

    suspend fun setActiveProfile(id: String) {
        context.deckDataStore.edit { it[Keys.ACTIVE] = id }
    }

    suspend fun saveShortcuts(shortcuts: List<SavedShortcut>) {
        context.deckDataStore.edit { it[Keys.SHORTCUTS] = json.encodeToString(shortcuts) }
    }

    suspend fun reset(hostOs: HostOs) {
        context.deckDataStore.edit { prefs ->
            prefs[Keys.PROFILES] = json.encodeToString(DeckPresets.all(hostOs))
            prefs[Keys.ACTIVE] = "general"
            prefs[Keys.SHORTCUTS] = json.encodeToString(DefaultShortcuts.all)
        }
    }

    private object Keys {
        val PROFILES = stringPreferencesKey("profiles_json")
        val ACTIVE = stringPreferencesKey("active_profile")
        val SHORTCUTS = stringPreferencesKey("shortcuts_json")
    }
}
