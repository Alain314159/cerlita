// Archivo de build raíz de Cerlita
//
// AGP 9 trae Kotlin integrado (built-in Kotlin): el plugin
// org.jetbrains.kotlin.android ya NO se aplica en módulos Android.
// Para alinear todo el proyecto a Kotlin 2.4.0 (y KSP 2.3.12) se usa
// el mecanismo documentado de buildscript classpath:
// https://developer.android.com/build/migrate-to-built-in-kotlin
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.12")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
