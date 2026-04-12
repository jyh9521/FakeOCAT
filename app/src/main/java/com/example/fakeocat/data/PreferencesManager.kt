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

class PreferencesManager(private val context: Context) {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        "secure_settings",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

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
        val dynamicKey = stringPreferencesKey("api_key_$provider")
        val legacyKey = when (provider) {
            "anthropic" -> API_KEY_ANTHROPIC
            "gemini" -> API_KEY_GEMINI
            "deepseek" -> API_KEY_DEEPSEEK
            "grok" -> API_KEY_GROK
            "openai" -> API_KEY_OPENAI
            else -> null
        }
        return context.dataStore.data.map { 
            var k = it[dynamicKey] ?: ""

            if (k.isEmpty() && legacyKey != null) {
                k = it[legacyKey] ?: ""
            }
            
            // 如果 DataStore 为空，则回退到 EncryptedSharedPreferences
            if (k.isEmpty()) {
                k = encryptedPrefs.getString("api_key_$provider", "") ?: ""
                if (k.isNotEmpty()) {
                    Log.d(TAG, "DataStore empty for $provider, recovered from EncryptedPrefs")
                    // 若可行则主动回写 DataStore（在 setApiKeyFor 中完成）
                }
            }
            
            Log.d(TAG, "Reading API Key for $provider: ${if (k.isEmpty()) "EMPTY" else "EXISTS(length=${k.length})"}")
            k
        }
    }

    /**
     * 当当前服务商缺少密钥时，回退查找任意可用密钥。
     * 返回 (ProviderName, Key) 二元组。
     */
    suspend fun findAnyAvailableKey(): Pair<String, String>? {
        // 先尝试 DataStore
        val data = context.dataStore.data.first()
        val providers = AiProviderCatalog.providers.map { it.id }
        
        for (provider in providers) {
            val dynamicKey = data[stringPreferencesKey("api_key_$provider")]
            val legacyKey = when (provider) {
                "anthropic" -> data[API_KEY_ANTHROPIC]
                "gemini" -> data[API_KEY_GEMINI]
                "deepseek" -> data[API_KEY_DEEPSEEK]
                "grok" -> data[API_KEY_GROK]
                "openai" -> data[API_KEY_OPENAI]
                else -> null
            }

            val dsKey = dynamicKey ?: legacyKey
            if (!dsKey.isNullOrBlank()) {
                Log.d(TAG, "Fallback: Found available key in DataStore for $provider")
                return provider to dsKey
            }
        }
        
        // 再尝试 EncryptedPrefs 作为第二级回退
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
        Log.d(TAG, "Setting API Key for $provider (Persistent + Secure)")
        
        // 1. 保存到 EncryptedSharedPreferences（可用时使用硬件保护）
        encryptedPrefs.edit().putString("api_key_$provider", apiKey).apply()
        
        // 2. 保存到 DataStore（供 UI Flow 使用）
        val dynamicKey = stringPreferencesKey("api_key_$provider")
        context.dataStore.edit { it[dynamicKey] = apiKey }

        val legacyKey = when (provider) {
            "anthropic" -> API_KEY_ANTHROPIC
            "gemini" -> API_KEY_GEMINI
            "deepseek" -> API_KEY_DEEPSEEK
            "grok" -> API_KEY_GROK
            "openai" -> API_KEY_OPENAI
            else -> null
        }
        if (legacyKey != null) {
            context.dataStore.edit { it[legacyKey] = apiKey }
        }
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
