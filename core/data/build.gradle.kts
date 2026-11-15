plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.meowmorize.core.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    // OCR：中文 + 拉丁文字识别（测试功能）。
    // 只引 GMS 版：模型首次使用时下载，APK 不含原生库，也不会在启动时初始化 ML Kit。
    implementation(libs.mlkit.text.recognition.chinese)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
