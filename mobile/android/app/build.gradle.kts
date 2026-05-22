plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

android {
  namespace = "com.ytlistplayer.mobile"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.ytlistplayer.mobile"
    minSdk = 26
    targetSdk = 34
    versionCode = 1
    versionName = "0.1.0"
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
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }

  sourceSets {
    getByName("main") {
      java.srcDirs("src/main/java", "src/main/kotlin")
    }
  }
}

dependencies {
  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("com.google.android.material:material:1.12.0")
  implementation("androidx.media:media:1.7.0")
  implementation("androidx.webkit:webkit:1.11.0")
  implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.0")
}
