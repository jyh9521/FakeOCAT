plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.fakeocat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.fakeocat"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        lint {
            checkReleaseBuilds = true
            abortOnError = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // 导航与 ViewModel
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // DataStore（偏好设置）
    implementation(libs.androidx.datastore.preferences)
    
    // 协程
    implementation(libs.kotlinx.coroutines.android)
    
    // 网络（OkHttp + SSE）
    implementation(libs.okhttp3.okhttp)
    implementation(libs.okhttp3.sse)

    testImplementation(libs.junit)
    // 单元测试中使用真实的 org.json 实现（Android 的 json stubs 不支持 optString 等方法）
    testImplementation(libs.json)
    // 协程测试：StandardTestDispatcher、runTest 等
    testImplementation(libs.kotlinx.coroutines.test)
    // Turbine：StateFlow/Flow 测试断言库
    testImplementation(libs.turbine)
    // MockK：Kotlin 友好的 mock 框架
    testImplementation(libs.mockk)
    // MockWebServer：模拟 HTTP 服务器，测试 LlmClient 网络请求
    testImplementation(libs.okhttp3.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
