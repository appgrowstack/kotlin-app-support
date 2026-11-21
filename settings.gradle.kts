pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kotlin-app-support"

// Kotlin/JVM だけ。Android の SDK を要らない（メールを開く・端末の名前を読むのはアプリが渡す口。画面はアプリの Compose）
include(":support-core")
