package dev.fluxpad.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fluxpad.app.AppContainer
import dev.fluxpad.core.model.DeckAction
import dev.fluxpad.core.model.DeckButton
import dev.fluxpad.core.model.DeckProfile
import dev.fluxpad.core.model.HostOs
import dev.fluxpad.core.model.KeyChord
import dev.fluxpad.core.model.SavedShortcut
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class DeckViewModel(private val container: AppContainer) : ViewModel() {

    private val writeMutex = Mutex()

    val profiles: StateFlow<List<DeckProfile>> = container.deckRepository.profiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val activeProfileId: StateFlow<String> = container.deckRepository.activeProfileId.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "general",
    )

    val shortcuts: StateFlow<List<SavedShortcut>> = container.deckRepository.shortcuts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    init {
        viewModelScope.launch {
            val host = container.settingsRepository.settings.first().hostOs
            container.deckRepository.ensureSeeded(host)
        }
    }

    fun setActive(id: String) {
        viewModelScope.launch { container.deckRepository.setActiveProfile(id) }
    }

    fun addProfile(name: String, onCreated: (String) -> Unit = {}) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            writeMutex.withLock {
                val id = "custom-" + UUID.randomUUID().toString().take(8)
                val next = profiles.value + DeckProfile(
                    id = id,
                    name = trimmed,
                    description = "Custom deck",
                    buttons = emptyList(),
                    builtIn = false,
                )
                container.deckRepository.saveProfiles(next)
                container.deckRepository.setActiveProfile(id)
                onCreated(id)
            }
        }
    }

    fun renameProfile(profileId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            writeMutex.withLock {
                container.deckRepository.saveProfiles(
                    profiles.value.map { if (it.id == profileId) it.copy(name = trimmed) else it },
                )
            }
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            writeMutex.withLock {
                val target = profiles.value.firstOrNull { it.id == profileId } ?: return@withLock
                if (target.builtIn || profiles.value.size <= 1) return@withLock
                val next = profiles.value.filterNot { it.id == profileId }
                container.deckRepository.saveProfiles(next)
                if (activeProfileId.value == profileId) {
                    container.deckRepository.setActiveProfile(next.first().id)
                }
            }
        }
    }

    fun upsertButton(profileId: String, button: DeckButton) {
        viewModelScope.launch {
            writeMutex.withLock {
                val next = profiles.value.map { profile ->
                    if (profile.id != profileId) {
                        profile
                    } else {
                        val existing = profile.buttons.any { it.id == button.id }
                        profile.copy(
                            buttons = if (existing) {
                                profile.buttons.map { if (it.id == button.id) button else it }
                            } else {
                                profile.buttons + button
                            },
                        )
                    }
                }
                container.deckRepository.saveProfiles(next)
            }
        }
    }

    fun removeButton(profileId: String, buttonId: String) {
        viewModelScope.launch {
            writeMutex.withLock {
                container.deckRepository.saveProfiles(
                    profiles.value.map { profile ->
                        if (profile.id == profileId) {
                            profile.copy(buttons = profile.buttons.filterNot { it.id == buttonId })
                        } else {
                            profile
                        }
                    },
                )
            }
        }
    }

    fun moveButton(profileId: String, buttonId: String, delta: Int) {
        viewModelScope.launch {
            writeMutex.withLock {
                container.deckRepository.saveProfiles(
                    profiles.value.map { profile ->
                        if (profile.id != profileId) return@map profile
                        val index = profile.buttons.indexOfFirst { it.id == buttonId }
                        if (index < 0) return@map profile
                        val target = (index + delta).coerceIn(0, profile.buttons.lastIndex)
                        if (target == index) return@map profile
                        val mutable = profile.buttons.toMutableList()
                        val item = mutable.removeAt(index)
                        mutable.add(target, item)
                        profile.copy(buttons = mutable)
                    },
                )
            }
        }
    }

    fun saveShortcut(name: String, chord: KeyChord, existingId: String? = null) {
        val trimmed = name.trim().ifEmpty { chord.label() }
        viewModelScope.launch {
            writeMutex.withLock {
                val current = shortcuts.value
                val id = existingId ?: "sc-" + UUID.randomUUID().toString().take(8)
                val entry = SavedShortcut(id, trimmed, chord)
                val next = if (current.any { it.id == id }) {
                    current.map { if (it.id == id) entry else it }
                } else {
                    current + entry
                }
                container.deckRepository.saveShortcuts(next)
            }
        }
    }

    fun deleteShortcut(id: String) {
        viewModelScope.launch {
            writeMutex.withLock {
                container.deckRepository.saveShortcuts(shortcuts.value.filterNot { it.id == id })
            }
        }
    }

    fun resetDecks(hostOs: HostOs) {
        viewModelScope.launch {
            writeMutex.withLock { container.deckRepository.reset(hostOs) }
        }
    }

    fun newButtonId(): String = "b-" + UUID.randomUUID().toString().take(8)

    fun actionIsMedia(action: DeckAction): Boolean = action is DeckAction.Media
}
