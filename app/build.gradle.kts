plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
}
android {
 namespace = "io.github.baishangui.thunder"
 compileSdk = 37
 defaultConfig {
  applicationId = "io.github.baishangui.thunder"
  minSdk = 37
  targetSdk = 37
  versionCode = 8
  versionName = "4.3"
 }
 buildFeatures { compose = true; buildConfig = true }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
 buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
 packaging { resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1") }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
 implementation("androidx.activity:activity-compose:1.12.4")
 implementation("androidx.compose.foundation:foundation:1.10.3")
 implementation("androidx.compose.ui:ui:1.10.3")
 implementation("androidx.compose.runtime:runtime-saveable:1.10.3")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.json:json:20251224")
 implementation("io.github.kyant0:backdrop:1.0.6")
 implementation("io.github.kyant0:shapes:1.2.0")
 implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.0")
 compileOnly("io.github.libxposed:api:102.0.0")
 testImplementation("io.github.libxposed:api:102.0.0")
 implementation("io.github.libxposed:service:102.0.0")
}
dependencyLocking { lockAllConfigurations() }
