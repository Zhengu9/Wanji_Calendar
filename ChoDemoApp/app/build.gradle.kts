import java.util.Properties

plugins {
    alias(libs.plugins.android.application)

    // Room（Java + annotationProcessor，避免内置 Kotlin 下 KSP/KAPT 兼容问题）
}

android {
    namespace = "com.example.chronosyncapp"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.chronosyncapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

		fun envValue(key: String): String? {
			val candidates = listOf(rootProject.file(".env"), rootProject.file("后端.env"))
			for (f in candidates) {
				if (!f.exists()) continue
				val line = f.readLines()
					.firstOrNull { it.trim().startsWith("$key=") }
					?: continue
				return line.substringAfter("=").trim().trim('"')
			}
			return System.getenv(key)
		}
		fun esc(v: String): String = v
			.replace("\\\\", "\\\\\\\\")
			.replace("\"", "\\\\\"")

		val kimiKey = envValue("kimiApiKey") ?: envValue("kimiapi") ?: envValue("KIMI_API_KEY") ?: ""
		buildConfigField("String", "KIMI_API_KEY", "\"${esc(kimiKey)}\"")
		buildConfigField("String", "KIMI_BASE_URL", "\"https://api.moonshot.cn\"")
		buildConfigField("String", "KIMI_MODEL", "\"kimi-k2-turbo-preview\"")
		buildConfigField("String", "KIMI_VISION_MODEL", "\"moonshot-v1-8k-vision-preview\"")

		val chronoSyncBaseUrl = envValue("CHRONOSYNC_BASE_URL")
			?: envValue("CHRONO_SYNC_BASE_URL")
			?: envValue("chronosyncBaseUrl")
			?: "http://115.190.155.26:8000"
		buildConfigField("String", "CHRONOSYNC_BASE_URL", "\"${esc(chronoSyncBaseUrl)}\"")
    }

    // Load signing properties (key.properties) if present
    val keyPropertiesFile = rootProject.file("key.properties")
    val keyProperties = Properties()
    if (keyPropertiesFile.exists()) {
        keyPropertiesFile.inputStream().use { stream -> keyProperties.load(stream) }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = keyProperties.getProperty("storeFile", "app/my-release-key.jks")
            storeFile = rootProject.file(storeFilePath)
            storePassword = keyProperties.getProperty("storePassword", "changeit123")
            keyAlias = keyProperties.getProperty("keyAlias", "mykey")
            keyPassword = keyProperties.getProperty("keyPassword", "changeit123")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // Calendar 库基于 java.time，需要启用 desugaring 以支持低于 26 的设备
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
	buildFeatures {
		buildConfig = true
	}
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Java 8+ API desugaring（支持 java.time 等 API 在低版本 Android 上运行）
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // 引入 Calendar 库的 View 版本，用于日历视图
    implementation("com.kizitonwose.calendar:view:2.10.0")

    // Lifecycle（协程 + repeatOnLifecycle）
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

	// 网络请求（Kimi API）
	implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    //Lynx系列
    implementation("org.lynxsdk.lynx:lynx:3.4.1")
    implementation("org.lynxsdk.lynx:lynx-jssdk:3.4.1")
    implementation("org.lynxsdk.lynx:lynx-trace:3.4.1")
    implementation("org.lynxsdk.lynx:primjs:2.14.1")

    // 图像服务 (lynx-service-image)
    implementation("org.lynxsdk.lynx:lynx-service-image:3.4.1")

    // 图像服务依赖 (Fresco)，若宿主已有，可移除
    implementation("com.facebook.fresco:fresco:2.3.0")
    implementation("com.facebook.fresco:animated-gif:2.3.0")
    implementation("com.facebook.fresco:animated-webp:2.3.0")
    implementation("com.facebook.fresco:webpsupport:2.3.0")
    implementation("com.facebook.fresco:animated-base:2.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.0")

    // 日志服务 (lynx-service-log)
    implementation("org.lynxsdk.lynx:lynx-service-log:3.4.1")

    // 网络服务 (lynx-service-http)
    implementation("org.lynxsdk.lynx:lynx-service-http:3.4.1")
}


