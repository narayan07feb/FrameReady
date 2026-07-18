pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "My Application"

include(":app")
include(":frameready")
include(":sample-standard")
include(":sample-baseline")
include(":sample-metrics-only")
include(":sample-trampoline")
include(":sample-notification")
include(":sample-hilt")
include(":sample-appstartup")
include(":sample-appcls-init")
include(":shared-ui")
include(":sample-ios")
include(":benchmark")


