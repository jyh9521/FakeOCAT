package com.example.fakeocat

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.fakeocat.ui.AppNavigation
import com.example.fakeocat.ui.theme.FakeOCATTheme
import com.example.fakeocat.ui.viewmodel.ChatViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val chatViewModel: ChatViewModel = viewModel()
            val themeMode by chatViewModel.themeMode.collectAsState()
            val appLang by chatViewModel.appLanguage.collectAsState()

            // 当 appLang 变化时应用对应语言环境
            val context = LocalContext.current
            var currentLocale by remember { mutableStateOf(appLang) }

            LaunchedEffect(appLang) {
                if (appLang != currentLocale) {
                    currentLocale = appLang
                    // 重新创建 Activity 以应用新的语言环境
                    (context as? MainActivity)?.recreate()
                }
            }

            val isDark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            FakeOCATTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavigation(
                        navController = navController,
                        chatViewModel = chatViewModel
                    )
                }
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        // 读取已保存的语言偏好并应用语言环境
        val prefs = newBase.getSharedPreferences("settings_locale", Context.MODE_PRIVATE)
        val langCode = prefs.getString("app_language", "system") ?: "system"
        val context = if (langCode != "system") {
            val locale = Locale.forLanguageTag(langCode)
            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            newBase.createConfigurationContext(config)
        } else {
            newBase
        }
        super.attachBaseContext(context)
    }
}