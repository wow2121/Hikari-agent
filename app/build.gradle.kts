plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
    id("io.realm.kotlin")
    // Firebase plugin commented out for now - will add when implementing cloud sync
    // id("com.google.gms.google-services")
}

android {
    namespace = "com.xiaoguang.assistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.xiaoguang.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Load API keys from gradle.properties
        buildConfigField("String", "SILICON_FLOW_API_KEY", "\"${project.findProperty("SILICON_FLOW_API_KEY") ?: ""}\"")

        // Neo4j configuration
        buildConfigField("String", "NEO4J_USERNAME", "\"${project.findProperty("NEO4J_USERNAME") ?: ""}\"")
        buildConfigField("String", "NEO4J_PASSWORD", "\"${project.findProperty("NEO4J_PASSWORD") ?: ""}\"")
        buildConfigField("String", "NEO4J_DATABASE", "\"${project.findProperty("NEO4J_DATABASE") ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")  // For ProcessLifecycleOwner
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Dependency Injection - Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-android-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    // Networking - Retrofit & OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Voice Recognition - Porcupine (Wake word detection)
    implementation("ai.picovoice:porcupine-android:3.0.0")

    // Voice Recognition - Vosk (Offline continuous speech recognition)
    implementation("com.alphacephei:vosk-android:0.3.47")

    // Database - Realm
    implementation("io.realm.kotlin:library-base:1.13.0")
    implementation("io.realm.kotlin:library-sync:1.13.0")

    // Database - Room (for vector storage)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Firebase - Commented out for now, will add when implementing cloud sync
    // implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    // implementation("com.google.firebase:firebase-firestore-ktx")
    // implementation("com.google.firebase:firebase-auth-ktx")
    // implementation("com.google.firebase:firebase-storage-ktx")

    // WorkManager (Background tasks)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.hilt:hilt-work:1.1.0")
    ksp("androidx.hilt:hilt-compiler:1.1.0")

    // Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // TensorFlow Lite (Optional - for embeddings/custom ML models)
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // ✅ Phase 2: ONNX Runtime for Speaker Diarization
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.0")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Apache Commons Math (for FFT optimization)
    implementation("org.apache.commons:commons-math3:3.6.1")

    // Chinese Word Segmentation - jieba-analysis (Java version)
    implementation("com.huaban:jieba-analysis:1.0.2")

    // DataStore (Preferences)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Timber (Logging)
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
