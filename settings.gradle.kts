pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "intl-fixed-calendar"

include(":app")
include(":core:calendar")
include(":core:data")
include(":core:designsystem")
include(":core:domain")
include(":core:holidays")
include(":core:navigation")
include(":core:scheduling")
include(":core:testing")
include(":feature:calendar")
include(":feature:converter")
include(":feature:settings")
