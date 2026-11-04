plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 发布签名：CI 中通过环境变量注入；本地无签名时回退到 debug 签名，保证随时可出包
val releaseKeystore: String? =
    System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() && file(it).exists() }

// 允许 CI 用 tag 覆盖版本号：./gradlew -PversionName=1.2.3 -PversionCode=12
val ciVersionName: String = (findProperty("versionName") as String?) ?: "1.3.0"
val ciVersionCode: String = (findProperty("versionCode") as String?) ?: "1"

android {
    namespace = "com.fuoins.opponote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fuoins.opponote"
        minSdk = 29
        targetSdk = 35
        versionCode = ciVersionCode.toInt()
        versionName = ciVersionName
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: "opponote"
                keyPassword = System.getenv("KEY_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Xposed 模块依赖类名与反射，保持不混淆，稳定性优先
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig =
                if (releaseKeystore != null) signingConfigs.getByName("release")
                else signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
    }
}

dependencies {
    // 编译期依赖，运行时由 Xposed/LSPosed 框架提供，绝不能打包进 APK
    compileOnly(project(":xposed-api-stub"))
    compileOnly("androidx.annotation:annotation:1.9.1")
    // 仅编译期：运行时使用便签自带的 androidx.preference
    compileOnly("androidx.preference:preference:1.2.1")
}
