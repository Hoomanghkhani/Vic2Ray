package com.vic2ray.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vic2ray.fetcher.ConfigFetcher
import com.vic2ray.models.ProtocolType
import com.vic2ray.models.VpnConfig
import com.vic2ray.parser.ConfigParser
import com.vic2ray.tester.ConnectivityTester
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("Vic2rayPrefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val fetcher = ConfigFetcher()
    private val parser = ConfigParser()
    private val tester = ConnectivityTester()

    private var syncJob: Job? = null

    private val _allConfigs = MutableStateFlow<List<VpnConfig>>(emptyList())
    
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    private val _selectedProtocol = MutableStateFlow<ProtocolType>(ProtocolType.VMESS)
    val selectedProtocol: StateFlow<ProtocolType> = _selectedProtocol

    private val _customSources = MutableStateFlow<List<String>>(emptyList())
    val customSources: StateFlow<List<String>> = _customSources

    init {
        loadSources()
        loadCachedConfigs()
    }

    private fun loadSources() {
        val cachedJson = prefs.getString("sources_list", null)
        if (cachedJson != null) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                val cachedList: List<String> = gson.fromJson(cachedJson, type)
                _customSources.value = cachedList
            } catch (e: Exception) {
                initDefaultSources()
            }
        } else {
            initDefaultSources()
        }
    }

    private fun initDefaultSources() {
        val defaultSources = listOf(
            "https://raw.githubusercontent.com/ebrasha/free-v2ray-public-list/main/V2Ray-Config-By-EbraSha.txt",
            "https://raw.githubusercontent.com/Epodonios/v2ray-configs/main/All_Configs_Sub.txt",
            "https://raw.githubusercontent.com/barry-far/V2ray-Configs/main/All_Configs_Sub.txt"
        )
        _customSources.value = defaultSources
        saveSources(defaultSources)
    }

    private fun saveSources(sources: List<String>) {
        val json = gson.toJson(sources)
        prefs.edit().putString("sources_list", json).apply()
    }

    private fun loadCachedConfigs() {
        val cachedJson = prefs.getString("cached_configs", null)
        if (cachedJson != null) {
            try {
                val type = object : TypeToken<List<VpnConfig>>() {}.type
                val cachedList: List<VpnConfig> = gson.fromJson(cachedJson, type)
                if (cachedList.isNotEmpty()) {
                    _allConfigs.value = cachedList
                    _uiState.value = UiState.Success(cachedList)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveConfigsToCache(configs: List<VpnConfig>) {
        try {
            val json = gson.toJson(configs)
            prefs.edit().putString("cached_configs", json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addCustomSource(url: String) {
        if (url.isNotBlank() && !_customSources.value.contains(url)) {
            val newList = _customSources.value + url
            _customSources.value = newList
            saveSources(newList)
        }
    }

    fun removeSource(url: String) {
        val newList = _customSources.value.filter { it != url }
        _customSources.value = newList
        saveSources(newList)
    }

    fun syncAndTestServers() {
        if (syncJob?.isActive == true) return

        syncJob = viewModelScope.launch {
            _allConfigs.value = emptyList() // پاک کردن سرورهای قبلی
            _uiState.value = UiState.Loading("در حال دریافت کانفیگ‌ها...")
            
            try {
                // دریافت
                val rawTexts = fetcher.fetchAllRawConfigs(_customSources.value)
                
                if(rawTexts.isEmpty()) {
                    _uiState.value = UiState.Error("هیچ کانفیگی دریافت نشد! لطفاً اینترنت خود را بررسی کنید.")
                    return@launch
                }

                // پردازش
                _uiState.value = UiState.Loading("در حال پردازش ${rawTexts.size} کانفیگ...")
                val parsedConfigs = parser.parseConfigs(rawTexts)
                
                // تست جریانی (Streaming)
                _uiState.value = UiState.Testing(0, parsedConfigs.size, emptyList())
                
                val currentWorking = mutableListOf<VpnConfig>()
                var testedCount = 0

                tester.testAllStreaming(parsedConfigs) { workingConfig ->
                    currentWorking.add(workingConfig)
                    testedCount++
                    val sorted = currentWorking.sortedBy { it.ping }
                    _allConfigs.value = sorted
                    saveConfigsToCache(sorted)
                    
                    // اپدیت UI بلافاصله
                    _uiState.value = UiState.Testing(testedCount, parsedConfigs.size, sorted)
                }
                
                // پایان تست‌ها موفقیت‌آمیز
                _uiState.value = UiState.Success(_allConfigs.value)

            } catch (e: CancellationException) {
                // با زدن دکمه توقف، این ارور پرتاب می‌شود که نباید به عنوان خطای واقعی در نظر گرفته شود
                throw e
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "خطای نامشخص رخ داد")
            }
        }
    }

    fun stopSync() {
        syncJob?.cancel()
        syncJob = null
        if (_uiState.value is UiState.Testing) {
            _uiState.value = UiState.Success(_allConfigs.value)
        } else if (_uiState.value is UiState.Loading) {
             _uiState.value = UiState.Idle
        }
    }

    fun setProtocolFilter(protocol: ProtocolType) {
        _selectedProtocol.value = protocol
    }
}

sealed class UiState {
    object Idle : UiState()
    data class Loading(val message: String) : UiState()
    data class Testing(val foundCount: Int, val totalConfigs: Int, val currentWorking: List<VpnConfig>) : UiState()
    data class Success(val configs: List<VpnConfig>) : UiState()
    data class Error(val error: String) : UiState()
}
