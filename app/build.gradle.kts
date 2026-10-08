import java.util.Properties
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val spotifyProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val spotifyClientId = providers.environmentVariable("SPOTIFY_CLIENT_ID").orNull
    ?: spotifyProperties.getProperty("spotify.clientId", "")
val spotifyRedirect = providers.environmentVariable("SPOTIFY_REDIRECT_URI").orNull
    ?: spotifyProperties.getProperty("spotify.redirectUri", "com.example.audiary://callback")
val redirectUri = runCatching { URI(spotifyRedirect) }.getOrNull()
require(redirectUri != null && redirectUri.scheme != null && redirectUri.host != null &&
    redirectUri.rawQuery == null && redirectUri.fragment == null && redirectUri.userInfo == null) {
    "spotify.redirectUri must be a valid URL without query, fragment, or credentials."
}
val parsedUri = redirectUri!!
val redirectScheme = parsedUri.scheme.orEmpty()
val redirectHost = parsedUri.host.orEmpty()
val rawPath = parsedUri.path?.ifBlank { "/" } ?: "/"
val isHttps = redirectScheme.equals("https", ignoreCase = true)
val httpsHost = if (isHttps) redirectHost else "audiary.example.com"
val httpsPath = if (isHttps) rawPath else "/callback"
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.example.audiary"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.audiary"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "2.0"
        buildConfigField("String", "SPOTIFY_CLIENT_ID", quoted(spotifyClientId))
        buildConfigField("String", "SPOTIFY_REDIRECT_URI", quoted(spotifyRedirect))
        manifestPlaceholders["spotifyRedirectScheme"] = redirectScheme
        manifestPlaceholders["spotifyRedirectHost"] = httpsHost
        manifestPlaceholders["spotifyRedirectPath"] = httpsPath

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room { schemaDirectory("$projectDir/schemas") }

dependencies {
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.okhttp)
    ksp(libs.room.compiler)
    implementation(libs.androidx.compose.icons)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.json.test)
    androidTestImplementation(libs.room.testing)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))
    implementation("com.google.code.gson:gson:2.10.1")
}
