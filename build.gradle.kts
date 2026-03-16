import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    
    // Add the Google services Gradle plugin
    id("com.google.gms.google-services")
}

android {
    namespace = "com.escape.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.escape.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Load keystore properties from local.properties
    val keystorePropertiesFile = rootProject.file("local.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    // Signing configuration for release builds
    signingConfigs {
        create("release") {
            val keystorePath = keystoreProperties["KEYSTORE_PATH"] as String? ?: "../escape-release-key.jks"
            val keystorePassword = keystoreProperties["KEYSTORE_PASSWORD"] as String? ?: ""
            val keyAlias = keystoreProperties["KEY_ALIAS"] as String? ?: "escape-key"
            val keyPassword = keystoreProperties["KEY_PASSWORD"] as String? ?: keystorePassword

            if (file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Apply signing config if keystore exists
            if (file(keystoreProperties["KEYSTORE_PATH"] as String? ?: "../escape-release-key.jks").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        disable += "ProtectedPermissions"
        disable += "NewApi"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Import the Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))

    // Firebase Analytics
    implementation("com.google.firebase:firebase-analytics")
    
    // Firebase Firestore (for cloud data storage - useful for syncing stats across devices)
    implementation("com.google.firebase:firebase-firestore")
    
    // Firebase Authentication
    implementation("com.google.firebase:firebase-auth")
    
    // Firebase Realtime Database (for real-time buddy sync)
    implementation("com.google.firebase:firebase-database")
    
    // Firebase Storage (for app icon sharing)
    implementation("com.google.firebase:firebase-storage")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}