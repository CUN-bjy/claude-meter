pluginManagement {
    repositories {
        google()
        // Google's mirror of Maven Central, tried first: repo.maven.apache.org
        // rate-limits CI runners (HTTP 429), and PR builds can't use the Gradle
        // cache, so they download everything on every run.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}

rootProject.name = "ClaudeMeter"
include(":app")
