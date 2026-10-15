buildscript {
 repositories { maven("https://maven.aliyun.com/repository/google"); mavenCentral() }
 dependencies { classpath("com.android.tools:r8:9.4.28") }
}
plugins {
 id("com.android.application") version "8.13.2" apply false
 id("org.jetbrains.kotlin.android") version "2.3.20" apply false
 id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
}
