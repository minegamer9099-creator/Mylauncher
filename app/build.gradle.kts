plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.mylauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.mylauncher"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        // Sirf English + Hindi resources: library ki 80+ bhashaon ki strings hat jati hain (chhota APK, tez resource lookup)
        resourceConfigurations += listOf("en", "hi")
    }

    buildTypes {
        release {
            // Debug APK debuggable hota hai (R8 nahi, JIT-only): launcher mein lag / freeze zyada. Isliye release APK
            // bhi banta hai: R8 se chhota + tez code. Debug key se sign hota hai, isliye seedha install ho jata hai.
            isMinifyEnabled = true
            isShrinkResources = true // koi getIdentifier / reflection use nahi hota, isliye safe
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Runtime null-check assertions hata do: har function call pe extra check nahi (tez hot paths)
        freeCompilerArgs += listOf("-Xno-param-assertions", "-Xno-call-assertions", "-Xno-receiver-assertions")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    // Spring (physics) animations: bouncy press / drawer / folder / entrance
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    // Baseline profile install karta hai (ART pehle se compile): pehle swipe / drawer mein jank kam
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
}
