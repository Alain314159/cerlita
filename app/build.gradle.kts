import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// local.properties jamás se versiona (ver .gitignore): URL y token del modelo
// de IA viven ahí, no en el código ni en el APK por defecto.
val propiedadesLocales = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.cerlita.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cerlita.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        vectorDrawables { useSupportLibrary = true }

        // ONNX Runtime empaqueta librerías nativas por arquitectura (~35 MB
        // cada una). Se dejan solo las de teléfonos reales: x86/x86_64 solo
        // sirven para emuladores. Con App Bundle (Play) el reparto es
        // automático por dispositivo.
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a")
        }

        // ── Modelo de IA local (Fase 3) ─────────────────────────────────
        // El repositorio es público: la descarga NO necesita token. Solo
        // define cerlita.modelo.token en local.properties si algún día el
        // repo pasa a privado (evita incrustar secretos en el APK).
        buildConfigField(
            "String",
            "MODELO_URL_API_RELEASE",
            "\"${propiedadesLocales.getProperty(
                "cerlita.modelo.urlApi",
                "https://api.github.com/repos/Alain314159/cerlita/releases/tags/modelo-ia-v1",
            )}\"",
        )
        buildConfigField(
            "String",
            "MODELO_TOKEN_GITHUB",
            "\"${propiedadesLocales.getProperty("cerlita.modelo.token", "")}\"",
        )
        buildConfigField(
            "int",
            "MODELO_TAMANO_MAX_MB",
            propiedadesLocales.getProperty("cerlita.modelo.maxMb", "400"),
        )
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    implementation(project(":core:ai"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
