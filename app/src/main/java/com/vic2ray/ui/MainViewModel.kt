package com.vic2ray.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vic2ray.fetcher.ConfigFetcher
import com.vic2ray.models.ProtocolType
import com.vic2ray.models.ServerCatalog
import com.vic2ray.models.ServerSort
import com.vic2ray.models.VpnConfig
import com.vic2ray.parser.ConfigParser
import com.vic2ray.tester.ConnectivityTester
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("Vic2rayPrefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val fetcher = ConfigFetcher()
    private val parser = ConfigParser()
    private val tester by lazy { ConnectivityTester(application) }
    @Volatile private var syncJob: Job? = null
    private var persistJob: Job? = null
    private var importedLinks = prefs.getStringSet("imported_links", emptySet()).orEmpty().toSet()

    private val _allConfigs = MutableStateFlow(loadCachedConfigs())
    val allConfigs = _allConfigs.asStateFlow()
    private val _uiState = MutableStateFlow<UiState>(
        if (_allConfigs.value.isEmpty()) UiState.Idle else UiState.Success(_allConfigs.value))
    val uiState = _uiState.asStateFlow()
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    private val _selectedProtocol = MutableStateFlow<ProtocolType?>(null)
    val selectedProtocol = _selectedProtocol.asStateFlow()
    private val _sortOrder = MutableStateFlow(ServerSort.LATENCY)
    val sortOrder = _sortOrder.asStateFlow()
    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly = _favoritesOnly.asStateFlow()
    private val _favorites = MutableStateFlow(prefs.getStringSet("favorites", emptySet()).orEmpty().toSet())
    val favorites = _favorites.asStateFlow()
    private val _customSources = MutableStateFlow(loadSources())
    val customSources = _customSources.asStateFlow()
    private val _sourceFailures = MutableStateFlow<List<String>>(emptyList())
    val sourceFailures = _sourceFailures.asStateFlow()
    private val _lastSyncTime = MutableStateFlow(prefs.getLong("last_sync_time", 0L))
    val lastSyncTime = _lastSyncTime.asStateFlow()
    private val _language = MutableStateFlow(
        prefs.getString("language", Locale.getDefault().language).let { if (it == "fa") "fa" else "en" })
    val language = _language.asStateFlow()

    private data class Filters(val query: String, val protocol: ProtocolType?, val sort: ServerSort,
        val favoritesOnly: Boolean)

    private val filters = combine(_searchQuery, _selectedProtocol, _sortOrder, _favoritesOnly) {
        query, protocol, sort, onlyFavorites -> Filters(query, protocol, sort, onlyFavorites)
    }

    val visibleConfigs = combine(_allConfigs, _favorites, filters) { configs, favorites, filters ->
        ServerCatalog.visible(configs, filters.query, filters.protocol, favorites,
            filters.favoritesOnly, filters.sort)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, _allConfigs.value)

    private fun loadSources(): List<String> {
        val json = prefs.getString("sources_list", null) ?: return DEFAULT_SOURCES
        return runCatching {
            val type = object : TypeToken<List<String>>() {}.type
            val saved = gson.fromJson<List<String>>(json, type).orEmpty()
                .map(String::trim).filter(ServerCatalog::isValidSource).distinct()
            if (saved == LEGACY_DEFAULT_SOURCES) {
                prefs.edit().putString("sources_list", gson.toJson(DEFAULT_SOURCES)).apply()
                DEFAULT_SOURCES
            } else saved
        }.getOrDefault(DEFAULT_SOURCES)
    }

    private fun loadCachedConfigs(): List<VpnConfig> = runCatching {
        val type = object : TypeToken<List<VpnConfig>>() {}.type
        gson.fromJson<List<VpnConfig>>(prefs.getString("cached_configs", "[]"), type).orEmpty()
            .filter { it.rawConfig.isNotBlank() && it.protocol != ProtocolType.UNKNOWN }
            .distinctBy { it.rawConfig }.take(MAX_CACHED_CONFIGS)
            .map { it.copy(id = UUID.nameUUIDFromBytes(it.rawConfig.toByteArray(Charsets.UTF_8)).toString()) }
    }.getOrDefault(emptyList())

    /** Serialize snapshots off the UI thread; only the newest snapshot may commit. */
    private fun persistCatalog() {
        val snapshot = _allConfigs.value
        val imports = importedLinks.toSet()
        val lastSync = _lastSyncTime.value
        persistJob?.cancel()
        persistJob = viewModelScope.launch(Dispatchers.Default) {
            val json = gson.toJson(snapshot)
            ensureActive()
            withContext(Dispatchers.Main.immediate) {
                prefs.edit().putString("cached_configs", json).putStringSet("imported_links", imports)
                    .putLong("last_sync_time", lastSync).apply()
            }
        }
    }

    fun addCustomSource(url: String): Boolean {
        val normalized = url.trim()
        if (!ServerCatalog.isValidSource(normalized) || normalized in _customSources.value) return false
        saveSources(_customSources.value + normalized)
        return true
    }

    fun removeSource(url: String) = saveSources(_customSources.value - url)
    fun restoreDefaultSources() = saveSources(DEFAULT_SOURCES)

    private fun saveSources(sources: List<String>) {
        _customSources.value = sources
        _sourceFailures.value = _sourceFailures.value.filter { it in sources }
        prefs.edit().putString("sources_list", gson.toJson(sources)).apply()
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setProtocolFilter(protocol: ProtocolType?) { _selectedProtocol.value = protocol }
    fun setSortOrder(sort: ServerSort) { _sortOrder.value = sort }
    fun setFavoritesOnly(enabled: Boolean) { _favoritesOnly.value = enabled }
    fun setLanguage(language: String) {
        _language.value = if (language == "fa") "fa" else "en"
        prefs.edit().putString("language", _language.value).apply()
    }

    fun toggleFavorite(config: VpnConfig) {
        val raw = config.rawConfig
        _favorites.value = if (raw in _favorites.value) _favorites.value - raw else _favorites.value + raw
        prefs.edit().putStringSet("favorites", _favorites.value).apply()
    }

    /** Called from Default by import UI. Negative results distinguish size and busy errors. */
    @Synchronized
    fun importConfigs(text: String): Int {
        if (syncJob?.isActive == true) return -2
        if (text.length > MAX_IMPORT_CHARS) return -1
        val parsed = parser.parseConfigs(listOf(text)).take(MAX_IMPORTED_CONFIGS)
        val existingLinks = _allConfigs.value.map { it.rawConfig }.toSet()
        val added = parsed.filterNot { it.rawConfig in existingLinks }
            .take((MAX_CACHED_CONFIGS - _allConfigs.value.size).coerceAtLeast(0))
        if (added.isEmpty()) return 0
        importedLinks = importedLinks + added.map { it.rawConfig }
        _allConfigs.value = ServerCatalog.merge(_allConfigs.value, added)
        _uiState.value = UiState.Success(_allConfigs.value)
        viewModelScope.launch { persistCatalog() }
        return added.size
    }

    @Synchronized
    fun syncAndTestServers() {
        if (syncJob?.isActive == true) return
        val manager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true) {
            _uiState.value = UiState.Error(message("No internet connection. Cached servers are still available.",
                "اتصال اینترنت برقرار نیست. سرورهای ذخیره‌شده همچنان در دسترس‌اند."))
            return
        }
        val sources = _customSources.value.toList()
        val imports = _allConfigs.value.filter { it.rawConfig in importedLinks }
        if (sources.isEmpty() && imports.isEmpty()) {
            _uiState.value = UiState.Error(message("Add a source or import a configuration first.",
                "ابتدا یک منبع یا کانفیگ اضافه کن."))
            return
        }

        syncJob = viewModelScope.launch {
            _sourceFailures.value = emptyList()
            _uiState.value = UiState.Loading(message("Updating subscription sources…", "در حال دریافت منابع…"))
            try {
                val fetched = fetcher.fetchSources(sources)
                _sourceFailures.value = fetched.failedSources
                val parsed = withContext(Dispatchers.Default) { parser.parseConfigs(fetched.rawLines) }
                val candidates = ServerCatalog.scanCandidates(ServerCatalog.merge(imports, parsed),
                    priorityLinks = _favorites.value + importedLinks,
                    offset = prefs.getInt("scan_cursor", 0), limit = MAX_SCAN_CONFIGS)
                if (candidates.isEmpty()) {
                    _uiState.value = UiState.Error(message("No valid configurations received. Cached servers were kept.",
                        "کانفیگ معتبری دریافت نشد. سرورهای قبلی حفظ شدند."))
                    return@launch
                }

                var tested = 0
                var working = 0
                _uiState.value = UiState.Testing(0, candidates.size, emptyList())
                val found = mutableListOf<VpnConfig>()
                tester.testAllStreaming(candidates) { result ->
                    tested++
                    if (result.isWorking) {
                        working++
                        found.add(result)
                    }
                    if (result.isWorking || _allConfigs.value.any { it.rawConfig == result.rawConfig }) {
                        _allConfigs.value = trimCatalog(ServerCatalog.merge(_allConfigs.value, listOf(result)))
                    }
                    _uiState.value = UiState.Testing(tested, candidates.size, found.toList())
                    if (tested % 25 == 0) persistCatalog()
                }
                _lastSyncTime.value = System.currentTimeMillis()
                val previousCursor = prefs.getInt("scan_cursor", 0)
                prefs.edit().putInt("scan_cursor", (previousCursor.toLong() + MAX_SCAN_CONFIGS)
                    .rem(Int.MAX_VALUE).toInt()).apply()
                persistCatalog()
                _uiState.value = if (working > 0) UiState.Success(_allConfigs.value) else
                    UiState.Error(message("No server passed the connection test. Try another sync or import a trusted link.",
                        "هیچ سروری از تست اتصال عبور نکرد. دوباره همگام‌سازی کن یا یک کانفیگ معتبر وارد کن."))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = UiState.Error(message("Update failed. Your saved servers are still available.",
                    "به‌روزرسانی ناموفق بود. سرورهای ذخیره‌شده همچنان در دسترس‌اند."))
            } finally {
                persistCatalog()
            }
        }
    }

    private fun trimCatalog(configs: List<VpnConfig>): List<VpnConfig> {
        if (configs.size <= MAX_CACHED_CONFIGS) return configs
        val priorityLinks = importedLinks + _favorites.value
        val pinned = configs.filter { it.rawConfig in priorityLinks }
        return (pinned + ServerCatalog.visible(configs.filterNot { it.rawConfig in priorityLinks }))
            .take(MAX_CACHED_CONFIGS)
    }

    fun stopSync() {
        syncJob?.cancel()
        _uiState.value = if (_allConfigs.value.isEmpty()) UiState.Idle else UiState.Success(_allConfigs.value)
        persistCatalog()
    }

    private fun message(english: String, persian: String) = if (_language.value == "fa") persian else english

    companion object {
        const val MAX_SCAN_CONFIGS = 300
        private const val MAX_CACHED_CONFIGS = 2000
        private const val MAX_IMPORTED_CONFIGS = 1000
        private const val MAX_IMPORT_CHARS = 1024 * 1024
        private val LEGACY_DEFAULT_SOURCES = listOf(
            "https://raw.githubusercontent.com/yebekhe/TelegramV2rayCollector/main/sub/normal/mix",
            "https://raw.githubusercontent.com/mahdibland/ShadowsocksAggregator/master/Eternity",
            "https://raw.githubusercontent.com/w1770946466/Auto_proxy/main/Long_term_subscription_num",
            "https://raw.githubusercontent.com/soroushmirzaei/telegram-configs-collector/main/channels/protocols/vless",
            "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/Sub1.txt"
        )
        private val DEFAULT_SOURCES = listOf(
            "https://raw.githubusercontent.com/mahdibland/ShadowsocksAggregator/master/Eternity",
            "https://raw.githubusercontent.com/w1770946466/Auto_proxy/main/Long_term_subscription_num",
            "https://raw.githubusercontent.com/barry-far/V2ray-config/main/Sub1.txt"
        )
    }
}

sealed class UiState {
    object Idle : UiState()
    data class Loading(val message: String) : UiState()
    data class Testing(val testedCount: Int, val totalConfigs: Int, val currentWorking: List<VpnConfig>) : UiState()
    data class Success(val configs: List<VpnConfig>) : UiState()
    data class Error(val error: String) : UiState()
}
