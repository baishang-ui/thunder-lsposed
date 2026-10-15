pluginManagement {
 repositories {
  google()
  maven("https://maven.aliyun.com/repository/google")
  mavenCentral()
  gradlePluginPortal()
 }
 resolutionStrategy.eachPlugin {
  if (requested.id.id == "com.android.application") useModule("com.android.tools.build:gradle:${requested.version}")
 }
}
dependencyResolutionManagement {
 repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
 repositories {
  mavenCentral()
  maven("https://maven.aliyun.com/repository/google")
 }
}
rootProject.name = "thunder"
include(":app")
