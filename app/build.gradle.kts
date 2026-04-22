import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.hilt)
}

// Gradle escribe mucho en app/build; OneDrive suele bloquear archivos y falla con
// AccessDeniedException. La salida del módulo va a AppData\Local (no sincronizado).
val offCloudAppBuild =
    File(
        System.getenv("LOCALAPPDATA")
            ?: File(System.getProperty("user.home")!!, "AppData${File.separator}Local").absolutePath,
        "AndroidBuild${File.separator}${rootProject.projectDir.name}${File.separator}app",
    )
layout.buildDirectory.set(file(offCloudAppBuild.absolutePath))

android {
    namespace = "ar.edu.uadexplorenow"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "ar.edu.uadexplorenow"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.firebase.firestore)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation (platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation ("com.google.firebase:firebase-firestore")
    implementation ("com.google.firebase:firebase-auth")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation(libs.retrofit)
    implementation(libs.retrofitConverterGson)
    implementation(libs.okhttp)
    implementation(libs.hilt.android)
    annotationProcessor(libs.hilt.compiler)
    implementation("androidx.hilt:hilt-navigation-fragment:1.2.0")
}