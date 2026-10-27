plugins {
    alias(libs.plugins.whlambda.android.application)
}

android {
    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(projects.shared)
    implementation(projects.core.data)
    implementation(projects.core.audio)
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
}
