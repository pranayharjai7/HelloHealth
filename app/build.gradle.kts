import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"
}

android {
    namespace = "com.hellohealth"
    compileSdk = 35

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(localPropertiesFile.inputStream())
    }

    defaultConfig {
        applicationId = "com.hellohealth"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "SUPABASE_URL", "\"${localProperties.getProperty("SUPABASE_URL") ?: ""}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProperties.getProperty("SUPABASE_ANON_KEY") ?: ""}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProperties.getProperty("GOOGLE_WEB_CLIENT_ID") ?: ""}\"")
        buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"${localProperties.getProperty("GOOGLE_MAPS_API_KEY") ?: ""}\"")
        // USDA FoodData Central API key for remote food search (Nutrition). Falls back to the public
        // rate-limited DEMO_KEY when unset in local.properties, so a fresh clone still builds and the
        // remote search degrades gracefully rather than failing to compile.
        buildConfigField(
            "String",
            "USDA_FDC_API_KEY",
            "\"${localProperties.getProperty("USDA_FDC_API_KEY")?.takeIf { it.isNotBlank() } ?: "DEMO_KEY"}\""
        )
        // NOTE: the Gemini/OpenRouter keys are deliberately NOT compiled into the app. AI Coaching
        // calls the `ai-coach` Supabase Edge Function (server-side proxy) which holds those keys as
        // function secrets, so no LLM provider secret ships in the APK. See supabase/functions/ai-coach.
        manifestPlaceholders["GOOGLE_MAPS_API_KEY"] = localProperties.getProperty("GOOGLE_MAPS_API_KEY") ?: ""
    }

    // Release signing is driven entirely from local.properties (git-ignored) with the keystore kept
    // OUTSIDE the repo — no secrets in version control. On a machine that has neither, `hasReleaseKeystore`
    // is false and the release build stays unsigned (the prior behaviour) instead of failing configuration,
    // so debug builds and CI without the keystore are unaffected.
    val releaseStoreFile = localProperties.getProperty("RELEASE_STORE_FILE")?.takeIf { it.isNotBlank() }
    val hasReleaseKeystore = releaseStoreFile != null && file(releaseStoreFile).exists()

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = file(releaseStoreFile!!)
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
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
            // Sign only when the keystore is present; otherwise leave the release build unsigned.
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // Keep the ML model assets uncompressed. TFLite's FileUtil.loadMappedFile memory-maps the
    // .tflite graphs straight out of the APK, which REQUIRES them stored uncompressed; the PyTorch
    // .ptl is copied to filesDir before load but is left uncompressed too to avoid pointless
    // compress-then-inflate churn on a 15 MB blob.
    androidResources {
        noCompress += listOf("tflite", "ptl")
    }
    // Check in exported Room schemas so migrations have a baseline to validate against.
    // androidTest gets them for instrumented runs. Robolectric-based MigrationTestHelper reads the
    // *debug-merged* app assets (android_merged_assets), so the schemas also go on the debug source
    // set — debug-only, so the release APK never ships them.
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
        getByName("debug").assets.srcDir("$projectDir/schemas")
    }
}

// Export Room schema JSONs to app/schemas for migration testing + version control.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    // Drag-to-reorder for LazyColumn (Stage 9: exercise reordering). Compose 1.7-compatible.
    implementation("sh.calvin.reorderable:reorderable:2.4.3")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // JSON Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // WorkManager + Hilt integration (offline-first sync reconciler)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Coil for images
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Credential Manager for Google Sign In
    implementation("androidx.credentials:credentials:1.2.2")
    implementation("androidx.credentials:credentials-play-services-auth:1.2.2")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.0")

    // Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0-alpha12")

    // Google Maps
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("com.google.maps.android:maps-compose:4.3.3")

    // Supabase
    val supabaseVersion = "2.5.0"
    implementation("io.github.jan-tennert.supabase:supabase-kt:$supabaseVersion")
    implementation("io.github.jan-tennert.supabase:postgrest-kt:$supabaseVersion")
    implementation("io.github.jan-tennert.supabase:gotrue-kt:$supabaseVersion")
    // functions-kt: invoke the ai-coach Edge Function (LLM proxy) with the user's session JWT, so the
    // Gemini/OpenRouter keys stay server-side and never ship in the APK.
    implementation("io.github.jan-tennert.supabase:functions-kt:$supabaseVersion")
    implementation("io.ktor:ktor-client-android:2.3.11")

    // Remote food data (Nutrition): a dedicated Ktor client (separate from Supabase's) for the USDA
    // FoodData Central + Open Food Facts JSON APIs. content-negotiation + kotlinx-json parse the
    // responses; every call is runCatching-guarded so a 429/timeout/malformed body degrades to a
    // defined fallback (empty results / null), never a crash.
    val ktorVersion = "2.3.11"
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // Barcode scanning (Nutrition): ML Kit reads EAN-13/UPC off the CameraX preview → Open Food
    // Facts lookup. Bundled model so the first scan works offline of Play Services.
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // On-device emotion ML (P2): MTCNN face detection (TFLite) + emotion classifier (PyTorch Lite).
    // Coordinates ported verbatim from the MyEmotions source app.
    implementation("org.pytorch:pytorch_android_lite:2.1.0")
    implementation("org.pytorch:pytorch_android_torchvision_lite:2.1.0")
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.5.0")

    // CameraX (P2): front-camera still capture for the face-scan flow.
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    // Resolvable ListenableFuture + `await()` so we can await ProcessCameraProvider from a coroutine
    // (camera-lifecycle 1.3.x has no awaitInstance()). Guava provides the ListenableFuture type that
    // CameraX exposes in its API but only ships as an empty stub transitively.
    implementation("androidx.concurrent:concurrent-futures-ktx:1.1.0")
    implementation("com.google.guava:guava:33.2.1-android")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("io.ktor:ktor-client-mock:2.3.11")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
