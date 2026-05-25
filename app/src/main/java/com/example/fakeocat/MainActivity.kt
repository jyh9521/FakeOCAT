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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.fakeocat.ui.AppNavigation
import com.example.fakeocat.ui.theme.FakeOCATTheme
import com.example.fakeocat.ui.viewmodel.ChatViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var appliedLangCode: String = "system"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 提前通知系统冷启动完成，避免三星 One UI 等设备因 Compose 首次
        // 组合（含 Vulkan 着色器 JIT 编译）耗时触发冷启动 ANR 对话框
        reportFullyDrawn()

        val currentLang = appliedLangCode
        setContent {
            val chatViewModel: ChatViewModel = viewModel(
                factory = ChatViewModel.Factory(application)
            )
            val appLang by chatViewModel.appLanguage.collectAsState()
            val themeMode by chatViewModel.themeMode.collectAsState()

            // 用户切换界面语言后立即 recreate Activity，
            // 使 attachBaseContext 重新应用新语言
            LaunchedEffect(appLang) {
                if (appLang != currentLang) {
                    recreate()
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
        val prefs = newBase.getSharedPreferences("settings_locale", Context.MODE_PRIVATE)
        val langCode = prefs.getString("app_language", "system") ?: "system"
        appliedLangCode = langCode
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
