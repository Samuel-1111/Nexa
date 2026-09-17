pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NEXA"

include(":app")
include(":core:common")
include(":core:model")
include(":core:database")
include(":core:datastore")
include(":core:designsystem")
include(":core:notifications")
include(":core:voice")
include(":core:network")
include(":core:testing")
include(":data")
include(":domain")
include(":feature:onboarding")
include(":feature:today")
include(":feature:assistant")
include(":feature:organizer")
include(":feature:settings")
