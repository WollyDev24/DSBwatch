package dev.wolly.dsbwatch.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.wolly.dsbwatch.api.DSBMobileAPI
import dev.wolly.dsbwatch.data.DataStoreManager
import dev.wolly.dsbwatch.data.SubstitutionEntry
import dev.wolly.dsbwatch.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@Immutable
sealed class UiState {
    @Immutable object Idle : UiState()
    @Immutable object Loading : UiState()
    @Immutable data class Success(val entries: List<SubstitutionEntry>, val isDemo: Boolean = false) : UiState()
    @Immutable data class Error(val message: String) : UiState()
    @Immutable object NeedsLogin : UiState()
    @Immutable data class SelectingClass(val classes: List<String>, val u: String, val p: String) : UiState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStoreManager = DataStoreManager(application)
    private val gson = Gson()
    
    private var isDemoMode = false
    
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    val isRoomFirst: StateFlow<Boolean> = dataStoreManager.swapDataFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val sortByPeriod: StateFlow<Boolean> = dataStoreManager.sortPeriodFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isDynamicColorEnabled: StateFlow<Boolean> = dataStoreManager.dynamicColorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val themeIndex: StateFlow<Int> = dataStoreManager.themeIndexFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _archive = MutableStateFlow<List<SubstitutionEntry>>(emptyList())
    val archive: StateFlow<List<SubstitutionEntry>> = _archive

    private var lastSuccessEntries: List<SubstitutionEntry> = emptyList()

    init {
        checkCredentialsAndFetch()
        loadArchive()
    }

    private fun loadArchive() {
        viewModelScope.launch {
            dataStoreManager.archiveFlow.collect { json ->
                if (!json.isNullOrEmpty()) {
                    val type = object : TypeToken<List<SubstitutionEntry>>() {}.type
                    val entries: List<SubstitutionEntry> = gson.fromJson(json, type)
                    _archive.value = sortArchive(entries)
                }
            }
        }
    }

    private fun sortArchive(entries: List<SubstitutionEntry>): List<SubstitutionEntry> {
        return entries.sortedWith(
            compareBy<SubstitutionEntry> { it.day }
                .thenBy { it.lesson.filter { c -> c.isDigit() }.toIntOrNull() ?: 999 }
        )
    }

    fun archiveSubstitutions(entries: List<SubstitutionEntry>? = null) {
        if (isDemoMode) return
        val toArchive = entries ?: lastSuccessEntries
        if (toArchive.isNotEmpty()) {
            viewModelScope.launch {
                val newArchive = (toArchive + _archive.value).distinctBy { 
                    it.day + it.lesson + it.subject + it.room + it.art + it.text 
                }
                val sortedArchive = sortArchive(newArchive)
                _archive.value = sortedArchive
                dataStoreManager.saveArchive(gson.toJson(sortedArchive))
            }
        }
    }

    fun removeFromArchive(entry: SubstitutionEntry) {
        viewModelScope.launch {
            val newArchive = _archive.value.filter { it != entry }
            _archive.value = newArchive
            dataStoreManager.saveArchive(gson.toJson(newArchive))
        }
    }

    fun clearArchive() {
        viewModelScope.launch {
            _archive.value = emptyList()
            dataStoreManager.saveArchive("")
        }
    }

    fun toggleColumnOrder() {
        viewModelScope.launch {
            dataStoreManager.saveSwapPreference(!isRoomFirst.value)
        }
    }

    fun toggleSortByPeriod() {
        viewModelScope.launch {
            dataStoreManager.saveSortPreference(!sortByPeriod.value)
            // Re-sort if we have data
            val current = _uiState.value
            if (current is UiState.Success) {
                _uiState.value = current.copy(entries = sortEntries(lastSuccessEntries))
            }
        }
    }

    fun toggleDynamicColor() {
        viewModelScope.launch {
            dataStoreManager.saveDynamicColorPreference(!isDynamicColorEnabled.value)
        }
    }

    fun setThemeIndex(index: Int) {
        viewModelScope.launch {
            dataStoreManager.saveThemeIndex(index)
            dataStoreManager.saveDynamicColorPreference(false)
        }
    }

