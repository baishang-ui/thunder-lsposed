plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
}
android {
 namespace = "io.github.baishangui.thunder"
 compileSdkVersion("android-37.0")
 defaultConfig {
  applicationId = "io.github.baishangui.thunder"
  minSdk = 37
  targetSdk = 37
  versionCode = 16
  versionName = "3.0.0"
 }
 buildFeatures { compose = true; buildConfig = true }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
 }
 buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
 packaging { resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1") }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) } }
dependencies {
 implementation("androidx.activity:activity-compose:1.13.0")
 implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
 implementation("org.jetbrains.compose.ui:ui:1.11.1")
 implementation("org.jetbrains.compose.runtime:runtime-saveable:1.11.1")
 implementation("io.github.suqi8.coui.kmp:coui-ui-android:2.0.0")
 implementation("io.github.suqi8.coui.kmp:coui-preference-android:2.0.0")
 implementation("io.github.suqi8.coui.kmp:coui-icons-android:2.0.0")
 implementation("io.github.suqi8.coui.kmp:coui-blur-android:2.0.0")
 implementation("io.github.suqi8.coui.kmp:coui-nav-android:2.0.0") {
  exclude(group = "org.jetbrains.androidx.lifecycle")
 }
 runtimeOnly("androidx.lifecycle:lifecycle-runtime-compose:2.10.0") { because("Lifecycle 2.11 requires AGP 9.1") }
 runtimeOnly("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0") { because("Lifecycle 2.11 requires AGP 9.1") }
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.json:json:20251224")
 compileOnly("io.github.libxposed:api:102.0.0")
 testImplementation("io.github.libxposed:api:102.0.0")
 implementation("io.github.libxposed:service:102.0.0")
}
dependencyLocking { lockAllConfigurations() }
