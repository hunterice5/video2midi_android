plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.video2midi"
    compileSdk = project.findProperty("GLOBAL_SDK_VERSION")?.toString()?.toInt() ?: 35

    defaultConfig {
        applicationId = "com.video2midi"
        minSdk = project.findProperty("GLOBAL_MIN_SDK_VERSION")?.toString()?.toInt() ?: 29
        targetSdk = project.findProperty("GLOBAL_TARGET_SDK_VERSION")?.toString()?.toInt() ?: 34
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
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("17"))
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")

    implementation("com.google.code.gson:gson:2.10.1")

    implementation("com.github.wseemann:FFmpegMediaMetadataRetriever-core:1.0.19")
    implementation("com.github.wseemann:FFmpegMediaMetadataRetriever-native:1.0.19")
}
