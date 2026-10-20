package com.vic2ray.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vic2ray.fetcher.ConfigFetcher
import com.vic2ray.models.AutoRefreshPolicy
import com.vic2ray.models.NetworkHealthCache
import com.vic2ray.models.NetworkType
import com.vic2ray.models.ProtocolType
import com.vic2ray.models.ServerCatalog
import com.vic2ray.models.ServerSort
import com.vic2ray.models.SubscriptionSources
import com.vic2ray.models.TestFailure
import com.vic2ray.models.VpnConfig
import com.vic2ray.parser.ConfigParser
import com.vic2ray.tester.ConnectivityTester
import com.vic2ray.vpn.VicVpnService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
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
    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val networkHealth = NetworkHealthCache()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var observedNetworkHandle: Long? = null
    private var physicalNetworkHandle: Long? = null
    private var networkInitialized = false
    private var internetAvailable = false
    @Volatile private var syncJob: Job? = null
    private var autoRefreshJob: Job? = null
    private var automaticSync = false
    private var syncGeneration = 0L
    private var appStarted = false
    private var importVisible = false
    private var lastAutoAttempt = 0L
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
    private val _autoRefresh = MutableStateFlow(prefs.getBoolean("auto_refresh", true))
    val autoRefresh = _autoRefresh.asStateFlow()
    private val _sourceCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val sourceCounts = _sourceCounts.asStateFlow()
    private val _sourceFailures = MutableStateFlow<List<String>>(emptyList())
    val sourceFailures = _sourceFailures.asStateFlow()
    private val _networkType = MutableStateFlow(NetworkType.OFFLINE)
    val networkType = _networkType.asStateFlow()
    private val _testFailures = MutableStateFlow<Map<TestFailure, Int>>(emptyMap())
    val testFailures = _testFailures.asStateFlow()
    private val _lastSyncTime = MutableStateFlow(0L)
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
        val json = prefs.getString("sources_list", null)
        return runCatching {
            val type = object : TypeToken<List<String>>() {}.type
            val saved = if (json == null) null else gson.fromJson<List<String>>(json, type)
            SubscriptionSources.migrate(saved).also { migrated ->
                if (migrated != saved) prefs.edit()
                    .putString("sources_list", gson.toJson(migrated))
                    .putLong("last_sync_time", 0L).apply()
            }
        }.getOrDefault(SubscriptionSources.urls)
    }

    private fun loadCachedConfigs(): List<VpnConfig> = runCatching {
        val type = object : TypeToken<List<VpnConfig>>() {}.type
        gson.fromJson<List<VpnConfig>>(prefs.getString("cached_configs", "[]"), type).orEmpty()
            .filter { it.rawConfig.isNotBlank() && it.protocol != ProtocolType.UNKNOWN }
            .distinctBy { it.rawConfig }.take(MAX_CACHED_CONFIGS)
            .map { it.copy(id = UUID.nameUUIDFromBytes(it.rawConfig.toByteArray(Charsets.UTF_8)).toString(),
                ping = -1, isWorking = false, lastTestFailure = null, lastHttpStatus = null) }
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
    fun restoreDefaultSources() = saveSources(SubscriptionSources.urls)

    private fun saveSources(sources: List<String>) {
        _customSources.value = sources
        _sourceFailures.value = _sourceFailures.value.filter { it in sources }
        _sourceCounts.value = _sourceCounts.value.filterKeys { it in sources }
        prefs.edit().putString("sources_list", gson.toJson(sources)).apply()
    }

    /** The activity owns this foreground-only loop; it never starts background services. */
    fun onAppStarted() {
        appStarted = true
        observeNetwork()
        refreshNetworkState()
        if (autoRefreshJob?.isActive == true) return
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                refreshNetworkState()
                maybeAutoRefresh()
                delay(30_000)
            }
        }
    }

    fun onAppStopped() {
        appStarted = false
        autoRefreshJob?.cancel()
        autoRefreshJob = null
        // Without a callback, foreground polling cannot protect a background scan's profile.
        if (automaticSync || (networkCallback == null && syncJob?.isActive == true)) stopSync()
    }

    fun setAutoRefresh(enabled: Boolean) {
        _autoRefresh.value = enabled
        prefs.edit().putBoolean("auto_refresh", enabled).apply()
        if (!enabled && automaticSync) stopSync()
        if (enabled) maybeAutoRefresh()
    }

    fun setImportVisible(visible: Boolean) {
        importVisible = visible
        if (visible && automaticSync) stopSync()
    }

    private fun maybeAutoRefresh() {
        if (!appStarted || physicalNetworkHandle == null || !internetAvailable) return
        val vpnActive = VicVpnService.isConnected.value || VicVpnService.isConnecting.value
        if (vpnActive && automaticSync) stopSync()
        if (AutoRefreshPolicy.shouldRefresh(
                now = System.currentTimeMillis(), lastSync = _lastSyncTime.value,
                lastAttempt = lastAutoAttempt, enabled = _autoRefresh.value,
                busy = syncJob?.isActive == true, vpnActive = vpnActive,
                hasSources = _customSources.value.isNotEmpty(),
                hasWorkingServers = _allConfigs.value.any { it.isWorking && it.ping > 0 },
                interactionActive = importVisible
            )) startSync(automatic = true)
    }

    /** Manual scans may continue while backgrounded, so network identity follows the ViewModel. */
    private fun observeNetwork() {
        if (networkCallback != null) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = postNetworkUpdate(this)
            override fun onLost(network: Network) = postNetworkUpdate(this)
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                postNetworkUpdate(this, network, capabilities)
        }
        networkCallback = callback
        if (runCatching { connectivityManager.registerDefaultNetworkCallback(callback) }.isFailure) {
            networkCallback = null // The foreground polling loop remains available.
        }
    }

    private fun postNetworkUpdate(callback: ConnectivityManager.NetworkCallback,
        network: Network? = null, capabilities: NetworkCapabilities? = null) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            if (networkCallback !== callback) return@launch
            val active = connectivityManager.activeNetwork
            refreshNetworkState(active, if (network == active) capabilities else null)
            maybeAutoRefresh()
        }
    }

    @Synchronized
    private fun refreshNetworkState(network: Network? = connectivityManager.activeNetwork,
        suppliedCapabilities: NetworkCapabilities? = null) {
        val capabilities = suppliedCapabilities ?: connectivityManager.getNetworkCapabilities(network)
        internetAvailable = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val vpn = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val handle = network?.networkHandle?.takeIf { internetAvailable }
        val physicalHandle = handle?.takeUnless { vpn }
        _networkType.value = when {
            !internetAvailable -> NetworkType.OFFLINE
            vpn -> NetworkType.OTHER
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> NetworkType.WIFI
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> NetworkType.MOBILE
            else -> NetworkType.OTHER
        }
        if (networkInitialized && handle == observedNetworkHandle && physicalHandle == physicalNetworkHandle) return
        networkInitialized = true
        ++syncGeneration
        syncJob?.cancel()
        syncJob = null
        automaticSync = false
        _allConfigs.value = networkHealth.switchTo(physicalHandle, _allConfigs.value, SystemClock.elapsedRealtime())
        observedNetworkHandle = handle
        physicalNetworkHandle = physicalHandle
        _testFailures.value = emptyMap()
        _sourceFailures.value = emptyList()
        _sourceCounts.value = emptyMap()
        _lastSyncTime.value = 0L
        lastAutoAttempt = 0L
        prefs.edit().putLong("last_auto_attempt", 0L).apply()
        _uiState.value = if (_allConfigs.value.isEmpty()) UiState.Idle else UiState.Success(_allConfigs.value)
        persistCatalog()
    }

    override fun onCleared() {
        val callback = networkCallback
        networkCallback = null
        if (callback != null) runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        super.onCleared()
    }

    /** Give tunnel startup priority over discovery, including the native test cores. */
    fun pauseDiscoveryForConnection() {
        if (syncJob?.isActive == true) stopSync()
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

    fun syncAndTestServers() = startSync(automatic = false)

    @Synchronized
    private fun startSync(automatic: Boolean) {
        if (syncJob?.isActive == true) return
        refreshNetworkState()
        if (!internetAvailable) {
            if (!automatic) _uiState.value = UiState.Error(message(
                "No internet connection. Cached servers are still available.",
                "اتصال اینترنت برقرار نیست. سرورهای ذخیره‌شده همچنان در دسترس‌اند."))
            return
        }
        val sources = _customSources.value.toList()
        val pinned = _allConfigs.value.filter { it.rawConfig in importedLinks || it.rawConfig in _favorites.value }
        if (sources.isEmpty() && pinned.isEmpty()) {
            _uiState.value = UiState.Error(message("Add a source or import a configuration first.",
                "ابتدا یک منبع یا کانفیگ اضافه کن."))
            return
        }

        automaticSync = automatic
        lastAutoAttempt = System.currentTimeMillis()
        prefs.edit().putLong("last_auto_attempt", lastAutoAttempt).apply()
        val scanLimit = if (automatic) MAX_AUTO_SCAN_CONFIGS else MAX_SCAN_CONFIGS
        val generation = ++syncGeneration
        val scanNetworkHandle = observedNetworkHandle
        fun isCurrentScan() = generation == syncGeneration && scanNetworkHandle == observedNetworkHandle
        syncJob = viewModelScope.launch {
            _testFailures.value = emptyMap()
            _sourceFailures.value = emptyList()
            _sourceCounts.value = emptyMap()
            _uiState.value = UiState.Loading(message("Updating subscription sources…", "در حال دریافت منابع…"))
            try {
                val fetched = fetcher.fetchSources(sources)
                if (!isCurrentScan()) return@launch
                val groups = withContext(Dispatchers.Default) {
                    fetched.documents.map { document ->
                        document.url to parser.parseConfigs(listOf(document.body))
                    }
                }
                if (!isCurrentScan()) return@launch
                _sourceCounts.value = groups.associate { it.first to it.second.size }
                _sourceFailures.value = (fetched.failedSources +
                    groups.filter { it.second.isEmpty() }.map { it.first }).distinct()
                val parsed = withContext(Dispatchers.Default) {
                    ServerCatalog.interleaveSources(groups.map { it.second })
                }
                if (!isCurrentScan()) return@launch
                val initialCursor = prefs.getInt("scan_cursor", 0)
                val candidates = ServerCatalog.scanCandidates(ServerCatalog.merge(pinned, parsed),
                    priorityLinks = _favorites.value + importedLinks,
                    offset = initialCursor, limit = scanLimit)
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
                    // A probe may finish before Android's queued network callback reaches Main.
                    refreshNetworkState()
                    if (!isCurrentScan()) return@testAllStreaming
                    tested++
                    // Save completed progress even if backgrounding cancels the rest of this page.
                    prefs.edit().putInt("scan_cursor", (initialCursor.toLong() + tested)
                        .rem(Int.MAX_VALUE).toInt()).apply()
                    if (result.isWorking) {
                        working++
                        found.add(result)
                    } else {
                        val failure = result.lastTestFailure ?: TestFailure.CONNECTION
                        _testFailures.value = _testFailures.value +
                            (failure to ((_testFailures.value[failure] ?: 0) + 1))
                    }
                    if (result.isWorking || _allConfigs.value.any { it.rawConfig == result.rawConfig }) {
                        _allConfigs.value = trimCatalog(ServerCatalog.merge(_allConfigs.value, listOf(result)))
                    }
                    networkHealth.record(result, SystemClock.elapsedRealtime())
                    _uiState.value = UiState.Testing(tested, candidates.size, found.toList())
                    if (tested % 25 == 0) persistCatalog()
                }
                if (!isCurrentScan()) return@launch
                _lastSyncTime.value = System.currentTimeMillis()
                persistCatalog()
                _uiState.value = if (working > 0) UiState.Success(_allConfigs.value) else
                    UiState.Error(message("No server passed the connection test. Try another sync or import a trusted link.",
                        "هیچ سروری از تست اتصال عبور نکرد. دوباره همگام‌سازی کن یا یک کانفیگ معتبر وارد کن."))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (isCurrentScan()) _uiState.value = UiState.Error(message(
                    "Update failed. Your saved servers are still available.",
                    "به‌روزرسانی ناموفق بود. سرورهای ذخیره‌شده همچنان در دسترس‌اند."))
            } finally {
                if (isCurrentScan()) {
                    automaticSync = false
                    persistCatalog()
                }
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
        ++syncGeneration
        lastAutoAttempt = System.currentTimeMillis()
        prefs.edit().putLong("last_auto_attempt", lastAutoAttempt).apply()
        syncJob?.cancel()
        automaticSync = false
        _uiState.value = if (_allConfigs.value.isEmpty()) UiState.Idle else UiState.Success(_allConfigs.value)
        persistCatalog()
    }

    private fun message(english: String, persian: String) = if (_language.value == "fa") persian else english

    companion object {
        const val MAX_SCAN_CONFIGS = 300
        private const val MAX_AUTO_SCAN_CONFIGS = 60
        private const val MAX_CACHED_CONFIGS = 2000
        private const val MAX_IMPORTED_CONFIGS = 1000
        private const val MAX_IMPORT_CHARS = 1024 * 1024
    }
}

sealed class UiState {
    object Idle : UiState()
    data class Loading(val message: String) : UiState()
    data class Testing(val testedCount: Int, val totalConfigs: Int, val currentWorking: List<VpnConfig>) : UiState()
    data class Success(val configs: List<VpnConfig>) : UiState()
    data class Error(val error: String) : UiState()
}
