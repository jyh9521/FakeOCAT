package com.example.fakeocat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fakeocat.data.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesPersistenceTest {

    private lateinit var context: Context
    private lateinit var prefsManager: PreferencesManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        prefsManager = PreferencesManager(context)
    }

    @Test
    fun testApiKeyPersistence() = runBlocking {
        val testProvider = "gemini"
        val testKey = "test-api-key-12345"

        prefsManager.setApiKeyFor(testProvider, testKey)

        val savedKey = prefsManager.apiKeyFlowFor(testProvider).first()
        assertEquals(testKey, savedKey)
    }

    @Test
    fun testAnyKeyFallback() = runBlocking {
        prefsManager.setApiKeyFor("openai", "openai-key")
        val fallback = prefsManager.findAnyAvailableKey()

        assertEquals("openai", fallback?.first)
        assertEquals("openai-key", fallback?.second)
    }
}

