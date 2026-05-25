package com.example.fakeocat.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.fakeocat.network.AiProviderCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(
    private val context: Context
) {

    // 使用 lazy 延迟初始化，避免在主线程构造时阻塞
    private val encryptedPrefs: android.content.SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "secure_settings",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        private const val TAG = "PreferencesManager"
        val SELECTED_PROVIDER = stringPreferencesKey("selected_provider")
        val API_KEY_OPENAI = stringPreferencesKey("api_key_openai")
        val API_KEY_ANTHROPIC = stringPreferencesKey("api_key_anthropic")
        val API_KEY_GEMINI = stringPreferencesKey("api_key_gemini")
        val API_KEY_DEEPSEEK = stringPreferencesKey("api_key_deepseek")
        val API_KEY_GROK = stringPreferencesKey("api_key_grok")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val NATIVE_LANGUAGE = stringPreferencesKey("native_language")
        val TARGET_LANGUAGE = stringPreferencesKey("target_language")
    }

    val selectedProviderFlow: Flow<String> = context.dataStore.data.map { 
        val p = it[SELECTED_PROVIDER] ?: "openai"
        Log.d(TAG, "Current selected provider: $p")
        p
    }
    val themeModeFlow: Flow<String> = context.dataStore.data.map { it[THEME_MODE] ?: "auto" }
    val appLanguageFlow: Flow<String> = context.dataStore.data.map { it[APP_LANGUAGE] ?: "system" }
    val nativeLanguageFlow: Flow<String> = context.dataStore.data.map { it[NATIVE_LANGUAGE] ?: "zh" }
    val targetLanguageFlow: Flow<String> = context.dataStore.data.map { it[TARGET_LANGUAGE] ?: "ja" }

    fun apiKeyFlowFor(provider: String): Flow<String> {
        // 仅从 EncryptedSharedPreferences 读取 API Key（不在 DataStore 中存储明文 Key）
        return context.dataStore.data.map {
            val k = encryptedPrefs.getString("api_key_$provider", "") ?: ""
            Log.d(TAG, "Reading API Key for $provider: ${if (k.isEmpty()) "EMPTY" else "EXISTS(length=${k.length})"}")
            k
        }
    }

    /**
     * 当当前服务商缺少密钥时，回退查找任意可用密钥。
     * 返回 (ProviderName, Key) 二元组。
     */
    suspend fun findAnyAvailableKey(): Pair<String, String>? {
        val providers = AiProviderCatalog.providers.map { it.id }
        // 仅从 EncryptedSharedPreferences 查找可用密钥
        for (provider in providers) {
            val epKey = encryptedPrefs.getString("api_key_$provider", "")
            if (!epKey.isNullOrBlank()) {
                Log.d(TAG, "Fallback: Found available key in EncryptedPrefs for $provider")
                return provider to epKey
            }
        }
        return null
    }

    suspend fun setSelectedProvider(provider: String) {
        Log.d(TAG, "Setting selected provider: $provider")
        context.dataStore.edit { it[SELECTED_PROVIDER] = provider }
    }

    suspend fun setApiKeyFor(provider: String, apiKey: String) {
        Log.d(TAG, "Setting API Key for $provider (Secure only)")
        // 仅保存到 EncryptedSharedPreferences（使用硬件保护加密存储）
        encryptedPrefs.edit().putString("api_key_$provider", apiKey).apply()
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[THEME_MODE] = mode }
    }

    suspend fun setAppLanguage(lang: String) {
        context.dataStore.edit { it[APP_LANGUAGE] = lang }
        // 同时写入 SharedPreferences，供 attachBaseContext 同步读取
        context.getSharedPreferences("settings_locale", Context.MODE_PRIVATE)
            .edit()
            .putString("app_language", lang)
            .apply()
    }

    suspend fun setNativeLanguage(lang: String) {
        context.dataStore.edit { it[NATIVE_LANGUAGE] = lang }
    }

    suspend fun setTargetLanguage(lang: String) {
        context.dataStore.edit { it[TARGET_LANGUAGE] = lang }
    }

}
