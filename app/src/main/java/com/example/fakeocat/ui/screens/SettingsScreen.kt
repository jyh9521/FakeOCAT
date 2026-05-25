package com.example.fakeocat.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fakeocat.R
import com.example.fakeocat.network.AiProviderCatalog
import com.example.fakeocat.network.ConnectionPrewarmer
import com.example.fakeocat.ui.viewmodel.ChatViewModel
import com.example.fakeocat.ui.viewmodel.PromptBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = viewModel.prefs
    val themeMode by viewModel.themeMode.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val nativeLang by viewModel.nativeLanguage.collectAsState()
    val targetLang by viewModel.targetLanguage.collectAsState()
    val appLang by viewModel.appLanguage.collectAsState()

    // 当前所选服务商的 API Key
    var apiKey by remember { mutableStateOf("") }
    var apiKeyLoaded by remember { mutableStateOf(false) }
    var lastLoadedProvider by remember { mutableStateOf("") }

    // 当服务商变化时重新加载 API Key（使用 LaunchedEffect 自动管理生命周期）
    LaunchedEffect(selectedProvider) {
        apiKey = prefs.apiKeyFlowFor(selectedProvider).first()
        apiKeyLoaded = true
        lastLoadedProvider = selectedProvider
    }

    val providers = AiProviderCatalog.providers.map { it.id to it.displayName }

    val languages = PromptBuilder.supportedLanguages.map {
        it to PromptBuilder.langDisplayName(it)
    }

    val appLanguages = listOf(
        "system" to stringResource(R.string.settings_language_system)
    ) + languages

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── 主题 ──
            SectionTitle(stringResource(R.string.settings_theme))
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "auto" to stringResource(R.string.settings_theme_auto),
                        "light" to stringResource(R.string.settings_theme_light),
                        "dark" to stringResource(R.string.settings_theme_dark)
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = themeMode == key,
                            onClick = { scope.launch { prefs.setThemeMode(key) } },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── AI 服务商 + API Key ──
            SectionTitle(stringResource(R.string.settings_ai_provider))
            SettingsCard {
                // 服务商下拉选择
                var providerExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = providerExpanded,
                    onExpandedChange = { providerExpanded = it }
                ) {
                    OutlinedTextField(
                        value = providers.find { it.first == selectedProvider }?.second ?: "OpenAI",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                        shape = RoundedCornerShape(12.dp),
                        label = { Text(stringResource(R.string.settings_ai_provider)) }
                    )
                    ExposedDropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false }
                    ) {
                        providers.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    scope.launch {
                                        prefs.setSelectedProvider(key)
                                        apiKey = prefs.apiKeyFlowFor(key).first()
                                        lastLoadedProvider = key
                                    }
                                    providerExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 接口密钥输入框
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.settings_api_key)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_api_key_hint),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── 语言设置 ──
            SectionTitle(stringResource(R.string.settings_app_language))
            SettingsCard {
                var appLangExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = appLangExpanded,
                    onExpandedChange = { appLangExpanded = it }
                ) {
                    OutlinedTextField(
                        value = appLanguages.find { it.first == appLang }?.second ?: stringResource(R.string.settings_language_system),
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = appLangExpanded) },
                        shape = RoundedCornerShape(12.dp),
                        label = { Text(stringResource(R.string.settings_app_language)) }
                    )
                    ExposedDropdownMenu(
                        expanded = appLangExpanded,
                        onDismissRequest = { appLangExpanded = false }
                    ) {
                        appLanguages.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    scope.launch { prefs.setAppLanguage(key) }
                                    appLangExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── 母语 ──
            SectionTitle(stringResource(R.string.settings_native_language))
            SettingsCard {
                var nativeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = nativeExpanded,
                    onExpandedChange = { nativeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = languages.find { it.first == nativeLang }?.second ?: "",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = nativeExpanded) },
                        shape = RoundedCornerShape(12.dp),
                        label = { Text(stringResource(R.string.settings_native_language)) }
                    )
                    ExposedDropdownMenu(
                        expanded = nativeExpanded,
                        onDismissRequest = { nativeExpanded = false }
                    ) {
                        languages.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    scope.launch { prefs.setNativeLanguage(key) }
                                    nativeExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── 目标语言 ──
            SectionTitle(stringResource(R.string.settings_target_language))
            SettingsCard {
                var targetExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = targetExpanded,
                    onExpandedChange = { targetExpanded = it }
                ) {
                    OutlinedTextField(
                        value = languages.find { it.first == targetLang }?.second ?: "",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetExpanded) },
                        shape = RoundedCornerShape(12.dp),
                        label = { Text(stringResource(R.string.settings_target_language)) }
                    )
                    ExposedDropdownMenu(
                        expanded = targetExpanded,
                        onDismissRequest = { targetExpanded = false }
                    ) {
                        languages.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    scope.launch { prefs.setTargetLanguage(key) }
                                    targetExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 保存按钮
            Button(
                onClick = {
                    scope.launch {
                        prefs.setApiKeyFor(selectedProvider, apiKey)
                        // 保存后异步预热连接，降低首次请求 TTFT
                        ConnectionPrewarmer.resetForProvider(selectedProvider)
                        ConnectionPrewarmer.warmUp(selectedProvider, apiKey)
                        Toast.makeText(context, context.getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.settings_save), fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}