    fun changeClass() {
        if (isDemoMode) return
        viewModelScope.launch {
            val u = dataStoreManager.usernameFlow.first() ?: ""
            val p = dataStoreManager.passwordFlow.first() ?: ""
            if (u.isNotEmpty() && p.isNotEmpty()) {
                fetchClasses(u, p)
            } else {
                _uiState.value = UiState.NeedsLogin
            }
        }
    }

    fun checkCredentialsAndFetch() {
        viewModelScope.launch {
            val username = dataStoreManager.usernameFlow.first()
            val password = dataStoreManager.passwordFlow.first()
            val className = dataStoreManager.classNameFlow.first() ?: ""

            if (username.isNullOrEmpty() || password.isNullOrEmpty()) {
                _uiState.value = UiState.NeedsLogin
            } else if (className.isEmpty()) {
                fetchClasses(username, password)
            } else {
                fetchData(username, password, className)
            }
        }
    }

    fun login(username: String, password: String) {
        isDemoMode = false
        viewModelScope.launch {
            fetchClasses(username, password)
        }
    }
    
    fun loginDemo() {
        isDemoMode = true
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            val context = getApplication<Application>()
            val demoEntries = listOf(
                SubstitutionEntry(
                    context.getString(R.string.demo_day_1),
                    context.getString(R.string.demo_art_1),
                    "Demo-10a",
                    "1 - 2",
                    context.getString(R.string.demo_subject_1),
                    "R101", "", "",
                    context.getString(R.string.demo_text_1), ""
                ),
                SubstitutionEntry(
                    context.getString(R.string.demo_day_1),
                    context.getString(R.string.demo_art_2),
                    "Demo-10a",
                    "3",
                    context.getString(R.string.demo_subject_2),
                    "R102", "", "",
                    context.getString(R.string.demo_text_2), ""
                ),
                SubstitutionEntry(
                    context.getString(R.string.demo_day_2),
                    context.getString(R.string.demo_art_3),
                    "Demo-10a",
                    "5 - 6",
                    context.getString(R.string.demo_subject_3),
                    "R205", "", "",
                    context.getString(R.string.demo_text_3), ""
                ),
                SubstitutionEntry(
                    context.getString(R.string.demo_day_3),
                    context.getString(R.string.demo_art_4),
                    "Demo-10a",
                    "1 - 2",
                    context.getString(R.string.demo_subject_4),
                    "HOME", "", "",
                    context.getString(R.string.demo_text_4), ""
                )
            )
            lastSuccessEntries = demoEntries
            _uiState.value = UiState.Success(demoEntries, isDemo = true)
        }
    }

    private suspend fun fetchClasses(u: String, p: String) {
        _uiState.value = UiState.Loading
        try {
            val api = DSBMobileAPI(u, p)
            val classes = api.getAvailableClasses()
            if (classes.isEmpty()) {
                _uiState.value = UiState.Error("No classes found. Check your credentials.")
            } else {
                _uiState.value = UiState.SelectingClass(classes, u, p)
            }
        } catch (e: Exception) {
            _uiState.value = UiState.Error(e.message ?: "Login failed")
        }
    }

    fun selectClass(username: String, password: String, className: String) {
        viewModelScope.launch {
            dataStoreManager.saveCredentials(username, password, className)
            fetchData(username, password, className)
        }
    }
    
    fun logout() {
        isDemoMode = false
        viewModelScope.launch {
            dataStoreManager.clearCredentials()
            _uiState.value = UiState.NeedsLogin
        }
    }

    fun resetToLogin() {
        isDemoMode = false
        _uiState.value = UiState.NeedsLogin
    }

    fun fetchData() {
        if (isDemoMode) {
            loginDemo()
        } else {
            checkCredentialsAndFetch()
        }
    }

    private suspend fun fetchData(u: String, p: String, c: String) {
        _uiState.value = UiState.Loading
        try {
            val api = DSBMobileAPI(u, p)
            val entries = api.getSubstitutions(c)
            lastSuccessEntries = entries
            _uiState.value = UiState.Success(sortEntries(entries))
            // Auto-archive
            archiveSubstitutions(entries)
        } catch (e: Exception) {
            _uiState.value = UiState.Error(e.message ?: "Unknown error")
        }
    }

    private fun sortEntries(entries: List<SubstitutionEntry>): List<SubstitutionEntry> {
        if (!sortByPeriod.value) return entries
        return entries.sortedBy { it.lesson.filter { c -> c.isDigit() }.toIntOrNull() ?: 999 }
    }
}
