plugins {
    id("com.android.library")
}

android {
    namespace = "de.robv.android.xposed.stub"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// 该模块仅用于编译期，运行时由 Xposed/LSPosed 框架提供同名类，
// 因此 app 必须以 compileOnly 方式依赖，绝不可打包进 APK。
