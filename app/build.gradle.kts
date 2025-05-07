plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.todays_drink"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.todays_drink"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

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
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

}

dependencies {
    implementation(libs.core) // Amplify Core SDK
    implementation(libs.aws.api) // AWS API
    implementation(libs.aws.auth.cognito) // AWS Cognito (선택)
    implementation(libs.aws.datastore)
    implementation(libs.constraintlayout) // AWS DataStore (선택)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    // ① OkHttp BOM으로 4.12.0(or 4.11.x) 계열 고정
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))

    // ② core + logging-interceptor 같은 버전으로
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")
    implementation ("com.squareup.retrofit2:retrofit:2.9.0")
    implementation ("com.squareup.retrofit2:converter-moshi:2.9.0")

    implementation ("com.squareup.retrofit2:converter-gson:2.7.1")
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.compose.material)
    implementation(libs.compose.foundation)
    implementation(libs.wear.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.core.splashscreen)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}
